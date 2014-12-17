package edu.uchicago.monitor;

import java.util.ArrayList;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class CollectorAddresses {

	private final static Logger logger = LoggerFactory.getLogger(CollectorAddresses.class);
	public final ArrayList<Address> summary = new ArrayList<>();
	public final ArrayList<Address> detailed = new ArrayList<>();
	public boolean reportSummary = false;
	public boolean reportDetailed = false;

	CollectorAddresses() {
	}
	
	public void init(Properties properties){

		String pSummary = properties.getProperty("summary");
		if (pSummary != null) {
			for (String summaryAddress : pSummary.split(",")) {
				try {
					this.summary.add(new Address(summaryAddress));
				} catch (IllegalArgumentException e) {
					logger.warn("could not interpret summary address {}: {}", summaryAddress, e.getMessage());
				}
			}
		}

		String pDetailed = properties.getProperty("detailed");
		if (pDetailed != null) {
			for (String detailedAddress : pDetailed.split(",")) {
				try {
					detailed.add(new Address(detailedAddress));
				} catch (IllegalArgumentException e) {
					logger.warn("could not interpret detailed address {}: {}",detailedAddress, e.getMessage());
				}
			}
		}

		if (summary.size() > 0)
			reportSummary = true;
		if (detailed.size() > 0)
			reportDetailed = true;
		if (summary.size() == 0 && detailed.size() == 0) {
			logger.warn("No addresses given to send monitoring info. Please provide addresses or turn off monitoring.");
		}
	}
	
	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		if (summary.size() > 0) {
			res.append("Summary information will be sent to: \n");
			for (Address a : summary) {
				res.append(a);
			}
		} else {
			res.append("Summary information will not be sent.\n");
		}

		if (detailed.size() > 0) {
			res.append("Detailed information will be sent to: \n");
			for (Address a : detailed) {
				res.append(a.toString());
				break;
			}
		} else {
			res.append("Detailed information will not be sent.\n");
		}
		return res.toString();
	}

}
