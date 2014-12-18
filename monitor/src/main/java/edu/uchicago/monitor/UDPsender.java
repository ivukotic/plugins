package edu.uchicago.monitor;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UDPsender extends Thread {
	private final static Logger logger = LoggerFactory.getLogger(UDPsender.class);

	private ConnectionlessBootstrap cbsDetailed;
	private DatagramChannelFactory f;
	private InetSocketAddress destination;
	private DatagramChannel c;
	
	private UDPmessage message;

	int init(CollectorAddresses ca, UDPmessage m) {

		message = m;		
		f = new NioDatagramChannelFactory(Executors.newCachedThreadPool());
		cbsDetailed = new ConnectionlessBootstrap(f);
		cbsDetailed.setOption("localAddress", new InetSocketAddress(0));
		cbsDetailed.setOption("broadcast", "true");
		cbsDetailed.setOption("connectTimeoutMillis", 10000);
		
		for (Address a : ca.detailed) {
			cbsDetailed.setOption("localAddress", new InetSocketAddress(a.outboundport));
			destination = new InetSocketAddress(a.address, a.port);
		}
		
		cbsDetailed.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline( new SimpleChannelUpstreamHandler() );
			}
		});
		
		c = (DatagramChannel) cbsDetailed.bind();
		
		return c.getLocalAddress().getPort();
	}

	public void run() {
		
		while (true) {
			ChannelFuture f = c.write(message.get(), destination);
			f.addListener(new ChannelFutureListener() {
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess())
						logger.info("detailed stream IO completed. success!");
					else {
						logger.error("detailed stream IO completed. did not send info: {}", future.getCause().toString());
					}
				}
			});
			
		}
		
	}

}
