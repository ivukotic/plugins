package edu.uchicago.monitor;

public class UserInfo {
	private final String name;
	private final int pid;
	private String host;
	private int port;
	private final int serverPort;

	UserInfo(String name, int pid, int serverPort) {
		this.name = name;
		this.pid = pid;
		this.serverPort = serverPort;
	}

	public void setHostPort(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public String getInfo() {
		return name + "." + pid + ":" + ((long) pid << 16 | serverPort) + "@" + host;
	}
	
	public String getFullInfo(String vo) {
		return name + "." + pid + ":" + ((long) pid << 16 | serverPort) + "@" + host + "\n&p=X509&n=" + name + "&h=" + host + "&o="+vo+"&r=&g=&m=";
	}

	public String toString() {
		return "name: " + name + "\t\thost: " + host + "\t\tport: " + port + "\t\tpid: " + pid + "\t\tserverPort: " + serverPort;
	}
}
