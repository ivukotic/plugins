package edu.uchicago.monitor;

public class Address {

	public String address;
	public int port;
	public int delay;
	public int outboundport;

	Address(String param) throws IllegalArgumentException {
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

		} else
			delay = 60;

	}

	@Override
	public String toString() {
		return "Hostname: " + address + "\t port: " + port + ".\t Sending each " + delay + " seconds. Outbound port" + outboundport + "\n";
	}
}
