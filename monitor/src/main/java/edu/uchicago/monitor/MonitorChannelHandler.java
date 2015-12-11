package edu.uchicago.monitor;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.atomic.AtomicInteger;

import org.dcache.xrootd.protocol.messages.CloseRequest;
import org.dcache.xrootd.protocol.messages.ErrorResponse;
import org.dcache.xrootd.protocol.messages.GenericReadRequestMessage.EmbeddedReadRequest;
import org.dcache.xrootd.protocol.messages.LoginRequest;
import org.dcache.xrootd.protocol.messages.LoginResponse;
import org.dcache.xrootd.protocol.messages.OpenRequest;
import org.dcache.xrootd.protocol.messages.OpenResponse;
import org.dcache.xrootd.protocol.messages.ReadRequest;
import org.dcache.xrootd.protocol.messages.ReadVRequest;
import org.dcache.xrootd.protocol.messages.WriteRequest;
import org.dcache.xrootd.protocol.messages.XrootdRequest;
import org.dcache.xrootd.protocol.messages.XrootdResponse;
import org.dcache.xrootd.util.FileStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dcache.xrootd.protocol.XrootdProtocol.*;

public class MonitorChannelHandler extends ChannelDuplexHandler {
	// SimpleChannelHandler {

	private final static Logger logger = LoggerFactory.getLogger(MonitorChannelHandler.class);
	private final static AtomicInteger fileCounter = new AtomicInteger();

	private final Collector collector;

	private int connId;

	public MonitorChannelHandler(Collector c) {
		collector = c;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		logger.debug("** CONNECTION {} ACTIVE **", ctx.hashCode());
		connId = ctx.hashCode();
		collector.connectedEvent(connId);
		super.channelActive(ctx);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		logger.debug("** CONNECTION {} INACTIVE **", ctx.hashCode());
		collector.disconnectedEvent(connId);
		super.channelInactive(ctx);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//		 logger.debug(" ** MONITOR read handler called. ** ");

		if (msg instanceof XrootdRequest) {
//			logger.debug(" ** XROOTDRequest. ** ");
			handleRequests(ctx, (XrootdRequest) msg);
		}
		ctx.fireChannelRead(msg);

	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		super.write(ctx, msg, promise);
//		logger.debug(" ** MONITOR write handler called. ** ");

		if (msg instanceof XrootdResponse<?>) {
//			logger.debug(" ** XROOTDResponse. **");
			handleResponse(ctx, (XrootdResponse<?>) msg);
		}

	}

	public void handleRequests(ChannelHandlerContext ctx, XrootdRequest request) throws Exception {
		
		int reqId = request.getRequestId();
//		logger.debug("REQUEST message: {}  id: {}", request, reqId);

		try {
			switch (reqId) {

			case kXR_login:
				LoginRequest lr = (LoginRequest) request;
				int protocol = lr.getClientProtocolVersion();
				String user = lr.getUserName();
				int userpid = lr.getPID();
				logger.info("LOGIN REQUEST   {}   client username: {}    protocol: {}     client PID: {}", connId, user, protocol, userpid);
				collector.getCI(connId).logUserRequest(user, userpid);
				break;

			case kXR_open:
				// fully handled on the response side
				OpenRequest or = (OpenRequest) request;
				logger.debug("FILE OPEN REQUEST    connId: " + connId +  "   readonly: " + or.isReadOnly() + "   path :" + or.getPath());
				break;

			case kXR_read:
				ReadRequest rr = (ReadRequest) request;
//				logger.info("READ REQUEST ------- connId:    {} ", connId);
				FileStatistics fs = collector.getCI(connId).getFile(rr.getFileHandle());
				if (fs != null) {
					fs.bytesRead.getAndAdd(rr.bytesToRead());
					fs.reads.getAndIncrement();
					collector.totBytesRead.getAndAdd(rr.bytesToRead());
				} else {
					logger.warn("can't get connId. should not happen except in case of recent restart.", connId);
				}
				break;

			case kXR_readv:
				ReadVRequest rvr = (ReadVRequest) request;
				EmbeddedReadRequest[] err = rvr.getReadRequestList();
				long totVread = 0;
				for (int i = 0; i < rvr.NumberOfReads(); i++) {
					FileStatistics rvrfs = collector.getCI(connId).getFile(err[i].getFileHandle()); // probably
																									// very
																									// slow.
					rvrfs.vectorReads.getAndIncrement();
					rvrfs.bytesVectorRead.getAndAdd(err[i].BytesToRead());
					totVread += err[i].BytesToRead();
				}
				collector.totBytesRead.getAndAdd(totVread);
				break;

			case kXR_write:
				WriteRequest wr = (WriteRequest) request;
				FileStatistics wfs = collector.getCI(connId).getFile(wr.getFileHandle());
				if (wfs != null) {
					wfs.bytesWritten.getAndAdd(wr.getDataLength());
					wfs.writes.getAndIncrement();
					collector.totBytesWriten.getAndAdd(wr.getDataLength());
				} else {
					logger.warn("can't get connId. should not happen except in case of recent restart.", connId);
				}
				break;

			case kXR_close:
				CloseRequest cr = (CloseRequest) request;
				logger.debug("FILE CLOSE REQUEST ------- connId:    {} ", connId);
				collector.getCI(connId).getFile(cr.getFileHandle()).close();
				collector.closeFileEvent(connId, connId);
				break;

			default:
				break;
			}

			// } catch (XrootdException e) {
			// ErrorResponse error = new ErrorResponse<>(request, e.getError(),
			// Strings.nullToEmpty(e.getMessage()));
			// ctx.writeAndFlush(error);
		} catch (RuntimeException e) {
			logger.error("xrootd server error while processing " + request + " (please report this to support@dcache.org)", e);
			ErrorResponse<?> error = new ErrorResponse<>(request, kXR_ServerError, String.format("Internal server error (%s)", e.getMessage()));
			ctx.writeAndFlush(error);
		}

	}

	public void handleResponse(ChannelHandlerContext ctx, XrootdResponse<?> response) throws Exception {

		if (response instanceof OpenResponse) {
			OpenResponse OR = (OpenResponse) response;
			OpenRequest or = (OpenRequest) OR.getRequest();
			logger.debug("FILE OPEN RESPONSE - REQUEST: {} ", or.toString());
			
			int mode = 1;
			if (or.isReadOnly())
				mode = 1;
			else
				mode = 0; // not correct

			fileCounter.compareAndSet(2147483647, 0);
			fileCounter.incrementAndGet();
			FileStatistics fs = new FileStatistics(fileCounter.get());
			fs.filename = or.getPath();
			fs.mode = mode;
			FileStatus fileStatus=OR.getFileStatus();
			if (fileStatus==null){
				fs.filesize=0;
			}else{
				fs.filesize = fileStatus.getSize();
			}
			logger.debug("FILE OPEN RESPONSE  connId: {}    path :{},  fileCounter: {}", connId, fs.filename, fs.fileCounter);
			collector.getCI(connId).addFile(OR.getFileHandle(), fs);
		}

		else if (response instanceof LoginResponse) {
			// LoginResponse lr = (LoginResponse) response;
			// LoginRequest LR = lr.getRequest();
			collector.addLoginAttempt();
			collector.loggedEvent(connId, ctx.channel().remoteAddress());
			logger.debug("LOGING RESPONSE connId: {}    host ip: {}", connId, ctx.channel().remoteAddress());
		} else {
//			logger.debug("OTHER RESPONSE");
		}

	}

}
