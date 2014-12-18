package edu.uchicago.monitor;

import java.util.concurrent.atomic.AtomicInteger;

import org.dcache.xrootd.protocol.XrootdProtocol;
import org.dcache.xrootd.protocol.messages.AbstractResponseMessage;
import org.dcache.xrootd.protocol.messages.CloseRequest;
import org.dcache.xrootd.protocol.messages.ErrorResponse;
import org.dcache.xrootd.protocol.messages.GenericReadRequestMessage.EmbeddedReadRequest;
import org.dcache.xrootd.protocol.messages.LoginRequest;
import org.dcache.xrootd.protocol.messages.OpenRequest;
import org.dcache.xrootd.protocol.messages.OpenResponse;
import org.dcache.xrootd.protocol.messages.ProtocolRequest;
import org.dcache.xrootd.protocol.messages.ReadRequest;
import org.dcache.xrootd.protocol.messages.ReadVRequest;
import org.dcache.xrootd.protocol.messages.RedirectResponse;
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

	private final static Logger logger = LoggerFactory.getLogger(MonitorChannelHandler.class);
	private final static AtomicInteger fileCounter = new AtomicInteger();

	private final Collector collector;
	private int connId;

	public MonitorChannelHandler(Collector c) {
		collector = c;
	}

	@Override
	public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
		logger.debug("UP mess: {}", e);

		if (e instanceof MessageEvent) {
			// logger.info("MessageEvent UP");
			MessageEvent me = (MessageEvent) e;
			connId = me.getChannel().getId();
			Object message = me.getMessage();

			if (message instanceof ReadRequest) {
				ReadRequest rr = (ReadRequest) message;
				FileStatistics fs = collector.getCI(connId).getFile(rr.getFileHandle());
				if (fs != null) {
					fs.bytesRead.getAndAdd(rr.bytesToRead());
					fs.reads.getAndIncrement();
					collector.totBytesRead.getAndAdd(rr.bytesToRead());
				} else {
					logger.warn("can't get connId {} in fmap. should not happen except in case of recent restart.", connId);
				}
			}

			else if (message instanceof ReadVRequest) {
				ReadVRequest rr = (ReadVRequest) message;

				EmbeddedReadRequest[] err = rr.getReadRequestList();
				long totVread = 0;
				for (int i = 0; i < rr.NumberOfReads(); i++) {
					FileStatistics fs = collector.getCI(connId).getFile(err[i].getFileHandle());
					fs.vectorReads.getAndIncrement();
					fs.bytesVectorRead.getAndAdd(err[i].BytesToRead());
					totVread += err[i].BytesToRead();
				}

				collector.totBytesRead.getAndAdd(totVread);

			}

			else if (message instanceof LoginRequest) {
				LoginRequest lr = (LoginRequest) message;
				int protocol = lr.getClientProtocolVersion();
				String user = lr.getUserName();
				int userpid = lr.getPID();
				logger.info("LOGIN REQUEST   {}   client username: {}    protocol: {}     client PID: {}", connId, user, protocol, userpid);
				collector.getCI(connId).logUserRequest(user, userpid);
			}

			// from OpenRequest we get:
			// mode (true -> readonly, false -> new, append, update, open
			// deleting existing)
			// filename

			else if (message instanceof OpenRequest) {
				// this is just a request. It gets temporary FS which gets
				// placed in the map
				// at negative streamId. If really opened it will be removed and
				// replaced with
				// positive file dictID.
				// OpenRequest or = (OpenRequest) message;
				// int mode = 1;
				// if (or.isReadOnly())
				// mode = 1;
				// else
				// mode = 0; // not correct
				// FileStatistics fs = new FileStatistics(fileCounter);
				// fileCounter = (fileCounter + 1) % 2147483647;
				// fs.filename = or.getPath();
				// logger.warn("FILE OPEN REQUEST    connId: " + connId +
				// "   readonly: " + mode + "   path :" + fs.filename);
				// fs.mode = mode;
				// // collector.fmap.put(-connId, fs);
				// collector.getCI(connId).addFile(fs);
			}

			else if (message instanceof CloseRequest) {
				CloseRequest cr = (CloseRequest) message;
				logger.info("FILE CLOSE REQUEST ------- connId:    {} ", connId);
				collector.getCI(connId).getFile(cr.getFileHandle()).close();
				collector.closeFileEvent(connId, connId);
			}

			else if (message instanceof WriteRequest) {
				WriteRequest wr = (WriteRequest) message;
				FileStatistics fs = collector.getCI(connId).getFile(wr.getFileHandle());
				if (fs != null) {
					fs.bytesWritten.getAndAdd(wr.getDataLength());
					fs.writes.getAndIncrement();
					collector.totBytesWriten.getAndAdd(wr.getDataLength());
				}
			} else if (message instanceof ProtocolRequest) {
				// ProtocolRequest req = (ProtocolRequest) message;
				// logger.info("ProtocolRequest streamId: " + req.getStreamId()
				// + "\treqID: " + req.getRequestId() + "\t data:" +
				// req.toString());
			} else if (message instanceof XrootdRequest) {
				XrootdRequest req = (XrootdRequest) message;
				logger.info("I-> streamID: {} \treqID: {}\t data: {}", req.getStreamId(), req.getRequestId(), req);
			} else {
				logger.info("Unhandled message event UP: {}", me);
			}
		}

		else if (e instanceof ChannelStateEvent) {
			logger.debug("ChannelStateEvent UP");

			ChannelStateEvent se = (ChannelStateEvent) e;
			connId = se.getChannel().getId();

			// logger.debug("Channel State Event UP : " +
			// se.getState().toString() +"\t"+ se.getValue().toString());

			if (se.getState() == ChannelState.OPEN && se.getValue() != null) {
				if (se.getValue() == Boolean.TRUE) {
					logger.info("CHANNEL OPEN EVENT {}", connId);
					collector.addConnectionAttempt();
					logger.debug("done.");
				} else {
					logger.debug("CHANNEL not OPEN");
				}
			}

			else if (se.getState() == ChannelState.BOUND) {
				// if (se.getValue() != null) {
				// logger.debug("CHANNEL BOUND EVENT " + se.getValue());
				// } else {
				// logger.debug("CHANNEL BOUND EVENT with null Value. Should not happen.");
				// }
			}

			else if (se.getState() == ChannelState.CONNECTED) {
				if (se.getValue() == null) {
					collector.disconnectedEvent(connId);
				} else {
					logger.info("CHANNEL CONNECT EVENT   connId: {}    value:{}", connId, se.getValue());
					collector.connectedEvent(connId);
				}
			}

			else if (se.getState() == ChannelState.INTEREST_OPS) {
				// // not sure why but this happens very often on real server.
				// logger.debug("INTEREST_CHANGED");
			}

			else {
				logger.info("Unhandled ChannelState Event UP : {} \t {}", se.getState(), se);
			}

		}

		else if (e instanceof WriteCompletionEvent) {
			// logger.debug("WriteCompletionEvent UP");
			// WriteCompletionEvent me = (WriteCompletionEvent) e;
			// logger.debug(me.toString());
		}

		else if (e instanceof ExceptionEvent) {
			logger.error("eXception thrown message {}", e);
		}

		else { // completely impossible
			logger.info("Monitor not handling this kind of message. UP");
		}

		super.handleUpstream(ctx, e);
	}
	
	@Override
	public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {

//		 logger.debug("DOWN mess {}", e.toString());

		if (e instanceof MessageEvent) {
			// logger.debug("MessageEvent DOWN");
			// logger.debug("message:        " + message.toString());

			MessageEvent me = (MessageEvent) e;
			Object message = me.getMessage();
			if (me.getChannel().getId() != connId) {
				logger.error("response before request! very strange");
			}

			if (me instanceof ErrorResponse) {
	            ErrorResponse error = (ErrorResponse) me;
	            if (error.getRequest() instanceof OpenRequest && error.getErrorNumber() == XrootdProtocol.kXR_NotFound) {
	            	logger.error("error on open. {}", error);
	            	int port=1094;
	            	String host="myRedirector.com"; 
	            	logger.error("will redirect: {}", e);
	                e.getChannel().write(new RedirectResponse(error.getRequest(), host, port, "", ""));
	                return;
	            }
	        }
			
			// if (message instanceof ReadResponse){
			// // this happens a lot
			// }
			// if (message instanceof LoginResponse) {
			// // LoginResponse lr = (LoginResponse) message;
			// // for whatever reason this does not happen
			// }

			if (message instanceof OpenResponse) {
				OpenResponse OR = (OpenResponse) message;
				OpenRequest or = (OpenRequest) OR.getRequest();
				int mode = 1;
				if (or.isReadOnly())
					mode = 1;
				else
					mode = 0; // not correct
				
				fileCounter.compareAndSet(9999999, 0);
				FileStatistics fs = new FileStatistics(fileCounter.incrementAndGet());
				fs.filename = or.getPath();
				fs.mode = mode;
				fs.filesize = OR.getFileStatus().getSize();
				logger.warn("FILE OPEN RESPONSE  connId: {}    path :{},  fileCounter: {}", connId, fs.filename, fs.fileCounter);
				collector.getCI(connId).addFile(OR.getFileHandle(), fs);
			}

			else if (message instanceof AbstractResponseMessage) {
				AbstractResponseMessage ARM = (AbstractResponseMessage) message;
				XrootdRequest req = ARM.getRequest();
				if (req instanceof LoginRequest) {
					logger.debug("LOGGIN RESPONSE connId:     {}    host ip:    {}", connId, e.getChannel().getRemoteAddress());
					collector.loggedEvent(connId, e.getChannel().getRemoteAddress());
				}
			} else {
				logger.warn("Unhandled MessageEvent DOWN: {}", me);
			}

		}

		else if (e instanceof ChannelStateEvent) {
			ChannelStateEvent se = (ChannelStateEvent) e;
			logger.info("Channel State Event DOWN : {} \t {}", se.getState(), se.getValue());
		}

		else if (e instanceof WriteCompletionEvent) {
			// commented for performance reasons
			// logger.debug("WriteCompletionEvent DOWN");
			// WriteCompletionEvent me = (WriteCompletionEvent) e;
			// logger.debug(me.toString());
		}

		else if (e instanceof ExceptionEvent) {
			logger.error("eXception thrown message {}", e);
		}

		else { // completely impossible
			logger.error("Monitor not handling this kind of message. DOWN");
		}

		super.handleDownstream(ctx, e);
	}

}
