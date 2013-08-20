package edu.uchicago.monitor;

public class UserInfo {
	public String name;
	public int pid;
	public String host;
	public int port;
	public Boolean disconnected; // initially this is 0. on disconnect will be changed to 1 and upon sending f-stream info will get removed.

	UserInfo(String name, int pid) {
		this.name = name;
		this.pid = pid;
		this.disconnected = false; 
	}

	public String getInfo() {
		return name + "." + pid + ":" + (pid << 16 | port) + "@" + host + "\n&p=X509&n=" + name + "&h=" + host + "&o=&r=&g=&m=";
	}
	
	public String toString(){
		return "name: "+name+"\t\thost: "+host+"\t\tport: "+port+"\t\tpid: "+pid;
	}
}
