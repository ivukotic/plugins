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

		String pSummary = properties.getProperty("xrootd.monitor.summary");
		if (pSummary != null) {
			for (String summaryAddress : pSummary.split(",")) {
				try {
					this.summary.add(new Address(summaryAddress));
				} catch (IllegalArgumentException e) {
					logger.warn("could not interpret summary address {}: {}", summaryAddress, e.getMessage());
				}
			}
		}

		String pDetailed = properties.getProperty("xrootd.monitor.detailed");
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
	
	public void print() {
		if (summary.size() > 0) {
			logger.info("Summary information will be sent to:");
			for (Address a : summary) {
				a.print();
			}
		} else {
			logger.warn("Summary information will not be sent.");
		}

		if (detailed.size() > 0) {
			logger.info("Detailed information will be sent to:");
			for (Address a : detailed) {
				a.print();
			}
		} else {
			logger.warn("Detailed information will not be sent.");
		}
	}

}
