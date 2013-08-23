package edu.uchicago.monitor;

import static org.junit.Assert.*;

import org.dcache.xrootd.plugins.ChannelHandlerFactory;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.Executors;

public class MonitorChannelHandlerFactoryTest {

	final static Logger logger = LoggerFactory.getLogger(MonitorChannelHandlerFactoryTest.class);

	@Test
	public void testMonitorChannelHandlerFactory() {
		// fail("Not yet implemented");
		logger.info("testing creation of monitor handler.");
		

//		Properties props = new Properties();
//		props.setProperty("site", "MWT2_UC");
//		props.setProperty("servername", "ilijasMac.uchicago.edu");
//		props.setProperty("summary", "atl-prod05.slac.stanford.edu:9931:10");
//		// props.setProperty("summary","uct2-int.uchicago.edu:9931:10");
//		props.setProperty("detailed", "atl-prod05.slac.stanford.edu:9930:10");
//		// props.setProperty("detailed","uct2-int.uchicago.edu:9930:10");
//		
//		MonitorChannelHandlerProvider mchp = new MonitorChannelHandlerProvider();
//		ChannelHandlerFactory mchf = mchp.createFactory("edu.uchicago.monitor", props);
//		logger.info(mchf.getDescription());
//		MonitorChannelHandler mch = (MonitorChannelHandler) mchf.createHandler();
//
//		for (int w = 0; w < 3000; w++) {
//			
//			try {
//				Thread.sleep(3 * 1000);
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
//		}
		
		logger.info("done.");
		logger.info("===================================================================");
	}

	@Test
	public void testCollector() {
		logger.info("testing message sender");

		Properties props = new Properties();
		props.setProperty("site", "MWT2_UC");
		props.setProperty("servername", "ilijasMac.uchicago.edu");

		props.setProperty("summary", "atl-prod05.slac.stanford.edu:9931:10");
		// props.setProperty("summary","uct2-int.uchicago.edu:9931:10");
		props.setProperty("detailed", "atl-prod05.slac.stanford.edu:9930:10:9999");
		// props.setProperty("detailed","uct2-int.uchicago.edu:9930:10");

		int cid=123456;
		Collector c = new Collector(props);
		for (int w = 0; w < 3000; w++) {

			// user logged in message "u"
//			c.SendMapMessage((byte) 117, cid, "");
			// open file map message "d"
//			c.SendMapMessage((byte) 100, 123456 + 0, "" );

			c.totBytesRead.getAndAdd(300 * 1024 * 1024); // 300 MB each 3
															// seconds
//			c.SendMapMessage((byte) 0, 15145, "content");
			try {
				Thread.sleep(3 * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		logger.info("done.");
		logger.info("===================================================================");

	}

}
