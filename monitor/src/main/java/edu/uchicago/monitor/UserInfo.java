package edu.uchicago.monitor;

public class UserInfo {
	public String name;
	public int pid;
	public String host;
	public int port;

	// is there a way to get users host?
	// main servers port hardcoded to 1096.
	UserInfo(String name, int pid) {
		this.name = name;
		this.pid = pid;
	}

	public String getInfo() {
		return name + "." + pid + ":" + (pid << 16 | port) + "@" + host + "\n&p=X509&n=" + name + "&h=" + host + "&o=&r=&g=&m=";
	}
}
