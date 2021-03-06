/*
 * Web C programming environment
 * Copyright (c) 2010-2011, David H. Hovemeyer <david.hovemeyer@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.cloudcoder.submitsvc.oop.builder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.cloudcoder.daemon.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Run a subprocess, capturing its stdout and stderr as text.
 * Optionally, send text to the stdin of the process.
 * 
 * @author David Hovemeyer
 * @author Jaime Spacco
 */
public class ProcessRunner implements ITestOutput {
	private static final Logger logger=LoggerFactory.getLogger(ProcessRunner.class);
    
	private static String RUN_PROCESS_SCRIPT;
	static {
		// "Externalize" the runProcess.sh script.
		// If we're running out of a directory, then we can directly access the file
		// in the classpath.  If we're running out of a jarfile, then this will copy
		// runProcess.sh into a temporary file in the filesystem.
		try {
			String runProcessPath = ProcessRunner.class.getPackage().getName().replace('.', '/') + "/res/runProcess.sh";
			RUN_PROCESS_SCRIPT = Util.getExternalizedFileName(ProcessRunner.class.getClassLoader(), runProcessPath);
		} catch (IOException e) {
			throw new IllegalStateException("Couldn't get externalized path for runProcess.sh", e);
		}
	}
    
	private String statusMessage = "";
	
	private boolean exitStatusKnown;
	private boolean processStarted;
	private int exitCode;
	private boolean killedBySignal;
	
	private volatile Process process;
	private Thread exitValueMonitor;
	private String stdin;
	private IOutputCollector stdoutCollector;
	private IOutputCollector stderrCollector;
	private InputSender stdinSender;
	
	private Map<String,String> env=new HashMap<String,String>();
	
	/**
	 * Constructor.
	 */
	public ProcessRunner() {
	    for (Entry<String,String> entry : System.getenv().entrySet()) {
	        env.put(entry.getKey(), entry.getValue());
	    }
	}
	
	/**
	 * Set text to send to the process as its standard input.
	 * 
	 * @param stdin text to send to the process as its standard input
	 */
	public void setStdin(String stdin) {
		this.stdin = stdin;
	}
	
	/**
	 * Create the environment array defining the environment
	 * variables for the process.  The environment will
	 * contain all of the environment variables in the parent process,
	 * plus any extra ones specified by the extraVars parameter.
	 * 
	 * @param extraVars extra environment variables to define for the process,
	 *                  in the form VAR=value
	 * @return enviroment array
	 */
	protected String[] getEnvp(String... extraVars) {
	    String[] envp=new String[env.size() + extraVars.length];
	    int i=0;
	    for (Entry<String,String> entry : env.entrySet()) {
	        envp[i]=entry.getKey()+"="+entry.getValue();
	        i+=1;
	    }
	    for (String s : extraVars) {
	    	envp[i++] = s;
	    }
	    return envp;
	}
	
	public void addDirToPath(String dir) {
	    String path=env.get("PATH");
	    path+=File.separatorChar+dir;
	    env.put("PATH", path);
	}
	
	public String getStatusMessage() {
		return statusMessage;
	}
	
	public boolean runSynchronous(File workingDir, String[] command) {
		// wrap command (by default, using the runProcess.sh script)
		command = wrapCommand(command);
		
		// exec command
		logger.info("Running in {} the command: {}", workingDir.toString(), CUtil.mergeOneLine(command));
		try {
			// Create a temp file in which the runProcess.sh script can save
			// the exit status of the process.
			File exitStatusFile = File.createTempFile("ccxs", ".txt", workingDir);
			//logger.debug("Creating exit status file " + exitStatusFile.getPath());
			exitStatusFile.deleteOnExit();

			// Start process, setting CC_PROC_STAT_FILE env var
			// to indicate where runProcess.sh should write the process's
			// exit status information
			process = Runtime.getRuntime().exec(
					command,
					getEnvp("CC_PROC_STAT_FILE=" + exitStatusFile.getPath()),
					workingDir);

			// Collect process output
			stdoutCollector = createOutputCollector(process.getInputStream());
			stderrCollector = createOutputCollector(process.getErrorStream());
			stdoutCollector.start();
			stderrCollector.start();

			// If stdin was provided, send it
			if (stdin != null) {
				stdinSender = new InputSender(process.getOutputStream(), stdin);
				stdinSender.start();
			}

			// wait for process and output collector threads to finish
			exitCode = process.waitFor();
			stdoutCollector.join();
			stderrCollector.join();
			if (stdinSender != null) {
				stdinSender.join();
			}
			
			// Read the process's exit status information
			readProcessExitStatus(exitStatusFile);
			return true;
		} catch (IOException e) {
			statusMessage = "Could not execute process: " + e.getMessage();
		} catch (InterruptedException e) {
			statusMessage = "Process was interrupted (infinite loop killed?)";
		}
		return false;
	}

	protected String[] wrapCommand(String[] command) {
		List<String> cmd = new ArrayList<String>();
		cmd.add("/bin/bash");
		cmd.add(RUN_PROCESS_SCRIPT);
		cmd.addAll(Arrays.asList(command));
		return cmd.toArray(new String[cmd.size()]);
	}

	/**
	 * Create an IOutputCollector to be used to collect the stdout/stderr
	 * of the process.  Subclasses may override to precisely control how
	 * output is collected (for example, to limit the number of bytes/lines
	 * that will be collected.)
	 * 
	 * Default implementation returns an {@link OutputCollector}, which
	 * reads an unlimited amount of output.
	 * 
	 * @param inputStream the InputStream for the process's stdout or stderr
	 * @return an IOutputCollector to collect the process's stdout or stderr
	 */
	protected IOutputCollector createOutputCollector(InputStream inputStream) {
		return new OutputCollector(inputStream);
	}
	
	/**
	 * Read the file written by the runProcess.sh script
	 * which contains information about the process's exit status.
	 * 
	 * @param exitStatusFile file containing information about the process's exit status
	 */
	private void readProcessExitStatus(File exitStatusFile) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(exitStatusFile));
			String status = reader.readLine();
			String exitCode = reader.readLine();
			if (status != null && exitCode != null) {
				logger.debug("Read process exit status file: status={}, exitCode={}", status, exitCode);
				
				// Second line of file should be the exit code
				this.exitCode = Integer.parseInt(exitCode);
				
				if (status.equals("failed_to_execute")) {
					// The process could not be started
					this.processStarted = false;
					this.statusMessage = "Process could not be started";
					
					logger.debug("process stderr is {}", CUtil.mergeOneLine(stderrCollector.getCollectedOutput()));
				} else if (status.equals("exited")) {
					// The process exited normally.
					this.exitStatusKnown = true;
					this.processStarted = true;
					this.statusMessage = "Process exited";
				} else if (status.equals("terminated_by_signal")) {
					// The process was killed by a signal.
					// The exit code is the signal that terminated the process.
					this.exitStatusKnown = true;
					this.processStarted = true;
					this.killedBySignal = true;
					this.statusMessage = "Process crashed (terminated by signal " + this.exitCode + ")";
				} else {
					// Should not happen.
					logger.warn("Unknown process exit status " + status);
					this.exitStatusKnown = false;
					this.statusMessage = "Process status could not be determined";
				}
			}
		} catch (IOException e) {
			logger.warn("IOException trying to read process status file");
			this.exitStatusKnown = false;
			this.statusMessage = "Process status could not be determined";
		} catch (NumberFormatException e) {
			logger.warn("NumberFormatException trying to read process status file");
			this.exitStatusKnown = false;
			this.statusMessage = "Process status could not be determined";
		} finally {
			IOUtils.closeQuietly(reader);
			exitStatusFile.delete();
		}
	}

	public void runAsynchronous(final File workingDir, final String[] command) {
	    exitValueMonitor=new Thread() {
	        public void run() {
	            runSynchronous(workingDir, command);
	        }
	    };
	    exitValueMonitor.start();
	}
	
	/**
	 * Find out whether or not the exit status of this process is known.
	 * Because in Java it's not directly possible to find out things about
	 * how a process was terminated (such as whether it was killed by
	 * a signal), we use a wrapper script to collect information about the
	 * process's status and write this to a file that this class can read.
	 * However, we can't rule out the possibility that this file wasn't
	 * written or was corrupted in some way.  If this method returns true,
	 * then the process's exit status information is definitely known.
	 * <bImportant:</b> don't call this unless the process is definitely not running.
	 * 
	 * @return true if the process's exit status is definitely known,
	 *         false otherwise
	 */
	public boolean isExitStatusKnown() {
		return exitStatusKnown;
	}
	
	/**
	 * Find out whether or not the process was actually started.
	 * <b>Important:</b>: don't call this unless the process is definitely not running.
	 * 
	 * @return the processStarted
	 */
	public boolean isProcessStarted() {
		return processStarted;
	}
	
	/**
	 * Get the terminated process's exit code.
	 * If the process was killed by a signal, then the exit code is
	 * the number of the signal that killed the process.
	 * <b>Important:</b>: don't call this unless the process is definitely not running.
	 * 
	 * @return process's exit code, or the number of the signal that
	 *         killed the process
	 */
	public int getExitCode() {
		return exitCode;
	}
	
	/**
	 * Find out whether the process was killed by a signal.
	 * <b>Important:</b>: don't call this unless the process is definitely not running.
	 * 
	 * @return true if the process was killed by a signal, false otherwise
	 */
	public boolean isKilledBySignal() {
		return killedBySignal;
	}
	
	/* (non-Javadoc)
	 * @see org.cloudcoder.submitsvc.oop.builder.IHasStdoutAndStderr#getStdout()
	 */
	@Override
	public String getStdout() {
		return CUtil.merge(getStdoutAsList());
	}

	/**
	 * @return the standard output written by the process as a List of strings
	 */
	public List<String> getStdoutAsList() {
		return stdoutCollector.getCollectedOutput();
	}
	
	/* (non-Javadoc)
	 * @see org.cloudcoder.submitsvc.oop.builder.IHasStdoutAndStderr#getStderr()
	 */
	@Override
	public String getStderr() {
		return CUtil.merge(getStderrAsList());
	}

	/**
	 * @return the standard error written by the process as a List of strings
	 */
	public List<String> getStderrAsList() {
		return stderrCollector.getCollectedOutput();
	}

    public boolean isRunning() {
    	Process p = process;
    	
    	if (p == null) {
    		// Process hasn't started yet.
    		// Count that as "running".
    		return true;
    	}
    	
        try {
            p.exitValue();
            return false;
        } catch (IllegalThreadStateException e){
            return true;
        }
    }
    
    public void killProcess() {
        logger.info("Killing process");
        process.destroy();
        stdoutCollector.interrupt();
        stderrCollector.interrupt();
        if (stdinSender != null) {
        	stdinSender.interrupt();
        }
    }

	/**
	 * Determine whether or not the process ended with a fatal signal.
	 * <b>Important:</b>: don't call this unless the process is definitely not running.
	 * 
	 * @return true if the process ended with a fatal signal, false if
	 *         it exited normally
	 */
	public boolean isCoreDump() {
		return isKilledBySignal();
	}
}
