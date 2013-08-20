package edu.uchicago.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionInfo {
	final static Logger logger = LoggerFactory.getLogger(ConnectionInfo.class);
	public int connectionID;
	public long duration;

	public ConnectionInfo(int connID) {
		this.connectionID = connID;
		duration = System.currentTimeMillis();
	}

	public void ConnectionClose() {
		logger.info("CLOSED CONNECTION: " + connectionID + "   duration: " + (System.currentTimeMillis() - duration) / 1000);
	}

	public String toString() {
		return "duration: " + (System.currentTimeMillis()-duration)/1000;
	}
}
