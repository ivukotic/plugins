package edu.uchicago.monitor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionInfo {
	private final static Logger logger = LoggerFactory.getLogger(ConnectionInfo.class);

	private final int connectionID;
	public UserInfo ui;
	public final long duration;
	private final int serverPort;
	public final ConcurrentHashMap<Integer, FileStatistics> allFiles;

	public Boolean disconnected; // initially this is 0. on disconnect will be
									// changed to 1 and upon sending f-stream
									// info will get removed.

	public ConnectionInfo(int connID, int DetailedLocalSendingPort) {
		this.disconnected = false;
		this.connectionID = connID;
		this.serverPort = DetailedLocalSendingPort;
		this.duration = System.currentTimeMillis();
		this.allFiles = new ConcurrentHashMap<>();
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

	public synchronized void removeFile(Integer handle) {
		allFiles.remove(handle);
	}

	public String toString() {
		StringBuilder ret = new StringBuilder();
		ret.append("disconnected: ").append(disconnected).append("\t\tduration: ").append((System.currentTimeMillis() - duration) / 1000).append("\n").append(ui);
		for (Map.Entry<Integer, FileStatistics> fi : allFiles.entrySet()){
			ret.append("\n handle: ").append(fi.getKey()).append("\t path: ").append(fi.getValue());
		}
		return ret.toString();
	}

}
