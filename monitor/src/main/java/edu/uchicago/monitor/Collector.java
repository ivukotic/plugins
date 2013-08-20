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
import org.jboss.netty.channel.ChannelFutureListener;
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

	final static Logger logger = LoggerFactory.getLogger(Collector.class);

	private String servername;
	private String sitename;
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
	private byte fseq = 0; // sequence for f-stream
	private byte pseq = 0; // sequence for all other streams.

	private DatagramChannelFactory f;
	private ConnectionlessBootstrap cbsSummary;
	private ConnectionlessBootstrap cbsDetailed;
	private ConnectionlessBootstrap cbsMMSender;
	private DatagramChannel dcMM;

	public final Map<Integer, FileStatistics> fmap = new ConcurrentHashMap<Integer, FileStatistics>();
	public final Map<Integer, ConnectionInfo> cmap = new ConcurrentHashMap<Integer, ConnectionInfo>();

	private AtomicInteger connectionAttempts = new AtomicInteger();
	private AtomicInteger currentConnections = new AtomicInteger();
	private AtomicInteger successfulConnections = new AtomicInteger();
	private AtomicInteger maxConnections = new AtomicInteger();
	public AtomicLong totBytesRead = new AtomicLong();
	public AtomicLong totBytesWriten = new AtomicLong();
	private CollectorAddresses ca = new CollectorAddresses();

	Collector(Properties properties) {
		this.properties = properties;
		init();
	}

	private void init() {

		// if not defined will try to get it using getHostName.
		String pServerName = properties.getProperty("servername");
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

		String pSitename = properties.getProperty("site");
		if (pSitename != null)
			sitename = pSitename;
		else
			sitename = "anon";

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

		// this one is for sending summary data
		cbsSummary = new ConnectionlessBootstrap(f);
		cbsSummary.setOption("localAddress", new InetSocketAddress(0));
		cbsSummary.setOption("broadcast", "true");
		cbsSummary.setOption("connectTimeoutMillis", 10000);

		cbsSummary.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(new StringEncoder(CharsetUtil.ISO_8859_1), new StringDecoder(CharsetUtil.ISO_8859_1),
						new SimpleChannelUpstreamHandler());
			}
		});

		for (Address a : ca.summary) {
			Timer timer = new Timer();
			timer.schedule(new SendSummaryStatisticsTask(a), 0, a.delay * 1000);
			cbsSummary.setOption("localAddress", new InetSocketAddress(a.outboundport));
			logger.info("Setting summary monitoring local port (outbound) to: " + a.outboundport);
		}

		// this one are detailed

		cbsDetailed = new ConnectionlessBootstrap(f);
		cbsDetailed.setOption("localAddress", new InetSocketAddress(0));
		cbsDetailed.setOption("broadcast", "true");
		cbsDetailed.setOption("connectTimeoutMillis", 10000);

		cbsDetailed.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(
				// new StringEncoder(CharsetUtil.ISO_8859_1),
				// new StringDecoder(CharsetUtil.ISO_8859_1),
						new SimpleChannelUpstreamHandler());
			}
		});

		for (Address a : ca.detailed) {
			Timer timer = new Timer();
			timer.schedule(new SendDetailedStatisticsTask(a), 0, a.delay * 1000);
			cbsDetailed.setOption("localAddress", new InetSocketAddress(a.outboundport));
			logger.info("Setting detailed monitoring local port (outbound) to: " + a.outboundport);
		}

		// this one is for mapping messages

		cbsMMSender = new ConnectionlessBootstrap(f);
		cbsMMSender.setOption("localAddress", new InetSocketAddress(0));
		cbsMMSender.setOption("broadcast", "true");
		cbsMMSender.setOption("connectTimeoutMillis", 10000);

		cbsMMSender.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(new StringEncoder(CharsetUtil.ISO_8859_1), new StringDecoder(CharsetUtil.ISO_8859_1),
						new SimpleChannelUpstreamHandler());
			}
		});

		dcMM = (DatagramChannel) cbsMMSender.bind();

		Timer timer = new Timer();
		timer.schedule(new currentStatus(), 0, 5 * 60 * 1000);

	}

	public void addConnectionAttempt() {
		connectionAttempts.getAndIncrement();
	}

	public void openFileEvent(int connectionId, FileStatistics fs) {
		// Note that status may be null - only available if client requested it
		logger.debug(">>>Opened " + connectionId + "\n" + fs.toString());
		fs.state |= 0x0011; // set first and second bit
		SendMapMessage((byte) 100, connectionId, fs.filename);
	}

	public void closeFileEvent(int connectionId, int fh) {
		logger.debug(">>>Closed " + connectionId + "  file handle: " + fh);
		if (fmap.get(fh) == null) {
			logger.warn("File handle missing from the fmap. Should not happen except in case of recent restart. ");
			return;
		}
		// if detailed monitoring is ON collector will remove it from map
		if (ca.reportDetailed == false)
			fmap.remove(fh);
		else
			fmap.get(fh).state |= 0x0004; // set third bit
	}

	public void connectedEvent(int connectionId) {
		if (currentConnections.getAndIncrement() > maxConnections.get())
			maxConnections.set(currentConnections.get());
		cmap.put(connectionId, new ConnectionInfo(connectionId));
	}

	public void disconnectedEvent(int connectionId) {
		currentConnections.getAndDecrement();
		successfulConnections.getAndIncrement();
		try {
			logger.info("DISCONNECTED " + connectionId);
			cmap.get(connectionId).ConnectionClose();
			if (ca.reportDetailed == false) 
				cmap.remove(connectionId);
		} catch (Exception ex) {
			logger.warn("connection closed before being opened.");
			logger.error(ex.getMessage());
		}

	}

	public void loggedEvent(int connectionId, SocketAddress remoteAddress) {

		ConnectionInfo ci = cmap.get(connectionId);
		if (ci != null) {
			ci.logUserResponse(((InetSocketAddress) remoteAddress).getHostName(), ((InetSocketAddress) remoteAddress).getPort());
			SendMapMessage((byte) 117, connectionId, ci.ui.getInfo());
		} else {
			logger.error("Could not map connection " + connectionId + "to user.");
		}
	}

	@Override
	public String toString() {
		String res = new String();
		res += "SUMMARY ----------------------------------\n";
		res += "Connection Attempts:     " + connectionAttempts.get() + "\n";
		res += "Current Connections:     " + currentConnections.toString() + "\n";
		res += "Successful Connections:  " + successfulConnections.toString() + "\n";
		res += "Bytes Read:              " + totBytesRead.toString() + "\n";
		res += "Bytes Written:           " + totBytesWriten.toString() + "\n";
		res += "SUMMARY ----------------------------------\n";
		return res;
	}

	// type - 117:u 100:d 105:i
	public void SendMapMessage(byte mtype, Integer dictid, String content) {
		logger.info("sending map message: " + dictid.toString() + " -> " + content);
		for (Address a : ca.detailed) {
			MapMessagesSender mms = new MapMessagesSender(a, mtype, dictid, content);
			mms.start();
		}
	}

	private class MapMessagesSender extends Thread {
		private InetSocketAddress destination;
		private Integer dictid;
		private String content;
		private byte mtype;

		MapMessagesSender(Address a, byte mtype, Integer dictid, String content) {
			destination = new InetSocketAddress(a.address, a.port);
			this.dictid = dictid;
			this.content = content;
			this.mtype = mtype;
		}

		public void run() {
			logger.debug("Sending Map Message.");
			try {
				pseq += 1;

				short plen = (short) (12 + content.length());
				ChannelBuffer db = dynamicBuffer(plen);

				// main header
				db.writeByte(mtype);
				db.writeByte((byte) pseq);
				db.writeShort(plen);
				db.writeInt(tos);
				db.writeInt(dictid); // this is dictID
				db.writeBytes(content.getBytes());
				ChannelFuture f = dcMM.write(db, destination);

				f.addListener(new ChannelFutureListener() {
					public void operationComplete(ChannelFuture future) {
						if (future.isSuccess())
							logger.debug("Map Message sent! Type: " + mtype);
						else {
							logger.error("Map Message IO completed. did not send info:" + future.getCause());
						}
					}
				});

			} catch (Exception e) {
				logger.error("unrecognized exception: " + e.getMessage());
			}
		}

	}

	private class SendSummaryStatisticsTask extends TimerTask {
		private InetSocketAddress destination;
		private String info;
		private String STATISTICSstart, STATISTICSend;
		private String SGENstart, SGENend;
		private String LINKstart, LINKend;
		private DatagramChannel c;
		private long lastUpdate;

		SendSummaryStatisticsTask(Address a) {
			c = (DatagramChannel) cbsSummary.bind();
			destination = new InetSocketAddress(a.address, a.port);
			STATISTICSstart = "<statistics ver=\"v1.9.12.21\" pgm=\"xrootd\" ins=\"anon\"";
			STATISTICSstart += " tos=\"" + tos + "\"";
			STATISTICSstart += " src=\"" + servername + ":" + c.getLocalAddress().getPort() + "\"";
			STATISTICSstart += " host=\"" + servername + "\"";
			STATISTICSstart += " site=\"" + sitename + "\"";
			STATISTICSstart += " pid=\"" + pid + "\"";
			STATISTICSend = "</statistics>";

			// needs instance name and proper port - how to get it from dCache?
			info = "<stats id=\"info\"><host>" + servername + "</host><port>1096</port><name>anon</name></stats>";

			SGENstart = "<stats id=\"sgen\"><as>1</as><et>" + a.delay + "</et>";
			SGENend = "</stats>";

			// these are not implemented yet: cumulative connection seconds,
			// timeouts, partial received files, partialSendFiles
			LINKstart = "<stats id=\"link\">";
			LINKend = "<ctime>0</ctime><tmo>0</tmo><stall>0</stall><sfps>0</sfps></stats>";

			lastUpdate = System.currentTimeMillis() / 1000L;
		}

		public void run() {
			try {
				logger.debug("sending summary stream");

				long curTime = System.currentTimeMillis() / 1000L;
				String sgen = SGENstart + "<toe>" + curTime + "</toe>" + SGENend;

				String link = LINKstart;
				link += "<num>" + currentConnections.toString() + "</num>";
				link += "<maxn>" + maxConnections.toString() + "</maxn>";
				link += "<tot>" + connectionAttempts.toString() + "</tot>";
				link += "<in>" + totBytesWriten.toString() + "</in>";
				link += "<out>" + totBytesRead.toString() + "</out>";
				link += LINKend;

				String xmlmessage = STATISTICSstart + " tod=\"" + lastUpdate + "\">" + sgen + info + link + STATISTICSend;

				logger.debug(xmlmessage);

				lastUpdate = curTime;

				ChannelFuture f = c.write(xmlmessage, destination);
				f.addListener(new ChannelFutureListener() {
					public void operationComplete(ChannelFuture future) {
						if (future.isSuccess())
							logger.debug("summary stream IO completed. success!");
						else {
							logger.error("summary stream IO completed. did not send info:" + future.getCause());
						}
					}
				});

			} catch (Exception e) {
				logger.error("unrecognized exception in sending summary stream: " + e.getMessage());
			}
		}
	}

	private class SendDetailedStatisticsTask extends TimerTask {
		private InetSocketAddress destination;
		private DatagramChannel c;

		SendDetailedStatisticsTask(Address a) {
			destination = new InetSocketAddress(a.address, a.port);
			c = (DatagramChannel) cbsDetailed.bind();
		}

		public void run() {
			sendFstream();
		}

		private void sendFstream() {
			logger.debug("sending detailed stream");
			try {
				fseq += 1;

				logger.debug("fmap size: " + fmap.size());
				short plen = (short) (24); // this is length of 2 mandatory
											// headers
				ChannelBuffer db = dynamicBuffer(plen);

				// main header - XrdXrootdMonHeader - 8 bytes
				db.writeByte((byte) 102); // 'f'
				db.writeByte((byte) fseq);
				db.writeShort(plen); // will be replaced later
				db.writeInt(tos); // time of server start

				// first timing header - XrdXrootdMonFileTOD - 16 bytes
				db.writeByte((byte) 2); // 2 - means isTime
				db.writeByte((byte) 0); // no meaning here
				db.writeShort(16); // size of this header
				// first short nRec[0] should give number of isXfr records
				// second short nRec[1] should give total number of records
				// will be overwritten below.
				db.writeShort(0);
				db.writeShort(0);
				// unix time - this should be start of package collection time
				db.writeInt(tosc);
				toec = (int) (System.currentTimeMillis() / 1000L);
				// unix time - this should be time of sending.
				db.writeInt(toec);

				tosc = toec;
				int subpackets = 0;
				int xfrpackets = 0;

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
						db.writeByte((byte) 0x01); // the lfn is present - 0x02
													// is
													// R/W
						int len = 21 + fs.filename.length();
						plen += len;
						db.writeShort(len); // size
						db.writeInt(fs.fileId); // replace with dictid of the
												// file

						db.writeLong(fs.filesize); // filesize at open.
						if (true) { // check if Filenames should be reported.
							db.writeInt(dictID);// user_dictid
							db.writeBytes(fs.filename.getBytes());// maybe
																	// should be
																	// forced to
																	// "US-ASCII"?
							db.writeByte(0x0); // to make this "C" string. end
												// with
												// null character.
						}

						// reset the first bit
						fs.state &= 0xFFFE;
						subpackets += 1;
					}

					db.writeByte((byte) 3); // 3 means isXfr
					db.writeByte((byte) 0); // no meaning
					db.writeShort(32); // 3*longlong + this header itself
					db.writeInt(fs.fileId); // replace with dictid of the file
					db.writeLong(fs.bytesRead.get());
					db.writeLong(fs.bytesVectorRead.get());
					db.writeLong(fs.bytesWritten.get());
					plen += 32;
					xfrpackets += 1;
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
						db.writeInt(fs.fileId); // replace with dictid of the
												// file

						if (closedetails != 1) {
							db.writeLong(fs.bytesRead.get());
							db.writeLong(fs.bytesVectorRead.get());
							db.writeLong(fs.bytesWritten.get());
						}

						if (closedetails > 1) { // OPS
							db.writeInt(fs.reads.get()); // reads
							db.writeInt(fs.vectorReads.get()); // readVs
							db.writeInt(fs.writes.get()); // writes
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

						if (closedetails == 6) { // SSQ
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

				// disconnects
				Iterator<Entry<Integer, ConnectionInfo>> iter = cmap.entrySet().iterator();
				while (iter.hasNext()) {
					Map.Entry<Integer, ConnectionInfo> ent = (Map.Entry<Integer, ConnectionInfo>) iter.next();
					if (ent.getValue().disconnected == true) {
						db.writeByte((byte) 4); // 4 - means isDisc
						db.writeByte((byte) 0); // no meaning
						db.writeShort(8); // size
						db.writeInt(ent.getKey()); // userID
						subpackets += 1;
						plen += 8;
						iter.remove();
					}
				}

				logger.debug("message length: " + plen + "\t buffer length:" + db.writableBytes());
				db.setShort(2, plen);
				db.setShort(12, xfrpackets);
				db.setShort(14, subpackets);

				ChannelFuture f = c.write(db, destination);
				f.addListener(new ChannelFutureListener() {
					public void operationComplete(ChannelFuture future) {
						if (future.isSuccess())
							logger.debug("detailed stream IO completed. success!");
						else {
							logger.error("detailed stream IO completed. did not send info:" + future.getCause());
						}
						// future.getChannel().close();
					}
				});

			} catch (Exception e) {
				logger.error("unrecognized exception in sending f-stream: " + e.getMessage());
			}
		}

	}

	private class currentStatus extends TimerTask {
		public void run() {
			String res = new String();
			res += "Report ----------------------------------------------------\n";
			res += "Connection Attempts:     " + connectionAttempts.get() + "\n";
			res += "Current Connections:     " + currentConnections.toString() + "\n";
			res += "Connections established: " + successfulConnections.toString() + "\n";
			res += "Bytes Read:              " + totBytesRead.toString() + "\n";
			res += "Bytes Written:           " + totBytesWriten.toString() + "\n";
			res += "-----------------------------------------------------------\n";
			res += "Current connections:\n";
			for (Map.Entry<Integer, ConnectionInfo> entry : cmap.entrySet()) {
				res += entry.getKey() + "\t\t" + entry.getValue().toString() + "\n";
			}
			logger.info(res);
			res = "";
			res += "Current openfiles:\n";
			for (Map.Entry<Integer, FileStatistics> entry : fmap.entrySet()) {
				res += entry.getKey() + "\t\t" + entry.getValue().toString() + "\n";
			}
			logger.info(res);
		}
	}

}
