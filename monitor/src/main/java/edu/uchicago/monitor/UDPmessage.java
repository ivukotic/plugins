package edu.uchicago.monitor;

import io.netty.buffer.ByteBuf;


public class UDPmessage {

	private ByteBuf contents;

	private boolean available = false;

	public synchronized ByteBuf get() {
		while (!available) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
		available = false;
		notifyAll();
		return contents;
	}

	public synchronized void put(ByteBuf value) {
		while (available) {
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
