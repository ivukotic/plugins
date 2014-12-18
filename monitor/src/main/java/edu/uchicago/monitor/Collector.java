package edu.uchicago.monitor;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
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

	private final static Logger logger = LoggerFactory.getLogger(Collector.class);

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
	private final Map<Integer, ConnectionInfo> cmap = new ConcurrentHashMap<Integer, ConnectionInfo>();

	private AtomicInteger connectionAttempts = new AtomicInteger();
	private AtomicInteger successfulConnections = new AtomicInteger();
	private AtomicInteger maxConnections = new AtomicInteger();
	public AtomicLong totBytesRead = new AtomicLong();
	public AtomicLong totBytesWriten = new AtomicLong();
	private CollectorAddresses ca = new CollectorAddresses();

	private final UDPsender sender;
	private final UDPmessage mess;
	private int DetailedLocalSendingPort;
	private Timer tDetailed;
	private float factor;
	private String virtualOrganization;


	public Collector() {
    	logger.debug("Collector constructor!");
		sender = new UDPsender();
		mess = new UDPmessage();
	}
	
	
	public ConnectionInfo getCI(Integer connid) {
		return cmap.get(connid);
	}

	void init(Properties properties) {
		
		if (this.properties != null){
	    	logger.debug("Collector properties were already set...");
	    	return;
		}
		this.properties = properties;
		
		// if not defined will try to get it using getHostName.
		String pServerName = properties.getProperty("servername");
		if (pServerName != null) {
			servername = pServerName;
		} else {
			try {
				servername = java.net.InetAddress.getLocalHost().getHostName();
				logger.info("server name: {}", servername);
			} catch (UnknownHostException e) {
				logger.error("Could not get server's hostname. Will set it to xxx.abc.def");
				servername = "xxx.abc.def";
				e.printStackTrace();
			}
		}
		
		String pVO = properties.getProperty("vo");
		if (pVO != null){
			logger.info("Setting VO to {}", pVO);
			virtualOrganization = pVO;
		}else{
			logger.warn("Could not get VO. Will set it to -unknown-");
			virtualOrganization = "unknown";
		}
		
		String pSitename = properties.getProperty("site");
		if (pSitename != null)
			sitename = pSitename;
		else
			sitename = "anon";

		ca.init(properties);
		logger.info(ca.toString());

		DetailedLocalSendingPort = sender.init(ca, mess);
		sender.start();

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
			logger.info("Setting summary monitoring local port (outbound) to: {}", a.outboundport);
		}

		Timer timer = new Timer();
		// this is used for printing out state and sending "=" stream
		timer.schedule(new currentStatus(), 0, 5 * 60 * 1000);

		factor = 1;
		createReportingThreads();
	}

	// this is rescheduling sending of information in case UDP packets would
	// grow too large
	private void createReportingThreads() {
		if (tDetailed != null) {
			logger.warn("removing detailed reporting timer");
			tDetailed.cancel();
		}
		tDetailed = new Timer();
		if (ca.reportDetailed == true) {
			logger.warn("detailed reporting timer set at {} ms.", ca.detailed.get(0).delay * 1000 * factor);
			tDetailed.schedule(new SendDetailedStatisticsProducer(mess), 2000, (long) (ca.detailed.get(0).delay * 1000 * factor));
		}
	}

	public void addConnectionAttempt() {
		connectionAttempts.getAndIncrement();
	}

	public void closeFileEvent(int connectionId, int fh) {
		logger.debug(">>>Closed {}  file handle:{}", connectionId, fh);
		// if detailed monitoring is ON collector will remove it from map
		if (ca.reportDetailed == false)
			cmap.get(connectionId).removeFile(fh);
	}

	public void connectedEvent(int connectionId) {
		maxConnectionsCheck();
		cmap.put(connectionId, new ConnectionInfo(connectionId, DetailedLocalSendingPort));
	}

	private synchronized void maxConnectionsCheck() {
		if (cmap.size() > maxConnections.get())
			maxConnections.set(cmap.size());
	}

	public void disconnectedEvent(int connectionId) {
		successfulConnections.getAndIncrement();
		try {
			logger.info("DISCONNECTED {} ", connectionId);
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
			SendMapMessage((byte) 117, connectionId, ci.ui.getFullInfo(virtualOrganization));
		} else {
			logger.error("Could not map connection {} to user.", connectionId);
		}
	}

	// type - 117:u 100:d 105:i
	public void SendMapMessage(byte mtype, Integer dictid, String content) {
		logger.debug("sending map message: {} -> {}", dictid, content);
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
			mess.put(db);

		} catch (Exception e) {
			logger.error("unrecognized exception: {} ", e.getMessage());
		}
	}

	private class SendSummaryStatisticsTask extends TimerTask {
		private final InetSocketAddress destination;
		private final String info;
		private final String STATISTICSstart, STATISTICSend;
		private final String SGENstart, SGENend;
		private final String LINKstart, LINKend;
		private final DatagramChannel c;
		private long lastUpdate;

		SendSummaryStatisticsTask(Address a) {
			c = (DatagramChannel) cbsSummary.bind();
			destination = new InetSocketAddress(a.address, a.port);
			STATISTICSstart = "<statistics ver=\"v1.9.12.21\" pgm=\"xrootd\" ins=\"anon\"" +
							  " tos=\"" + tos + "\"" +
							  " src=\"" + servername + ":" + c.getLocalAddress().getPort() + "\"" +
							  " host=\"" + servername + "\"" +
							  " site=\"" + sitename + "\"" +
							  " pid=\"" + pid + "\"";
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

				String link = LINKstart +
							  "<num>" + cmap.size() + "</num>" +
							  "<maxn>" + maxConnections + "</maxn>" +
							  "<tot>" + connectionAttempts + "</tot>" +
							  "<in>" + totBytesWriten + "</in>" +
							  "<out>" + totBytesRead + "</out>" +
							  LINKend;

				String xmlmessage = STATISTICSstart + " tod=\"" + lastUpdate + "\">" + sgen + info + link + STATISTICSend;

				logger.debug(xmlmessage);

				lastUpdate = curTime;

				ChannelFuture f = c.write(xmlmessage, destination);
				f.addListener(new ChannelFutureListener() {
					public void operationComplete(ChannelFuture future) throws Exception {
						if (future.isSuccess())
							logger.debug("summary stream IO completed. success!");
						else {
							logger.error("summary stream IO completed. did not send info: {}", future.getCause());
						}
					}

				});

			} catch (Exception e) {
				logger.error("unrecognized exception in sending summary stream: {}", e.getMessage());
			}
		}
	}

	private class currentStatus extends TimerTask {
		public void run() {

			// this is "=" stream. Collector neglects all the info in it. If 5
			// of these are not received it knows server is down
			// so it cleans up all the connections.
			SendMapMessage((byte) 61, 0, "user.pid:sid@host\n&pgm=dCacheXrootdDoor&ver=5.0.0&inst=anon&port=0&site=" + sitename);
			if (logger.isInfoEnabled()) {
				StringBuilder res = new StringBuilder();
				res.append("Report ----------------------------------------------------\n");
				res.append("Connection Attempts:     ").append(connectionAttempts.get()).append("\n");
				res.append("Current Connections:     ").append(cmap.size()).append("\n");
				res.append("Connections established: ").append(successfulConnections.toString()).append("\n");
				res.append("Bytes Read:              ").append(totBytesRead.toString()).append("\n");
				res.append("Bytes Written:           ").append(totBytesWriten.toString()).append("\n");
				res.append("-----------------------------------------------------------\n");
				res.append("Current connections:\n");
				for (Map.Entry<Integer, ConnectionInfo> entry : cmap.entrySet()) {
					res.append(entry.getKey()).append("\t\t").append(entry.getValue().toString()).append("\n");
				}
				logger.info(res.toString());
			}
		}
	}

	private class SendDetailedStatisticsProducer extends TimerTask {
		private final UDPmessage mess;

		SendDetailedStatisticsProducer(UDPmessage m) {
			this.mess = m;
		}

		public void run() {
			sendFstream();
		}

		private void sendFstream() {
			logger.debug("sending detailed stream");
			try {
				fseq += 1;

				int plen = (int) (24); // this is length of 2 mandatory
											// headers
				ChannelBuffer db = dynamicBuffer(plen);

				// main header - XrdXrootdMonHeader - 8 bytes
				db.writeByte((byte) 102); // 'f'
				db.writeByte((byte) fseq);
				db.writeShort((short) plen); // will be replaced later
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

				for (Map.Entry<Integer, ConnectionInfo> cent : cmap.entrySet()) {

					for (Entry<Integer, FileStatistics> fent : cent.getValue().allFiles.entrySet()) {

						FileStatistics fs = (FileStatistics) fent.getValue();
						Integer dictID = cent.getKey();

						// add OPEN structure
						if ((fs.state & 0x0001) > 0) {
							// header
							db.writeByte((byte) 1); // 1 - means isOpen
							db.writeByte((byte) 0x01); // the lfn is present -
														// 0x02
														// is
														// R/W
							int len = 21 + fs.filename.length();
							plen += len;
							db.writeShort(len); // size
							db.writeInt(fs.fileCounter);
							logger.debug("FOpened: {}", fs.fileCounter);
							db.writeLong(fs.filesize); // filesize at open.
							if (true) { // check if Filenames should be
										// reported.
								db.writeInt(dictID);// user_dictid
								db.writeBytes(fs.filename.getBytes());
								// to make this "C" string end
								// with null character.
								db.writeByte(0x0);
							}

							// reset the first bit
							fs.state &= 0xFFFE;
							subpackets += 1;
						}

						// adding TRANSFER levels
						db.writeByte((byte) 3); // 3 means isXfr
						db.writeByte((byte) 0); // no meaning
						db.writeShort(32); // 3*longlong + this header itself
						logger.debug("FTransfer: {}", fs.fileCounter);
						db.writeInt(fs.fileCounter);
						db.writeLong(fs.bytesRead.get());
						db.writeLong(fs.bytesVectorRead.get());
						db.writeLong(fs.bytesWritten.get());
						plen += 32;
						xfrpackets += 1;
						subpackets += 1;

						// add CLOSE structure
						if ((fs.state & 0x0004) > 0) {
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
							logger.debug("FClosed: {}", fs.fileCounter);
							db.writeInt(fs.fileCounter);

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
								db.writeLong(123456); // number of readv
														// segments
								db.writeInt(111000); // rdMin
								db.writeInt(112000); // rdMax
								db.writeInt(113000); // rvMin
								db.writeInt(111001); // rvMax
								db.writeInt(112002); // wrMin
								db.writeInt(113003); // wrMax
							}

							if (closedetails == 6) { // SSQ
								db.writeLong(123456); // number of readv
														// segments
								db.writeDouble(123.123);
								db.writeLong(123456); // number of readv
														// segments
								db.writeDouble(123.123);
								db.writeLong(123456); // number of readv
														// segments
								db.writeDouble(123.123);
								db.writeLong(123456); // number of readv
														// segments
								db.writeDouble(123.123);
							}
							// remove it
							if(cent.getValue().allFiles.remove(fent.getKey())==null)
								logger.error("Could not remove closed file!");
							
							subpackets += 1;
							plen += packlength;
						}
					}
				}

				// disconnects
				long ct = System.currentTimeMillis();
				for (Map.Entry<Integer, ConnectionInfo> ent : cmap.entrySet()) {
					// this also closes connection if longer than 5 days
					if (ent.getValue().disconnected == true || (ct - ent.getValue().duration) > 432000000) {

						logger.debug("ReportConnClose: {}", ent.getKey());
						db.writeByte((byte) 4); // 4 - means isDisc
						db.writeByte((byte) 0); // no meaning
						db.writeShort(8); // size
						db.writeInt(ent.getKey()); // userID
						subpackets += 1;
						plen += 8;
						cmap.remove(ent.getKey());
					}
				}

				logger.debug("f-stream message length: {} \t buffer length: {}", plen, db.writableBytes());
				db.setShort(2, (short) plen);
				db.setShort(12, xfrpackets);
				db.setShort(14, subpackets);
				if (plen>0 && plen<32767)
					mess.put(db);
				else 
					logger.warn("f-stream message longer than 32k. Not sending it.");

				// if message is long, half the reporting time
				if (plen > 20000) {
					if (factor > 0.1) {
						factor = factor / 2;
						createReportingThreads();
					}
				}
				// if message is smaller and of reduced size increase the period
				if (plen<10000 && factor < 1) {
						factor = factor * 2;
						createReportingThreads();
				}
				
			} catch (Exception e) {
				logger.error("unrecognized exception in sending f-stream:{} ", e.getMessage());
			}
		}

	}

}
