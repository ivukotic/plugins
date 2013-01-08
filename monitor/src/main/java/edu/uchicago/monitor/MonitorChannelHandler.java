package edu.uchicago.monitor;

import java.util.HashMap;
import java.util.Map;
import org.dcache.xrootd.protocol.messages.AbstractResponseMessage;
import org.dcache.xrootd.protocol.messages.CloseRequest;
import org.dcache.xrootd.protocol.messages.GenericReadRequestMessage.EmbeddedReadRequest;
import org.dcache.xrootd.protocol.messages.LoginRequest;
import org.dcache.xrootd.protocol.messages.LoginResponse;
import org.dcache.xrootd.protocol.messages.OpenRequest;
import org.dcache.xrootd.protocol.messages.OpenResponse;
import org.dcache.xrootd.protocol.messages.ProtocolRequest;
import org.dcache.xrootd.protocol.messages.ReadRequest;
import org.dcache.xrootd.protocol.messages.ReadVRequest;
import org.dcache.xrootd.protocol.messages.WriteRequest;
import org.dcache.xrootd.protocol.messages.XrootdRequest;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitorChannelHandler extends SimpleChannelHandler {

	final Logger logger = LoggerFactory.getLogger(MonitorChannelHandler.class);

	private final Collector collector;
	private int connId = 0;
	private static int fileCounter=100;
	private long duration;

	public MonitorChannelHandler(Collector c) {
		collector = c;
	}

	@Override
	public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
//		logger.debug("UP mess:"+ e.toString());
        
		if (e instanceof MessageEvent) {
//			logger.info("MessageEvent UP");
			MessageEvent me = (MessageEvent) e;
			connId=me.getChannel().getId();
			Object message = me.getMessage();

			if (message instanceof ReadRequest) {
				ReadRequest rr = (ReadRequest) message;
//				FileStatistics fs = collector.fmap.get(rr.getFileHandle());
				FileStatistics fs = collector.fmap.get(connId);
				fs.bytesRead.getAndAdd(rr.bytesToRead());
				fs.reads.getAndIncrement();
				collector.totBytesRead.getAndAdd(rr.bytesToRead());
			}

			else if (message instanceof ReadVRequest) { 
				// this is very strange construction. if getFileHandle is always 0
				// when fileHandle is not 0? Can someone ask for vector read from 2 files simultaneously?
				ReadVRequest rr = (ReadVRequest) message;
				EmbeddedReadRequest[] err = rr.getReadRequestList();
				Map<Integer, Integer> fts = new HashMap<Integer, Integer>();
				for (int i = 0; i < rr.NumberOfReads(); i++) {
					Integer fh = err[i].getFileHandle();
					Integer val = fts.get(fh);
					if (val == null) {
						fts.put(fh, 0);
					} else {
						val += err[i].BytesToRead();
					}
				}
				int totVread = 0;
				for (Map.Entry<Integer, Integer> entry : fts.entrySet()) {
//					FileStatistics fs = collector.fmap.get(entry.getKey());
					FileStatistics fs = collector.fmap.get(connId);
					if (fs != null) {
						fs.bytesRead.getAndAdd(entry.getValue());
						fs.reads.getAndIncrement();
						totVread += entry.getValue();
					}
				}
				collector.totBytesRead.getAndAdd(totVread);
			}

			else if (message instanceof LoginRequest) {
				LoginRequest lr = (LoginRequest) message;
				int protocol = lr.getClientProtocolVersion();
				String user=lr.getUserName();
				int userpid=lr.getPID();
				logger.info("login request: client username" + user);
				logger.info("login request: client protocol" + protocol);
				logger.info("login request: client PID" + userpid);
			}

			// from OpenRequest we get:
			// mode (true -> readonly, false -> new, append, update, open
			// deleting existing)
			// filename

			else if (message instanceof OpenRequest) {
				// this is just a request. It gets temporary FS which gets placed in the map
				// at negative streamId. If really opened it will be removed and replaced with 
				// positive file dictID.
				OpenRequest or = (OpenRequest) message;
				int mode = 1;
				if (or.isReadOnly())
					mode = 1;
				else
					mode = 0; // not correct
				logger.warn("FILE OPEN REQUEST -------- connId:     " + connId);
				logger.info("path:     " + or.getPath());
				logger.info("readonly: " + mode);
				FileStatistics fs = new FileStatistics(fileCounter);
				fileCounter=(fileCounter+1) % 2147483647;
				fs.filename = or.getPath();
				logger.warn("FILE OPEN REQUEST : " + fs.filename);
				fs.mode = mode;
				collector.fmap.put(-connId, fs);
				collector.SendMapMessage(connId,"ivukotic.12345:"+connId+"@mycomputer");
//				collector.sendMyMessage(connId,"ivukotic.12345:"+connId+"@mycomputer");
//				logger.info("------------------------------------");
			}

			else if (message instanceof CloseRequest) {
//				CloseRequest cr = (CloseRequest) message;
				logger.warn("FILE CLOSE REQUEST ------- connId:     " + connId);
//				collector.closeEvent(connId, cr.getFileHandle());
				collector.closeEvent(connId, connId);
//				logger.info("------------------------------------");
			}

			else if (message instanceof WriteRequest) {
				WriteRequest wr = (WriteRequest) message;
//				FileStatistics fs = collector.fmap.get(wr.getFileHandle());
				FileStatistics fs = collector.fmap.get(connId);
				fs.bytesWritten.getAndAdd(wr.getDataLength());
				fs.writes.getAndIncrement();
				collector.totBytesWriten.getAndAdd(wr.getDataLength());
			}
			else if (message instanceof ProtocolRequest) {
				ProtocolRequest req = (ProtocolRequest) message;
				logger.info("ProtocolRequest streamId: " + req.getStreamId() + "\treqID: " + req.getRequestId() +"\t data:"+req.toString() );
			}
			else if (message instanceof XrootdRequest) {
				XrootdRequest req = (XrootdRequest) message;
				logger.info("I-> streamID: " + req.getStreamId() + "\treqID: " + req.getRequestId() +"\t data:"+req.toString());
			}
			else{
				logger.info("Unhandled message event UP: " + me.toString());
			}
		}

		else if (e instanceof ChannelStateEvent) {
			logger.info("ChannelStateEvent UP");
			
			ChannelStateEvent se = (ChannelStateEvent) e;
//			logger.debug("Channel State Event UP : " + se.getState().toString() +"\t"+  se.getValue().toString());
			
			if (se.getState() == ChannelState.OPEN && se.getValue() != null) {
				if (se.getValue() == Boolean.TRUE) {
					logger.info("CONNECTION OPEN ATTEMPT EVENT");
					collector.addConnectionAttempt();
					logger.info("done.");
				}
			}

			else if (se.getState() == ChannelState.CONNECTED && se.getValue() == null) {
				logger.info("DISCONNECT EVENT --------------------");
				duration = System.currentTimeMillis() - duration;
				logger.info("connId:             " + connId);
				logger.info("connection duration:" + duration);
				collector.disconnectedEvent(connId, duration);
//				logger.info("------------------------------------");
//				logger.info(collector.toString());
			}
			 else {
				 logger.info("Unhandled ChannelState Event UP : " + se.getState().toString() +"\t"+  se.toString());
			 }
			
		}

		else if (e instanceof WriteCompletionEvent) {
//			logger.debug("WriteCompletionEvent UP");
//        	WriteCompletionEvent me = (WriteCompletionEvent) e;
//        	logger.debug(me.toString());
		}

		else if (e instanceof ExceptionEvent) { 
			logger.info("ExceptionEvent UP");
			ExceptionEvent dee = (ExceptionEvent) e;
			logger.error("eXception thrown message " + dee.toString());
		} 
		
		else { // completely impossible
			logger.info("Monitor not handling this kind of message. UP");
		}

		super.handleUpstream(ctx, e);
	}

	@Override
	public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {

//		logger.info("DOWN mess:"+ e.toString());
		
		if (e instanceof MessageEvent) {
//			logger.info("MessageEvent DOWN");
			
			MessageEvent me = (MessageEvent) e;
			Object message = me.getMessage();
			if (me.getChannel().getId()!=connId){
				logger.error("response before request! very strange");
			}
//			connId = me.getChannel().getId();
			
			// logger.info("connId:         " + connId);
			// logger.info("message:        " + message.toString());

			if (message instanceof LoginResponse) {
				// LoginResponse lr = (LoginResponse) message;
				// logger.info("reqID: " +
				// lr.getRequest().getStreamId());
				// int [] sessionID= new int[2];
				// ChannelBuffer ssid = lr.getBuffer();
				// StringBuffer hexString = new StringBuffer();
				// logger.info("buffer:"+ssid.toString("ISO-8859-1"));
				// for (int i=8;i<16;i++) {
				// logger.info("byte: "+i+"\t value: "+ssid.getByte(i));
				// hexString.append(Integer.toHexString(0xFF & ssid.getByte(i)
				// ));
				// }
				//
				// for (int j=2;j<4;j++){
				// sessionID[j-2]=0;
				// for (int i = j*4; i < (j+1)*4; i++) {
				// int shift = (4 - 1 - i) * 8;
				// sessionID[j-2] += (ssid.getByte(i) & 0x000000FF) << shift;
				// }
				// }
				// logger.info("ssid : H  "+hexString);
				// logger.info(Arrays.toString(sessionID));

				duration = System.currentTimeMillis();
				logger.warn("CONNECT EVENT ----------------------");
				logger.info("connId:     " + connId);
				logger.info("host ip:    " + e.getChannel().getRemoteAddress());
				collector.connectedEvent(connId, e.getChannel().getRemoteAddress());
				logger.info("------------------------------------");
			}

			else if (message instanceof OpenResponse) {
				OpenResponse OR = (OpenResponse) message;
				Integer streamID = OR.getRequest().getStreamId();
				// logger.info("FILE OPEN RESPONSE --------- stream Id: " + streamID);
				// logger.info("filehandle: " + OR.getFileHandle());
				// logger.info("filesize  : " + OR.getFileStatus().getSize());
				// logger.info("fileid    : " + OR.getFileStatus().getId()); // usually 0 ?!
				// logger.info("filestatus: " + OR.getFileStatus().toString());
				FileStatistics fs = collector.fmap.get(-connId);
				if (fs == null) {
					logger.error("Serious problem: can not find file with handle " + connId);
				}
				fs.filesize = OR.getFileStatus().getSize();
//				collector.fmap.put(OR.getFileHandle(), fs);
				collector.fmap.put(connId, fs);
				collector.fmap.remove(-connId);
				collector.openEvent(connId, fs);
				// logger.info("-----------------------------------------------");
			}

			else if (message instanceof AbstractResponseMessage) {
//				 AbstractResponseMessage ARM=(AbstractResponseMessage) message;
//				 XrootdRequest req = ARM.getRequest();
//				 logger.info("I-> streamID: "+req.getStreamId()+ "\treqID: "+req.getRequestId());
//				 logger.info("IB->: "+ (ARM.getBuffer()).toString("UTF-8") ) ;
//				 logger.warn("Unhandled AbstractResponseMessage DOWN: "+ me.toString());
			}
			else { 
				logger.warn("Unhandled MessageEvent DOWN: "+ me.toString() );
			}

		}

		else if (e instanceof ChannelStateEvent) {
			ChannelStateEvent se = (ChannelStateEvent) e;
			logger.info("Channel State Event DOWN : " + se.getState().toString() + "\t" + se.getValue().toString());
		}

		else if (e instanceof WriteCompletionEvent) {
			// commented for performance reasons
//			logger.debug("WriteCompletionEvent DOWN");
//        	WriteCompletionEvent me = (WriteCompletionEvent) e;
//        	logger.debug(me.toString());
		}

		else if (e instanceof ExceptionEvent) { 
			logger.info("ExceptionEvent DOWN");
			ExceptionEvent dee = (ExceptionEvent) e;
			logger.error("eXception thrown message " + dee.toString());
		} 
		
		else { // completely impossible
			logger.error("Monitor not handling this kind of message. DOWN");
		}
		
		super.handleDownstream(ctx, e);
	}

}
