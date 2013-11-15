package edu.uchicago.monitor;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionInfo {
	final static Logger logger = LoggerFactory.getLogger(ConnectionInfo.class);
	public int connectionID;
	public UserInfo ui;
	public long duration;
	private int serverPort;
	public HashMap<Integer, FileStatistics> allFiles;

	public Boolean disconnected; // initially this is 0. on disconnect will be
									// changed to 1 and upon sending f-stream
									// info will get removed.

	public ConnectionInfo(int connID, int DetailedLocalSendingPort) {
		this.disconnected = false;
		this.connectionID = connID;
		this.serverPort = DetailedLocalSendingPort;
		duration = System.currentTimeMillis();
		allFiles = new HashMap<Integer, FileStatistics>();
	}

	public void ConnectionClose() {
		logger.info("CLOSED CONNECTION: {}   duration: {}", connectionID, (System.currentTimeMillis() - duration) / 1000);
		this.disconnected = true;
	}

	public void logUserRequest(String name, int pid) {
		ui = new UserInfo(name, pid, serverPort);
	}

	public void logUserResponse(String host, int port) {
		ui.setHostPort(host, port);
		logger.info("LOGGED {} {}", connectionID, ui.getInfo());
	}

	public void addFile(Integer handle, FileStatistics fi) {
		fi.state |= 0x0003;
		allFiles.put(handle, fi);
	}

	public FileStatistics getFile(Integer handle) {
		return allFiles.get(handle);
	}

	public void removeFile(Integer handle) {
		allFiles.remove(handle);
	}

	public String toString() {
		String ret = "disconnected: " + disconnected + "\t\tduration: " + (System.currentTimeMillis() - duration) / 1000 + "\n" + ui.toString();
		for(Map.Entry<Integer, FileStatistics> fi:allFiles.entrySet()){
			ret += "\n handle: " + fi.getKey() + "\t path: " + fi.getValue().toString();
		}
		return ret;

	}

}
