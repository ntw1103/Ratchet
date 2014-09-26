/*
 * Copyright (c) 2014, Mattersight Coperation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.mattersight.ratchet;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class allows for interaction with an SQLite database.
 * it is a wrapper for org.sqlite.JDBC.
 * @author Nathan Wiering
 */
public class Database {
    private  Connection connection = null;
    private String database =null;
    Database(String db) {
        this.database = db;
    }
    
    /**
     * Connects to the SQLite database.
     * @return Details about the attempted connection.
     */
    public String connect(){
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
            return ex.toString();
        }
       String answer="";
        try{// create a database connection
            connection = DriverManager.getConnection("jdbc:sqlite:"+this.database);
            initialize();
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);  // set timeout to 30 sec.
           // statement.executeUpdate("create table if not exists results (datetime string PRIMARY KEY,job string,build int,result real, failed int)");
           answer = "Connected to database."; 
        }catch(SQLException e){
            // if the error message is "out of memory", 
            // it probably means no database file is found
            System.err.println(e.getMessage());
             answer = e.toString();
        }finally{            
        }return answer;
    }

    /**
     * Disconnects from the SQLite database.
     */
    public void disconnect(){
        try {
            connection.close();
        } catch (SQLException ex) {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Returns the current database connection.
     * @return Connection to the database
     * @see Connection
     */
    public Connection getConnection(){
        return this.connection;
    }
    
    /**
     * Executes an SQL statement.
     * @param sql
     */
    public void insert(String sql){
       try { 
            Statement stmt = connection.createStatement();
            stmt.setQueryTimeout(30);
            stmt.executeUpdate(sql);
            stmt.close();
        } catch ( SQLException e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
        } 
    }

    /**
     * Returns the results from an given query
     * @param query Gets executed against the database.
     * @return The results from running a query.
     * @throws SQLException
     * @see ResultSet
     */
    public ResultSet select(String query) throws SQLException{
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(30);  // set timeout to 30 sec.
        ResultSet rs = statement.executeQuery(query);
        return rs;
    }

    /**
     * Returns a list of results from a given query
     * @param sql query to be executed against the database
     * @return list containing results from query.
     */
    public List selectList(String sql){
        List<List> rows = new ArrayList();
        try {
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);  // set timeout to 30 sec.
            ResultSet rs = statement.executeQuery(sql);
            ResultSetMetaData rsMetaData = rs.getMetaData();
            int numberOfColumns = rsMetaData.getColumnCount();

            while(rs.next()){ 
                List<Object> columns = new ArrayList();
                for (int x =1; x<=numberOfColumns;x++){
                    Object column = rs.getObject(x);
                    columns.add(column);
                }
                rows.add(columns);                
            }
        } catch (SQLException ex) {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }
        return rows;
    }

    /**
     * This Sets up the database in the event that it doesn't exist.
     * This will also preform updates to the database as the schema changes.
     */
    public void initialize(){ 
       try {
            String table_exists = "SELECT COUNT(name) as count FROM sqlite_master WHERE type='table' AND name='results';";
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);  // set timeout to 30 sec.
            ResultSet rs = statement.executeQuery(table_exists);
            if(rs.getInt("count")==0){//If the `results` table does not already exist.
                System.out.println("Table not created yet.");
                String sql = "create table if not exists results (datetime string PRIMARY KEY,job string,result real, failed int)";
                Statement stmt = connection.createStatement();
                stmt.executeUpdate(sql);
                stmt.close();
            }else{ // If it does exist, we are going to need to make sure it is updated.
                System.out.println("Table was already created");
                String table_schema = "pragma table_info(results);";
                statement = connection.createStatement();
                statement.setQueryTimeout(30);  // set timeout to 30 sec.
                rs = statement.executeQuery(table_schema);
                boolean found = false;
                while(rs.next()){
                    if(rs.getString("name").contains("result")){ //found the column.
                        found = true;
                    }
                }
                if(!found){
                    System.out.println("build column needs to be added");
                    String add_column = "ALTER TABLE results ADD COLUMN build int;";
                    statement = connection.createStatement();
                    statement.setQueryTimeout(30);  // set timeout to 30 sec.
                    statement.executeUpdate(add_column);
                }
            }
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
        } 
    }
}
