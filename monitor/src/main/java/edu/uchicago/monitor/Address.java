package edu.uchicago.monitor;

import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Address {

	private final static Logger logger = LoggerFactory.getLogger(Address.class);
	public final String address;
	public final int port;
	public final int delay;
	public final int outboundport;
	private final InetSocketAddress destination;

	public Address(String param) throws IllegalArgumentException {
		String[] temp;
		temp = param.split(":");
		if (temp.length < 2)
			throw new IllegalArgumentException("Incorrect collector address format: " + param
					+ " format should be <address>:<port>[:<delay>[:<outbound port>]].");
		address = temp[0];

		try {
			port = Integer.parseInt(temp[1]);
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Incorrect collector address format: port: " + temp[1] + " should be integer number.");
		}

		int outboundport = 0;
		if (temp.length > 2) {
			try {
				delay = Integer.parseInt(temp[2]);
			} catch (NumberFormatException ex) {
				throw new IllegalArgumentException("Incorrect collector sending interval: " + temp[2] + " should be integer number.");
			}

			if (temp.length == 4) {
				try {
					outboundport = Integer.parseInt(temp[3]);
				} catch (NumberFormatException ex) {
					throw new IllegalArgumentException("Incorrect collector outbound port: " + temp[3] + " should be integer number.");
				}
			}
		} else {
			delay = 60;
		}
		this.outboundport = outboundport;
		this.destination = new InetSocketAddress(this.address,this.port);
	}
	
	public InetSocketAddress getDestination(){
		return this.destination;
	}

	public void print() {
		logger.info("Hostname: " + address + "\t port: " + port + ".\t Sending each " + delay + " seconds. Outbound port: " + outboundport + "\n");
	}
}
