package edu.uchicago.monitor;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;

import static org.jboss.netty.buffer.ChannelBuffers.*;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.util.CharsetUtil;


public class Collector {

	private final String sitename;

	private int tos;
	private int pid;
	private byte fseq;
	
	private DatagramChannelFactory f;
	private ConnectionlessBootstrap b;

	public final Map<Integer, FileStatistics> fmap = new HashMap<Integer, FileStatistics>();
	
	private AtomicInteger connectionAttempts = new AtomicInteger();
	private AtomicInteger currentConnections = new AtomicInteger();
	private AtomicInteger successfulConnections = new AtomicInteger();
	public AtomicLong totBytesRead = new AtomicLong();
	public AtomicLong totBytesWriten = new AtomicLong();

	private CollectorAddresses ca=new CollectorAddresses();
	Timer timer = new Timer();
	
	Collector() {
		Properties properties = System.getProperties();
		sitename = properties.getProperty("sitename");
		System.out.println("sitename: " + sitename);
		init();
	}

	private void init() {
		System.out.println(ca.toString());
		tos = (int) (System.currentTimeMillis() / 1000L);

		try {
			pid = Integer.parseInt(new File("/proc/self").getCanonicalFile().getName());
		} catch (Exception e) {
			System.err.println("could not get PID from /proc/self. Setting it to 123456.");
			pid = 123456;
		}

		f = new NioDatagramChannelFactory(Executors.newCachedThreadPool());
		b = new ConnectionlessBootstrap(f);

		b.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(new StringEncoder(CharsetUtil.ISO_8859_1), new StringDecoder(CharsetUtil.ISO_8859_1),
						new SimpleChannelUpstreamHandler());
			}
		});
		b.setOption("receiveBufferSizePredictorFactory", new FixedReceiveBufferSizePredictorFactory(1024));
		for (Address a:ca.summary)
			timer.schedule(new SendSummaryStatisticsTask(a), 0, a.delay * 1000);
		for (Address a:ca.detailed)
			timer.schedule(new SendDetailedStatisticsTask(a), 0, a.delay * 1000);
	}

	public void addConnectionAttempt() {
		connectionAttempts.getAndIncrement();
	}

	public void openEvent(UUID connectionId, FileStatistics fs) {
		// Note that status may be null - only available if client requested it
		System.out.println(">>>Opened " + connectionId + "\n" + fs.toString());
	}

	public void closeEvent(UUID connectionId, FileStatistics fs) {
		System.out.println(">>>Closed " + connectionId + "\n" + fs.toString());

	}

	public void connectedEvent(UUID connectionId, SocketAddress remoteAddress) {
		currentConnections.getAndIncrement();
		System.out.println(">>>Connected " + connectionId + " " + remoteAddress);
	}

	public void disconnectedEvent(UUID connectionId, long duration) {
		currentConnections.getAndDecrement();
		successfulConnections.getAndIncrement();
		System.out.println(">>>Disconnected " + connectionId + " " + duration + "ms");
	}

	@Override
	public String toString() {
		String res = new String();
		res += "SUMMARY ----------------------------------\n";
		res += "Connection Attempts:     " + connectionAttempts.get() + "\n";
		res += "Current Connections:     " + currentConnections.toString() + "\n";
		res += "Connections established: " + successfulConnections.toString() + "\n";
		res += "Bytes Read:              " + totBytesRead.toString() + "\n";
		res += "Bytes Written:           " + totBytesWriten.toString() + "\n";
		res += "SUMMARY ----------------------------------\n";
		return res;
	}

	private class SendSummaryStatisticsTask extends TimerTask {
		private String info;
		private Address a;
		SendSummaryStatisticsTask(Address a){
			this.a=a;
			info = "<stats id=\"info\"><host>xxx." + sitename + "</host><port>" + a.port + "</port><name>anon</name></stats>";
		}
		public void run() {
			long tod = System.currentTimeMillis() / 1000L - 60;
			String sgen = "<stats id=\"sgen\"><as>1</as><et>60000</et><toe>" + (tod + 60) + "</toe></stats>";
			String link = "<stats id=\"link\"><num>1</num><maxn>1</maxn><tot>20</tot><in>" + totBytesWriten.toString() + "</in><out>" + totBytesRead.toString()
					+ "</out><ctime>0</ctime><tmo>0</tmo><stall>0</stall><sfps>0</sfps></stats>";
			String xmlmessage = "<statistics tod=\"" + tod + "\" ver=\"v1.9.12.21\" tos=\"" + tos + "\" src=\"xxx." + sitename
					+ "\" pgm=\"xrootd\" ins=\"anon\" pid=\"" + pid + "\">" + info + sgen + link + "</statistics>";
			// System.out.println(xmlmessage);
			
			DatagramChannel c = (DatagramChannel) b.bind(new InetSocketAddress(0));

			c.write(xmlmessage, new InetSocketAddress(a.address, a.port));
			
		}
	}
	
	private class SendDetailedStatisticsTask extends TimerTask {
		private Address a;
//		private String info;
		SendDetailedStatisticsTask(Address a){
			this.a=a;
//			info = "<stats id=\"info\"><host>xxx." + sitename + "</host><port>" + a.port + "</port><name>anon</name></stats>";
		}
		public void run() {
			sendFstream();
//			long tod = System.currentTimeMillis() / 1000L - 60;
//			String sgen = "<stats id=\"sgen\"><as>1</as><et>60000</et><toe>" + (tod + 60) + "</toe></stats>";
//			String link = "<stats id=\"link\"><num>1</num><maxn>1</maxn><tot>20</tot><in>" + totBytesWriten.toString() + "</in><out>" + totBytesRead.toString()
//					+ "</out><ctime>0</ctime><tmo>0</tmo><stall>0</stall><sfps>0</sfps></stats>";
//			String xmlmessage = "<statistics tod=\"" + tod + "\" ver=\"v1.9.12.21\" tos=\"" + tos + "\" src=\"xxx." + sitename
//					+ "\" pgm=\"xrootd\" ins=\"anon\" pid=\"" + pid + "\">" + info + sgen + link + "</statistics>";
//			// System.out.println(xmlmessage);
		}
		private void sendFstream(){
			System.out.println("sending detailed stream");
			DatagramChannel c = (DatagramChannel) b.bind(new InetSocketAddress(0));
			short plen= (short) (24+32*fmap.size()); 
			ChannelBuffer db = dynamicBuffer(24);
			
			// main header
			db.writeByte((byte) 102); // 'f'
			db.writeByte((byte) fseq);
			db.writeShort(plen);
			db.writeInt(tos);
			
			// first timing header
			db.writeByte((byte) 2); // 2 - means isTime 
			db.writeByte((byte) 0); // no meaning here
			db.writeShort(8);       // size of this header
			db.writeInt(tos);       // unix time
			
			for(Map.Entry<Integer, FileStatistics> entry : fmap.entrySet() ){

				db.writeByte((byte) 3); // fileIO report
				db.writeByte((byte) 0); // no meaning
				db.writeShort(32); // 3*longlong + this header itself
				db.writeInt(entry.getKey()); // fileid
				db.writeLong(entry.getValue().bytesRead.get());
				db.writeLong(entry.getValue().bytesVectorRead.get());
				db.writeLong(entry.getValue().bytesWritten.get());
				
			}
			
			// last timing header
			db.writeByte((byte) 2); 
			db.writeByte((byte) 0);
			db.writeShort(8);
			db.writeInt(tos);
			
			
			c.write(db, new InetSocketAddress(a.address, a.port));
		}
		
	}
	


}
