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
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import java.io.BufferedReader;
import java.io.FileInputStream;
import hudson.remoting.Callable;
import java.io.File;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Logger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class contains the Ratcheting plugin. This plugin will execute code on a
 * Jenkins slave and parses it for a provided regex, then compares that to
 * previous Jenkins job executions. Threshold results will be stored into an
 * SQLte database and retrieved for comparison. 
 * There will also be a report generated to display historical results.
 * 
 * @author Nathan Wiering
 */
public class Ratchet extends Builder implements Serializable{
    private final String regex;
    private final String file;
    private final String override;
    private final String report;
    private final static Logger LOG = Logger.getLogger(Ratchet.class.getName());
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public Ratchet(String regex,String file,String override,String report) {
        this.regex = regex;
        this.file = file;
        this.override =  override;
        this.report =  report;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>. 
     * @return 
     */
    /*public String getName() {
        return name;
    }*/
    public String getRegex() {
        return regex;
    }
    public String getFile(){
        return file;
    }
    public String getOverride(){
        return override;
    } 
    public String getReport(){
        return report;
    } 
    /**
     * Returns a String that matches the input Regex if found. OR returns ERROR from remote executer. 
     * This is run on the slave node, and is what parses the output file looking for the number to ratchet on.
     * @param fileName  This is the file you want it to open.
     * @param regex   This is the regex it will use to locate the ratchet number within the file.
     * @return      Result from Regex search in file, that will be used to ratchet against.
     */
    public String get_remote_result(String fileName,String regex){ // This is executed on the slave node, and therefor must be serializable.
        String rs = "";
        InputStream ins = null; // raw byte-stream
        Reader r = null; // cooked reader
        BufferedReader br = null; // buffered for readLine()
        try {
            String s;
            rs +=" In the try, opening file, ";
            ins = new FileInputStream(fileName);
            r = new InputStreamReader(ins, "UTF-8"); // leave charset out for default
            br = new BufferedReader(r);
            while ((s = br.readLine()) != null) {
                rs += " Trying to read a line from the file: "+s+"  \nAnd match against: "+regex+ "\n";
                Pattern p = Pattern.compile(regex);
                Matcher m = p.matcher(s);
                if (m.find()){
                  rs += " I found a pattern.";
                  return s;
                }else{
                  rs += "I failed to find the pattern\n";
                }
            }
        }catch (Exception e){
           return "Current directory: " + System.getProperty("user.dir") + "Error: "+ e.toString();
        }
        finally {
            if (br != null) { try { br.close(); } catch(Throwable t) { /* ensure close happens */ } }
            if (r != null) { try { r.close(); } catch(Throwable t) { /* ensure close happens */ } }
            if (ins != null) { try { ins.close(); } catch(Throwable t) { /* ensure close happens */ } }
        }
        return rs;
    }
    /**
     * This serves as the dispatcher to launch get_remote_result().
     * @param build Current Jenkins build.
     * @param launcher Current launcher
     * @param listener Current Build listener, used for output.
     * @return result String containing the parsed Number that will be used to ratchet against.
     */
    public String get_result(AbstractBuild build,Launcher launcher,BuildListener listener){
        String result = "0.0";
        String worksp = "";
        try {
            worksp = build.getWorkspace().toURI().toString();
        } catch (IOException ex) {
           worksp = ex.toString();
           return "Failed to get result: "+ worksp;
        } catch (InterruptedException ex) {
           worksp = ex.toString();
           return "Failed to get result: "+ worksp;
        }
        final String file_path = worksp.substring(6)+file; // Create a path that will be serializable.
        final String regex_topass = regex;
            Callable<String, IOException> task = new Callable<String, IOException>() { // Create a serialized method that will run on the slave.
                @Override
                public String call() throws IOException {
                    String result = get_remote_result(file_path,regex_topass);
                    return result; //InetAddress.getLocalHost().getHostName();
                }
            };
        try {// Get a "channel" to the build machine and run the task there
            result = launcher.getChannel().call(task);
            //listener.getLogger().println("Result: "+ file_path+"| |" + result );
        }catch (IOException ex) {
            listener.getLogger().println("failed: " + ex);
        } catch (InterruptedException ex) {
            listener.getLogger().println("failed: " + ex);
        }
        return result;
    }
    /**
     * This will load the threshold from the database for a given job.
     * @param job This is the name of the current job being executed.
     * @return  The previous Threshold for this job.
     * @throws java.lang.Exception
     */
    public double getThreshold(String job) throws Exception{
        Double answer = 0.0;
        Database d = new Database(getDescriptor().getDatabase());
        String thing =  d.connect();
        List results = d.selectList("SELECT `result` FROM results  WHERE `job`=\""+job+"\" AND `failed` = 0 ORDER BY `datetime` DESC  LIMIT 1");
        for(Object result : results){
           answer = Double.parseDouble(result.toString().replace("[", "").replace("]", ""));
        }
        d.disconnect();        
        return answer;
    }
    /**
     * Returns a Double "threshold" loaded from the database for a given job.
     * @param job This is the name of the current job being executed.
     * @param listener Current BuildListener used for output.
     * @return  The previous Threshold for this job.
     * @throws java.lang.Exception
     */
    public double getThreshold(String job, BuildListener listener) throws Exception{
        Double answer = 0.0;
        listener.getLogger().println("Trying to Load from the DB: " + job);
        Database d = new Database(getDescriptor().getDatabase());
        String thing =  d.connect();
        listener.getLogger().println("Database connection status: " +thing);
         listener.getLogger().println("Doing the select: "+ "SELECT `result` FROM results  WHERE `job`=\""+job+"\" AND `failed` = 0 ORDER BY `datetime` DESC  LIMIT 1");
        List results = d.selectList("SELECT `result` FROM results  WHERE `job`=\""+job+"\" AND `failed` = 0 ORDER BY `datetime` DESC  LIMIT 1");
        listener.getLogger().println("Did the select.");
        for(Object result : results){
            listener.getLogger().println("Trying to parse the result: "+result.toString());
           answer = Double.parseDouble(result.toString().replace("[", "").replace("]", ""));
        }
        d.disconnect();        
        return answer;
    }
    /**
     * This will set the threshold for a given job
     * also it will keep track of whether the build failed or not.
     * @param job Name of the current job.
     * @param build Current build number.
     * @param threshold The Threshold being stored.
     * @param failed Whether the build failed or not 1 or 0.
     * @throws java.lang.Exception
     */
    public void setThreshold(String job,int build, double threshold,int failed) throws Exception{
        try{
            Database d = new Database(getDescriptor().getDatabase());
            String status = d.connect();
            d.insert("insert into results values(strftime('%Y-%m-%d %H:%M:%f','now'),'"+job+"',"+Integer.toString(build)+","+Double.toString(threshold)+","+failed+")");
        }catch(Exception e){
            throw new Exception("Error: " + e);
        }
    }
    /**
     * This will set the threshold for a given job
     * also it will keep track of whether the build failed or not.
     * @param job Name of the current job.
     * @param build Current build number.
     * @param threshold The Threshold being stored.
     * @param failed Whether the build failed or not 1 or 0.
     * @param listener Current BuildListener - used for output.
     * @throws java.lang.Exception
     */
    public void setThreshold(String job,int build, double threshold,int failed, BuildListener listener) throws Exception{
        try{
            Database d = new Database(getDescriptor().getDatabase());
            String status = d.connect();
            d.insert("insert into results values(strftime('%Y-%m-%d %H:%M:%f','now'),'"+job+"',"+Integer.toString(build)+","+Double.toString(threshold)+","+failed+")");
            listener.getLogger().println("Added Threshold reading.");
        }catch(Exception e){
            throw new Exception("Error: " + e);
        }
    }
    /**  
     * Returns boolean indicating pass/fail.
     * This is the main section of the Jenkins plugin. The perform is what gets 
     * called when the plugin is executed. 
     * @param build
     * @param launcher
     * @param listener
     * @return  whether it passed or failed.
     */
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        AbstractProject project= build.getProject();
        listener.getLogger().println("Ratchet is trying to parse your output. Version 0.1.133");
        listener.getLogger().println("File: " +file);
        String result = get_result(build,launcher,listener);
        listener.getLogger().println("Trying to load the threshold: "+project.getFullName());
        Double Threshold;
        try {
            Threshold = getThreshold(project.getFullName(),listener);
        } catch (Exception ex) {
            listener.getLogger().println("Load ERROR: " +ex.toString());
            return false; 
        }
        listener.getLogger().println("Running get_result : "+result +" DONE READING FILE.");
        Pattern pat = Pattern.compile("\\d?\\d?\\d?\\.\\d?\\d?");
        Matcher mat = pat.matcher(result);
        Double current =0.0;
        while (mat.find()){
            listener.getLogger().println("Trying to parse on: "+mat.group()+" vs. "+ Threshold);
           try{
                current = Double.parseDouble(mat.group());
            }catch (NumberFormatException e){
                 listener.getLogger().println("Unable to parse Ratchet input.");
            }
        }
        if (override != null && override.length() > 0){
            try{
                Threshold = Double.parseDouble(override);
            }catch (NumberFormatException e){
                listener.getLogger().println("Unable to parse override.2");
            }
        }
        listener.getLogger().println("________________________________________");
        listener.getLogger().println("________Ratcheting on:__________________");
        listener.getLogger().println("________________________________________");
        listener.getLogger().println("Current: " +current + "| Threshold: "+Threshold);
        listener.getLogger().println("________________________________________");
        try{//let's make sure they supplied a report before we try to use it
            Statistics s = new Statistics(new Database(getDescriptor().getDatabase()));
            if(current >=Threshold){
                setThreshold(project.getFullName(),build.getNumber(),current,0);
                listener.getLogger().println("About to generate html: ");//+build.getWorkspace().toURI().toString().substring(6) +"output.html");
                String output = s.GenerateHtml(launcher,project.getFullName());
                listener.getLogger().println(wrFile(listener,launcher,output,build.getWorkspace().toURI().toString().substring(6) +report));
            }else{
                setThreshold(project.getFullName(),build.getNumber(),current,1);
                build.setDescription("Failed because threshold was not met: " +current.toString() + " vs. "+Threshold.toString());
                listener.getLogger().println("FAILED to pass threshold.");
                String output = s.GenerateHtml(launcher,/*listener,*/project.getFullName());
                listener.getLogger().println(wrFile(listener,launcher,output,build.getWorkspace().toURI().toString().substring(6) +report));
                throw new Exception("Failed to pass threshold.");
            }
        }catch(Exception e){
            listener.getLogger().println("Save ERROR: " +e.toString());
            return false;
        }
        return true; // Unless the job failed.. 
    }
    /**
     * 
     * This is a file writer wrapper that executes on the slave, writing a
     * string to a text file.
     * 
     * @param listener Current Jenkins Buildlistener
     * @param launcher Current Jenkins Launcher
     * @param fileName File path/name where the file will be written.
     * @param output   Contents that will be written to the file.
     * @return 
     */
    public String wrFile(BuildListener listener,Launcher launcher,String output, String fileName){
        String result = "FAILED TO EXECUTE REMOTELY";
        final String filePath = fileName;
        final String contents = output;
        listener.getLogger().println("File_path: " +filePath);
        Callable<String, IOException> task = new Callable<String, IOException>() { // Create a serialized method that will run on the slave.
            @Override
            public String call() throws IOException {
                try {
                    File file = new File(filePath);
                    file.getParentFile().mkdirs();
                    PrintWriter out = new PrintWriter(file);
                    out.println(contents);

                    out.close();
                return "Should be done writing the file now:" + filePath;
                } catch (Exception ex){
                    return "CAllible died: "+ex.toString();
                } 
            }
        };
        try { // Get a "channel" to the build machine and run the task there
            result = launcher.getChannel().call(task);
            listener.getLogger().println("Result3: "+ result );
        }catch (Exception ex) {
            result = "Failed in wrFile: " + ex.toString();
        }
        return result;
    }
    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link Ratchet}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private String database;
        public DescriptorImpl(){
                load();
            }
        /**
         * Performs on-the-fly validation of the form field 'file'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         */
        public FormValidation doCheckFile(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a filename");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the filename too short?");
            return FormValidation.ok();
        }
        /**
         * Performs on-the-fly validation of the form field 'file'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         */
        public FormValidation doCheckReport(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a report name");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the filename too short?");
            if (!value.contains(".html")){
                return FormValidation.warning("This report is html, so your name should end with .html");
            }
            return FormValidation.ok();
        }
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         * @return String with the Plugin name.
         */
        @Override
        public String getDisplayName() {
            return "Ratcheting";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            database = formData.getString("database");
            //regex = formData.getString("regex");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         *
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         * @return Database path/name
         * @throws java.lang.Exception
         */
        public String getDatabase() throws Exception{
            if(database != null && database.length() > 0){
                return database;
            }
            throw new Exception("FAILED: You must configure a database location in  your Jenkins configuration.");
            
        }
    }
    
}