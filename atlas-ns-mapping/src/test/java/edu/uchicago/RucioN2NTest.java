package edu.uchicago;

import junit.framework.TestCase;
import edu.uchicago.xrootd4j.RucioN2N;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class RucioN2NTest extends TestCase {

	private static Logger log = Logger.getLogger(RucioN2NTest.class);
	private Properties p=new Properties();
	
	public void testRucioInitialization() {
		log.info("test initialization...");
		PropertyConfigurator.configure(RucioN2NTest.class.getClassLoader().getResource("log4j.properties"));
		p.setProperty("site","MWT2");
		RucioN2N rucio=new RucioN2N(p);
		assert(rucio!=null);
	}
	
}
