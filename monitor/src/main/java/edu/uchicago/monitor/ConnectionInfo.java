package edu.uchicago.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionInfo {
	final static Logger logger = LoggerFactory.getLogger(ConnectionInfo.class);
	public int connectionID;
	public UserInfo ui;
	public int filesOpen;
	public long duration;

	public Boolean disconnected; // initially this is 0. on disconnect will be changed to 1 and upon sending f-stream info will get removed.
	
	public ConnectionInfo(int connID) {
		this.disconnected = false; 
		this.connectionID = connID;
		duration = System.currentTimeMillis();
		filesOpen=0;
	}

	public void ConnectionClose() {
		logger.info("CLOSED CONNECTION: " + connectionID + "   duration: " + (System.currentTimeMillis() - duration) / 1000);
		this.disconnected = true; 
	}
	
	public void logUserRequest(String name, int pid){
		ui=new UserInfo(name, pid);
	}	
	
	public void logUserResponse(String host, int port){
		ui.setHostPort(host, port);
		logger.info("LOGGED " + connectionID + " " + ui.getInfo());
	}

	public String toString() {
		return "disconnected: "+disconnected+"\t\tfilesOpen:  "+filesOpen+"\t\tduration: " + (System.currentTimeMillis()-duration)/1000+"\n"+ui.toString();
	}
	
	
}
