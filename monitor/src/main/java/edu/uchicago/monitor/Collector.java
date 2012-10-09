package edu.uchicago.monitor;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
	private Properties properties;
	
	private int tos;
	private int pid;
	private byte fseq;

	private DatagramChannelFactory f;
	private ConnectionlessBootstrap b;

	public final Map<Integer, FileStatistics> fmap = new ConcurrentHashMap<Integer, FileStatistics>();

	private AtomicInteger connectionAttempts = new AtomicInteger();
	private AtomicInteger currentConnections = new AtomicInteger();
	private AtomicInteger successfulConnections = new AtomicInteger();
	public AtomicLong totBytesRead = new AtomicLong();
	public AtomicLong totBytesWriten = new AtomicLong();

	private CollectorAddresses ca = new CollectorAddresses();
	Timer timer = new Timer();

	Collector(Properties properties) {
		this.properties=properties;
		sitename = properties.getProperty("sitename");
		System.out.println("sitename: " + sitename);
		init();
	}

	private void init() {
		ca.init(properties);
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
		for (Address a : ca.summary)
			timer.schedule(new SendSummaryStatisticsTask(a), 0, a.delay * 1000);
		for (Address a : ca.detailed)
			timer.schedule(new SendDetailedStatisticsTask(a), 0, a.delay * 1000);
	}

	public void addConnectionAttempt() {
		connectionAttempts.getAndIncrement();
	}

	public void openEvent(UUID connectionId, FileStatistics fs) {
		// Note that status may be null - only available if client requested it
		System.out.println(">>>Opened " + connectionId + "\n" + fs.toString());
		fs.state |= 0x0011; // set first and second bit
	}

	public void closeEvent(UUID connectionId, int fh) {
		System.out.println(">>>Closed " + connectionId + "\n" + fmap.get(fh).toString());
		// if detailed monitoring is ON collector will remove it from map
		if (ca.reportDetailed == false)
			fmap.remove(fh);
		else
			fmap.get(fh).state |= 0x0004; // set third bit
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

		SendSummaryStatisticsTask(Address a) {
			this.a = a;
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

		// private String info;
		SendDetailedStatisticsTask(Address a) {
			this.a = a;
			// info = "<stats id=\"info\"><host>xxx." + sitename +
			// "</host><port>" + a.port + "</port><name>anon</name></stats>";
		}

		public void run() {
			sendFstream();
			// long tod = System.currentTimeMillis() / 1000L - 60;
			// String sgen = "<stats id=\"sgen\"><as>1</as><et>60000</et><toe>"
			// + (tod + 60) + "</toe></stats>";
			// String link =
			// "<stats id=\"link\"><num>1</num><maxn>1</maxn><tot>20</tot><in>"
			// + totBytesWriten.toString() + "</in><out>" +
			// totBytesRead.toString()
			// +
			// "</out><ctime>0</ctime><tmo>0</tmo><stall>0</stall><sfps>0</sfps></stats>";
			// String xmlmessage = "<statistics tod=\"" + tod +
			// "\" ver=\"v1.9.12.21\" tos=\"" + tos + "\" src=\"xxx." + sitename
			// + "\" pgm=\"xrootd\" ins=\"anon\" pid=\"" + pid + "\">" + info +
			// sgen + link + "</statistics>";
			// // System.out.println(xmlmessage);
		}

		private void sendFstream() {
			System.out.println("sending detailed stream");
			DatagramChannel c = (DatagramChannel) b.bind(new InetSocketAddress(0));
			short plen = (short) (24+32*fmap.size()); // this is length of 3 mandatory headers
			ChannelBuffer db = dynamicBuffer(plen);

			// main header
			db.writeByte((byte) 102); // 'f'
			db.writeByte((byte) fseq);
			db.writeShort(plen); // will be replaced later
			db.writeInt(tos);

			// first timing header
			db.writeByte((byte) 2); // 2 - means isTime
			db.writeByte((byte) 0); // no meaning here
			db.writeShort(8); // size of this header
			db.writeInt(tos); // unix time

			Iterator<Entry<Integer, FileStatistics>> it = fmap.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Integer, FileStatistics> ent = (Map.Entry<Integer, FileStatistics>) it.next();
				FileStatistics fs = (FileStatistics) ent.getValue();

				if ((fs.state & 0x0001) > 0) {
					// add fileopen structure
					// header
					db.writeByte((byte) 1); // 1 - means isOpen
					db.writeByte((byte) 0x01); // the lfn is present - 0x02 is
												// R/W
					int len=21+fs.filename.length();
					plen+=21+fs.filename.length();
					db.writeShort(len); // size
					db.writeInt(tos); // unix time

					db.writeLong(fs.filesize); // filesize at open.
					if (true) { // check if Filenames should be reported.
						db.writeInt(0);// user_dictid
						db.writeBytes(fs.filename.getBytes());// maybe should be
																// forced to
																// "US-ASCII"?
						db.writeByte(0x0); // to make this "C" string. end with
											// null character.
					}

					// reset the first bit
					fs.state &= 0xFFFE;
				}

				db.writeByte((byte) 3); // fileIO report
				db.writeByte((byte) 0); // no meaning
				db.writeShort(32); // 3*longlong + this header itself
				db.writeInt(ent.getKey()); // fileid
				db.writeLong(fs.bytesRead.get());
				db.writeLong(fs.bytesVectorRead.get());
				db.writeLong(fs.bytesWritten.get());

				if ((fs.state & 0x0004) > 0) { // add fileclose structure
					// header
					db.writeByte((byte) 0); // 0 - means isClose
					db.writeByte((byte) 0x00); // 0- basic 2- MonStatXFR + MonStatOPS 1-
												// close due to disconnect 4-
												// XFR + OPS + MonStatSDV
					db.writeShort(8 + 24); // size of this header
					plen+=32;
					db.writeInt(tos); // unix time

					//
					db.writeLong(fs.bytesRead.get());
					db.writeLong(fs.bytesVectorRead.get());
					db.writeLong(fs.bytesWritten.get());

					// if (false){
					// here if ops or SDV was required
					// }

					// remove it
					it.remove();
				}

			}

			// last timing header
			db.writeByte((byte) 2);
			db.writeByte((byte) 0);
			db.writeShort(8);
			db.writeInt(tos);

			db.setShort(2,plen);
			
			c.write(db, new InetSocketAddress(a.address, a.port));
		}

	}

}
