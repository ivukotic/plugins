package edu.uchicago.monitor;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;

import static org.jboss.netty.buffer.ChannelBuffers.*;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Collector {

	final Logger logger = LoggerFactory.getLogger(Collector.class);

	private String servername;
	private Properties properties;

	private int tos; // time of server start
	private int tosc; // time of start of info collection
	private int toec; // time of end of info collection

	// 0- basic
	// 1- close due to disconnect
	// 2- MonStatXFR + MonStatOPS
	// 6- XFR + OPS + MonStatSDV
	private int closedetails = 0;

	private int pid;
	private byte fseq = 0;

	private DatagramChannelFactory f;
	private ConnectionlessBootstrap b;
	private ConnectionlessBootstrap b1;

	public final Map<Integer, FileStatistics> fmap = new ConcurrentHashMap<Integer, FileStatistics>();

	private AtomicInteger connectionAttempts = new AtomicInteger();
	private AtomicInteger currentConnections = new AtomicInteger();
	private AtomicInteger successfulConnections = new AtomicInteger();
	public AtomicLong totBytesRead = new AtomicLong();
	public AtomicLong totBytesWriten = new AtomicLong();
	private CollectorAddresses ca = new CollectorAddresses();

	// Timer timer = new Timer();

	Collector(Properties properties) {
		this.properties = properties;
		init();
	}

	private void init() {

		String pServerName = properties.getProperty("servername"); // if not
																	// defined
																	// will try
																	// to get it
																	// using
																	// getHostName.
		if (pServerName != null) {
			servername = pServerName;
		} else {
			try {
				servername = java.net.InetAddress.getLocalHost().getHostName();
				logger.info("server name: " + servername);
			} catch (UnknownHostException e) {
				logger.error("Could not get server's hostname. Will set it to xxx.abc.def");
				servername = "xxx.abc.def";
				e.printStackTrace();
			}
		}

		ca.init(properties);
		logger.info(ca.toString());

		tos = (int) (System.currentTimeMillis() / 1000L);
		tosc = tos;

		try {
			pid = Integer.parseInt(new File("/proc/self").getCanonicalFile().getName());
		} catch (Exception e) {
			logger.warn("could not get PID from /proc/self. Setting it to 123456.");
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
		// b.setOption("receiveBufferSizePredictorFactory", new
		// FixedReceiveBufferSizePredictorFactory(1024));
		// b.setOption("sendBufferSize",32000);
		b.setOption("localAddress", new InetSocketAddress(9934));
		b.setOption("broadcast", "true");
		b.setOption("connectTimeoutMillis", 10000);

		b1 = new ConnectionlessBootstrap(f);

		b1.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(new StringEncoder(CharsetUtil.ISO_8859_1), new StringDecoder(CharsetUtil.ISO_8859_1),
						new SimpleChannelUpstreamHandler());
			}
		});
		b1.setOption("localAddress", new InetSocketAddress(9935));
		b1.setOption("broadcast", "true");
		b1.setOption("connectTimeoutMillis", 10000);

		for (Address a : ca.summary) {
			Timer timer = new Timer();
			timer.schedule(new SendSummaryStatisticsTask(a), 0, a.delay * 1000);
		}
		for (Address a : ca.detailed) {
			Timer timer = new Timer();
			timer.schedule(new SendDetailedStatisticsTask(a), 0, a.delay * 1000);
		}
	}

	public void addConnectionAttempt() {
		connectionAttempts.getAndIncrement();
	}

	public void openEvent(int connectionId, FileStatistics fs) {
		// Note that status may be null - only available if client requested it
		logger.info(">>>Opened " + connectionId + "\n" + fs.toString());
		fs.state |= 0x0011; // set first and second bit
	}

	public void closeEvent(int connectionId, int fh) {
		logger.info(">>>Closed " + connectionId + "\n" + fmap.get(fh).toString());
		// if detailed monitoring is ON collector will remove it from map
		if (ca.reportDetailed == false)
			fmap.remove(fh);
		else
			fmap.get(fh).state |= 0x0004; // set third bit
	}

	public void connectedEvent(int connectionId, SocketAddress remoteAddress) {
		currentConnections.getAndIncrement();
		logger.info(">>>Connected " + connectionId + " " + remoteAddress);
	}

	public void disconnectedEvent(int connectionId, long duration) {
		currentConnections.getAndDecrement();
		successfulConnections.getAndIncrement();
		logger.info(">>>Disconnected " + connectionId + " " + duration + "ms");
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

	public void SendMapMessage(Integer dictid, String content) {
		logger.info("sending map message: " + content);
		for (Address a : ca.detailed) {
			MapMessagesSender mms = new MapMessagesSender(a, dictid, content);
			mms.start();
		}
	}

	// public void sendMyMessage(Integer dictid, String content) {
	// for (Address a : ca.detailed) {
	// InetSocketAddress destination = new InetSocketAddress(a.address, a.port);
	// try {
	// DatagramChannel c = (DatagramChannel) b.bind();
	// String authinfo =
	// "\n&p=SSL&n=ivukotic&h=hostname&o=UofC&r=Production&g=higgs&m=fuck";
	// content += authinfo;
	//
	// logger.info("sending map message: " + content);
	// short plen = (short) (12 + content.length());
	// ChannelBuffer db = dynamicBuffer(plen);
	//
	// // main header
	// db.writeByte((byte) 117); // 'u'
	// db.writeByte((byte) fseq);
	// db.writeShort(plen);
	// db.writeInt(tos);
	// db.writeInt(dictid); // this is dictID
	// db.writeBytes(content.getBytes());
	// ChannelFuture f = c.write(db, destination);
	//
	// f.addListener(new ChannelFutureListener() {
	// public void operationComplete(ChannelFuture future) throws Exception {
	// if (future.isSuccess()) {
	// logger.debug("OK sent. ");
	// } else {
	// logger.error("NOT sent. ");
	// }
	// }
	// });
	//
	// ChannelFuture f1 = c.close();
	// f1.addListener(new ChannelFutureListener() {
	// public void operationComplete(ChannelFuture future) throws Exception {
	// if (future.isSuccess()) {
	// logger.debug("Connection CLOSED. ");
	// } else {
	// logger.error("Connection NOT CLOSED. ");
	// }
	// }
	// });
	//
	// } catch (Exception e) {
	// logger.error("unrecognized exception: " + e.getMessage());
	// }
	// }
	// }

	private class MapMessagesSender extends Thread {
		private InetSocketAddress destination;
		private Integer dictid;
		private String content;

		MapMessagesSender(Address a, Integer dictid, String content) {
			destination = new InetSocketAddress(a.address, a.port);
			this.dictid = dictid;
			this.content = content;
		}

		public void run() {
			try {
				fseq += 1;
				DatagramChannel c = (DatagramChannel) b.bind();
				String authinfo = "\n&p=SSL&n=ivukotic&h=hostname&o=UofC&r=Production&g=higgs&m=whatever";
				content += authinfo;
				short plen = (short) (12 + content.length());
				ChannelBuffer db = dynamicBuffer(plen);

				// main header
				db.writeByte((byte) 117); // 'u'
				db.writeByte((byte) fseq);
				db.writeShort(plen);
				db.writeInt(tos);
				db.writeInt(dictid); // this is dictID
				db.writeBytes(content.getBytes());
				ChannelFuture f = c.write(db, destination);

				f.awaitUninterruptibly();
				if (!f.isSuccess()) {
					f.getCause().printStackTrace();
				} else {
					logger.info("MAP MSG sent ok");
				}
				c.close();

				// f.addListener(new ChannelFutureListener() {
				// public void operationComplete(ChannelFuture future) throws
				// Exception {
				// if (future.isSuccess()) {
				// logger.debug("OK sent. ");
				// } else {
				// logger.error("NOT sent. ");
				// }
				// }
				// });
				//
				// ChannelFuture f1=c.close();
				// f1.addListener(new ChannelFutureListener() {
				// public void operationComplete(ChannelFuture future) throws
				// Exception {
				// if (future.isSuccess()) {
				// logger.debug("Connection CLOSED. ");
				// } else {
				// logger.error("Connection NOT CLOSED. ");
				// }
				// }
				// });

			} catch (Exception e) {
				logger.error("unrecognized exception: " + e.getMessage());
			}
		}

	}

	private class SendSummaryStatisticsTask extends TimerTask {
		private String info;
		private Address a;

		SendSummaryStatisticsTask(Address a) {
			this.a = a;
			info = "<stats id=\"info\"><host>" + servername + "</host><port>" + a.port + "</port><name>anon</name></stats>";
		}

		public void run() {

			logger.info("sending summary stream");

			long tod = System.currentTimeMillis() / 1000L - 60;
			String sgen = "<stats id=\"sgen\"><as>1</as><et>60000</et><toe>" + (tod + 60) + "</toe></stats>";
			String link = "<stats id=\"link\"><num>1</num><maxn>1</maxn><tot>20</tot><in>" + totBytesWriten.toString() + "</in><out>" + totBytesRead.toString()
					+ "</out><ctime>0</ctime><tmo>0</tmo><stall>0</stall><sfps>0</sfps></stats>";
			String xmlmessage = "<statistics tod=\"" + tod + "\" ver=\"v1.9.12.21\" tos=\"" + tos + "\" src=\"" + servername
					+ "\" pgm=\"xrootd\" ins=\"anon\" pid=\"" + pid + "\">" + info + sgen + link + "</statistics>";
			// logger.info(xmlmessage);

			DatagramChannel c = (DatagramChannel) b1.bind();

			ChannelFuture f = c.write(xmlmessage, new InetSocketAddress(a.address, a.port));

			f.awaitUninterruptibly();
			if (!f.isSuccess()) {
				f.getCause().printStackTrace();
			}
			// else { logger.info("sent ok"); }
			c.close();
		}
	}

	private class SendDetailedStatisticsTask extends TimerTask {
		private InetSocketAddress destination;

		// private String info;
		SendDetailedStatisticsTask(Address a) {
			destination = new InetSocketAddress(a.address, a.port);
		}

		public void run() {
			sendFstream();
		}

		private void sendFstream() {
			logger.debug("sending detailed stream");
			fseq += 1;
			DatagramChannel c = (DatagramChannel) b.bind();

			logger.debug("fmap size: " + fmap.size());
			short plen = (short) (24); // this is length of 2 mandatory headers
			ChannelBuffer db = dynamicBuffer(plen);

			// main header
			db.writeByte((byte) 102); // 'f'
			db.writeByte((byte) fseq);
			db.writeShort(plen); // will be replaced later
			db.writeInt(tos);

			// first timing header
			db.writeByte((byte) 2); // 2 - means isTime
			db.writeByte((byte) 0); // no meaning here
			db.writeShort(16); // size of this header
			db.writeShort(0); // since this is TOD - this is nRec[0]
			db.writeShort(0); // this gives total number of "subpackages". will
								// be overwritten bellow
			db.writeInt(tosc); // unix time - this should be start of package
								// collection time
			toec = (int) (System.currentTimeMillis() / 1000L);
			db.writeInt(toec); // unix time - this should be time of sending.
			tosc = toec;
			int subpackets = 0;
			Iterator<Entry<Integer, FileStatistics>> it = fmap.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Integer, FileStatistics> ent = (Map.Entry<Integer, FileStatistics>) it.next();
				FileStatistics fs = (FileStatistics) ent.getValue();
				Integer dictID = ent.getKey();// user_dictid - actually
												// connectionID. should be
												// changed later for proof.

				if (dictID < 0)
					continue; // file has been requested but not yet really
								// opened.

				if ((fs.state & 0x0001) > 0) { // file OPEN structure
					// header
					db.writeByte((byte) 1); // 1 - means isOpen
					db.writeByte((byte) 0x01); // the lfn is present - 0x02 is
												// R/W
					int len = 21 + fs.filename.length();
					plen += len;
					db.writeShort(len); // size
					db.writeInt(fs.fileId); // replace with dictid of the file

					db.writeLong(fs.filesize); // filesize at open.
					if (true) { // check if Filenames should be reported.
						db.writeInt(dictID);// user_dictid
						db.writeBytes(fs.filename.getBytes());// maybe should be
																// forced to
																// "US-ASCII"?
						db.writeByte(0x0); // to make this "C" string. end with
											// null character.
					}

					// reset the first bit
					fs.state &= 0xFFFE;
					subpackets += 1;
				}

				db.writeByte((byte) 3); // fileIO report
				db.writeByte((byte) 0); // no meaning
				db.writeShort(32); // 3*longlong + this header itself
				db.writeInt(fs.fileId); // replace with dictid of the file
				db.writeLong(fs.bytesRead.get());
				db.writeLong(fs.bytesVectorRead.get());
				db.writeLong(fs.bytesWritten.get());
				plen+=32;
				subpackets += 1;

				if ((fs.state & 0x0004) > 0) { // add fileclose structure
					// header
					db.writeByte((byte) 0); // 0 - means isClose

					db.writeByte((byte) closedetails);

					int packlength = 8;
					switch (closedetails) {
					case 0:
						packlength += 24;
						break;
					case 1:
						// do nothing
						break;
					case 2:
						packlength += 24 + 48;
						break;
					case 6:
						packlength += 24 + 48 + 64;
						break;
					}

					db.writeShort(packlength); // size of this header
					db.writeInt(fs.fileId); // replace with dictid of the file

					if (closedetails != 1) {
						db.writeLong(fs.bytesRead.get());
						db.writeLong(fs.bytesVectorRead.get());
						db.writeLong(fs.bytesWritten.get());
					} 
					
					if ( closedetails >1 ) { // OPS
						db.writeInt(111); // reads
						db.writeInt(112); // readVs
						db.writeInt(113); // writes
						db.writeShort(11); // shortest readv segments
						db.writeShort(12); // longest readv segments
						db.writeLong(123456); // number of readv segments
						db.writeInt(111000); // rdMin
						db.writeInt(112000); // rdMax
						db.writeInt(113000); // rvMin
						db.writeInt(111001); // rvMax
						db.writeInt(112002); // wrMin
						db.writeInt(113003); // wrMax
					}

					if (closedetails == 6) { //SSQ
						db.writeLong(123456); // number of readv segments
						db.writeDouble(123.123);
						db.writeLong(123456); // number of readv segments
						db.writeDouble(123.123);
						db.writeLong(123456); // number of readv segments
						db.writeDouble(123.123);
						db.writeLong(123456); // number of readv segments
						db.writeDouble(123.123);
					}

					// remove it
					it.remove();
					subpackets += 1;
					plen += packlength;
				}

			}

			logger.debug("message length: " + plen+"\t buffer length:"+db.writableBytes() );
			db.setShort(2, plen);
			db.setShort(14, subpackets);

			ChannelFuture f = c.write(db, destination);
			f.awaitUninterruptibly();
			if (!f.isSuccess()) {
				f.getCause().printStackTrace();
			}
			// else { logger.info("sent ok");}
			f = c.close();
		}

	}

}
