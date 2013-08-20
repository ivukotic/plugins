package edu.uchicago.monitor;

public class UserInfo {
	public String name;
	public int pid;
	public String host;
	public int port;
	
	UserInfo(String name, int pid) {
		this.name = name;
		this.pid = pid;
	}
	
	public void setHostPort(String host, int port){
		this.host=host;
		this.port=port;
	}
	
	public String getInfo() {
		return name + "." + pid + ":" + (pid << 16 | port) + "@" + host + "\n&p=X509&n=" + name + "&h=" + host + "&o=&r=&g=&m=";
	}
	
	public String toString(){
		return "name: "+name+"\t\thost: "+host+"\t\tport: "+port+"\t\tpid: "+pid;
	}
}
