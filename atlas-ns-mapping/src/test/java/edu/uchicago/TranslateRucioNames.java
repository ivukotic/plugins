package edu.uchicago;

import static org.junit.Assert.*;

import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.Test;

import edu.uchicago.xrootd4j.RucioN2N;

public class TranslateRucioNames {

	private static Logger log = Logger.getLogger(TranslateRucioNames.class);
	@Test
	public void test() {
		log.info("test both initialization and translation.");

		Properties p=new Properties();
		PropertyConfigurator.configure(RucioN2NTest.class.getClassLoader().getResource("log4j.properties"));
		p.setProperty("site","MWT2");
		
		RucioN2N rucio=new RucioN2N(p);
		

		log.info("test translation...");
		String gLFN1="/atlas/rucio/mc12_8TeV:NTUP_TOP.01213329._025034.root.1";
		String pfn1=rucio.translate(gLFN1);
		log.info(pfn1);
		assertTrue(pfn1!=null);
		log.info("done. ");
		String pfn2=rucio.translate(gLFN1);
		assertTrue(pfn2!=null);
		log.info("done. ");
		
	}

}
