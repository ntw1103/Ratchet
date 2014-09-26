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

import hudson.Launcher;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 * This class is supposed to generate historical data about a given job.
 * @author Nathan Wiering
 */
public class Statistics {
    private Database database = null;

    Statistics(Database database) {
        this.database = database;
    }

    /**
     * Returns a string containing an HTML report on a job.
     * @param launcher Current Jenkins Launcher
     * @param job Name of the current build.
     * @return HTML report
     */
    public String GenerateHtml(Launcher launcher, String job){
        //listener.getLogger().println("In the generate HTML, output: " + outputName);
        //listener.getLogger().println("Trying to load stats from the DB");
        Database d = this.database;
        String thing =  d.connect();
        ResultSet rs;
        int position = 0;
        double last = 0;
        String content ="";
        String jobList = "";
        try {
            rs = d.select("SELECT * FROM results  WHERE `job`=\""+job+"\" ORDER BY `datetime` ASC");
            while(rs.next()){
                // read the result set
                int build = rs.getInt("build");
                int failed = rs.getInt("failed");
                double current = rs.getDouble("result");
                String color = "#A2C4E0"; //blue
                if(failed == 1){
                    color = "#EBA091";//red
                }else{// If it didn't fail, and the previous was larger, they must have used an override.
                    if(last > current){
                        color ="#9328EB";
                    }
                }
                content += "<a href=\"/job/test/"+Integer.toString(build)+"/console\" style=\" left:"+position+"px;height:"+Double.toString(current)+"px;background-color:"+color+"\"><div class=\"tooltip\">Build# "+Integer.toString(build)+": "+Double.toString(current)+" vs. "+Double.toString(last)+"</div></a>";
                jobList += "<div class=\"job\" id=\""+Integer.toString(build)+"\"><a href=\"/job/test/"+Integer.toString(build)+"/console\">"+Double.toString(current)+" vs. "+Double.toString(last)+"</a></div>";
                last = current;
                position = position+8;
            }
        } catch (SQLException ex) {
            Logger.getLogger(Statistics.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        String output = "";
        output += start(job);
        output += "100%\n<div class=\"wrapper\" id=\"wrapper\">\n";
        output += "<div style=\" left:0px;height:1px;width:"+(position+10)+"px;bottom:100px;background-color:black;\"></div>";
        output += content;
        output += "</div>0%";
        output += "<div id=results></div>";
        output += end();
       // listener.getLogger().println("Stats loaded, trying to write the file.");
        return output;
    }

    /**
     * Returns a String containing HTML Header elements. 
     * @param title The title of the HTML page.
     * @return The start of an HTML document.
     */
    public String start(String title){
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head><title>"+title+"</title></head>\n" + styles()+
                "<script type=\"text/javascript\">\n" +
                    "window.onload = function(){\n" +
"	document.getElementById('wrapper').scrollLeft =document.getElementById('wrapper').scrollLeftMax;\n" +
"	var links = document.getElementsByTagName('a');\n" +
"	for (var i = 0; i < links.length; i++) { \n" +
"	links.item(i).addEventListener('click', function(e){\n" +
"		e.preventDefault();\n" +
"		request = new XMLHttpRequest();\n" +
"		request.open('GET', this, true);\n" +
"		request.onload = function() {\n" +
"		  if (request.status >= 200 && request.status < 400){\n" +
"			// Success!\n" +
"			data = request.responseText;\n" +
"			//alert(data);\n" +
"			document.getElementById('results').innerHTML = data;\n" +
"		  } else {\n" +
"			// We reached our target server, but it returned an error\n" +
"\n" +
"		  }\n" +
"		};\n" +
"\n" +
"		request.onerror = function() {\n" +
"		alert(\"error\");\n" +
"		  // There was a connection error of some sort\n" +
"		};\n" +
"\n" +
"		request.send();\n" +
"		\n" +
"	});\n" +
"	\n" +
"	}\n" +
"}" +
                    
                "</script>\n"+
                "<body>";
    }

    /**
     * Returns the end of the HTML document.
     * @return HTML closing tags.
     */
    public String end(){
        return "</body>\n" +
               "</html>";
    }

    /**
     * Returns a string containing CSS styles in an HTML tag.
     * @return CSS styles.
     */
    public String styles(){
        String style = "<style>"
          + ".wrapper{\n" +
            "    border: 1px solid #aeaeae; \n" +
            "    background-color: #eaeaea; \n" +
            "    overflow:auto;    width: 100%; \n" +
            "    height: 118px;\n" +
            "    position : relative;\n" +
            "}\n" +
            ".wrapper > div{\n" +
            "  bottom: 0px;\n" +
            "  width: 6px;\n" +
            "  position : absolute;\n" +
            "  background-color: #aeaeae; \n" +
            "  margin: 1px;\n" +
            "  display : inline-block; \n" +
            "}\n" +
            ".wrapper > a{\n" +
            "  bottom: 0px;\n" +
            "  width: 6px;\n" +
            "  position : absolute;\n" +
            "  background-color: #aeaeae; \n" +
            "  margin: 1px;\n" +
            "  display : inline-block; \n" +
            "}\n" +
            ".wrapper > span{\n" +
            "  bottom: 0px;\n" +
            "  width: 6px;\n" +
            "  position : absolute;\n" +
            "  background-color: #aeaeae; \n" +
            "  margin: 1px;\n" +
            "  display : inline-block; \n" +
            "}\n" +
            ".info .tooltipcontainer {\n" +
            "    position: relative;\n" +
            "}\n" +
            "a .tooltip {\n" +
            "    display: none;\n" +
            "    position: absolute;\n" +
            "    /* More positioning, heigh, width, etc */\n" +
            "}\n" +
            "a:hover .tooltip {\n" +
            "	z-index: 200;\n" +
            "   top:150px;\n" +
            "	left:50px;\n" +
            "	position:fixed;"+
            "   display: block;\n" +
            "	background-color: white;\n" +
            "}\n"+
            "</style>\n";
        return style;
    }
}
