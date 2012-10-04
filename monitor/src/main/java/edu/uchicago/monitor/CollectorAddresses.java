package edu.uchicago.monitor;

import java.util.ArrayList;
import java.util.Properties;

public class CollectorAddresses {

	public ArrayList<Address> summary = new ArrayList<Address>();
	public ArrayList<Address> detailed = new ArrayList<Address>();
	
	CollectorAddresses() {
		Properties properties = System.getProperties();
		
		String pSummary = properties.getProperty("summary");
		String[] tempSummary = pSummary.split(",");
		for (int i = 0; i < tempSummary.length; i++) {
			try {
				summary.add(new Address(tempSummary[i]));
			} catch (IllegalArgumentException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			} 
		}

		String pDetailed = properties.getProperty("detailed");
		String[] tempDetailed = pDetailed.split(",");
		for (int i = 0; i < tempDetailed.length; i++) {
			try {
				detailed.add(new Address(tempDetailed[i]));
			} catch (IllegalArgumentException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
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
