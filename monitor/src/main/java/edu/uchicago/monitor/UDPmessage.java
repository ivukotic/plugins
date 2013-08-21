package edu.uchicago.monitor;

import org.jboss.netty.buffer.ChannelBuffer;

public class UDPmessage {

	private ChannelBuffer contents;

	private boolean available = false;

	public synchronized ChannelBuffer get() {
		while (available == false) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
		available = false;
		notifyAll();
		return contents;
	}

	public synchronized void put(ChannelBuffer value) {
		while (available == true) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
		contents = value;
		available = true;
		notifyAll();
	}
	
}
