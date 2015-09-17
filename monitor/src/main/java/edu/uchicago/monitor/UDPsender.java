package edu.uchicago.monitor;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UDPsender extends Thread {
	private final static Logger logger = LoggerFactory.getLogger(UDPsender.class);

	private CollectorAddresses ca;
	private UDPmessage message;

	private DatagramSocket detailedSocket = null;

	int init(CollectorAddresses ca, UDPmessage m) {

		this.message = m;
		this.ca=ca;

		try {
			detailedSocket = new DatagramSocket();
		} catch (IOException e) {
			logger.warn("can not create UDP detailed socket:" + e.getLocalizedMessage());
		}

		return 0;
	}

	public void run() {
		logger.info("UDP sender started.");
		while(true){	
//			logger.debug("UDPsender trying to get message!!!!!!!!!!!!!");
			byte[] b = message.get().array();
//			logger.debug("UDPsender GOT message!!!!!!!!!!!!!");
			for (Address a : ca.detailed) {
				DatagramPacket dp = new DatagramPacket(b, b.length, a.getDestination());
				try {
					detailedSocket.send(dp);
				} catch (IOException e) {
					logger.error("unrecognized exception in sending detailed stream: {}", e.getMessage());
				}
			}
		}
	}
}

