package edu.uchicago.monitor;

import java.util.ArrayList;
import java.util.Properties;

public class CollectorAddresses {

	public ArrayList<Address> summary = new ArrayList<Address>();
	public ArrayList<Address> detailed = new ArrayList<Address>();

	CollectorAddresses() {
		Properties properties = System.getProperties();
		boolean on = false;

		String pSummary = properties.getProperty("summary");
		if (pSummary != null) {
			String[] tempSummary = pSummary.split(",");
			for (int i = 0; i < tempSummary.length; i++) {
				try {
					summary.add(new Address(tempSummary[i]));
					on = true;
				} catch (IllegalArgumentException e) {
					System.err.println(e.getMessage());
					System.exit(1);
				}
			}
		}

		String pDetailed = properties.getProperty("detailed");
		if (pDetailed != null) {
			String[] tempDetailed = pDetailed.split(",");
			for (int i = 0; i < tempDetailed.length; i++) {
				try {
					detailed.add(new Address(tempDetailed[i]));
					on = true;
				} catch (IllegalArgumentException e) {
					System.err.println(e.getMessage());
					System.exit(1);
				}
			}
		}
		if (!on) {
			System.err.println(" *** Err: No monitoring stream set-up. Set one up or remove monitoring plugin from configuration.");
			System.exit(1);
		}
	}

	@Override
	public String toString() {
		String res = "";
		if (summary.size() > 0) {
			res += "Summary information will be sent to: \n";
			for (Address a : summary) {
				res += a.toString();
			}
		} else
			res += "Summary information will not be sent.";

		if (detailed.size() > 0) {
			res += "Detailed information will be sent to: \n";
			for (Address a : detailed) {
				res += a.toString();
			}
		} else
			res += "Detailed information will not be sent.";

		if (summary.size() == 0 && detailed.size() == 0) {
			System.err.println(" *** ERR: no addresses given to send monitoring info. Please provide addresses or turn off monitoring.");
			System.exit(3);
		}
		return res;
	}

}
