package edu.uchicago.redirector;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dcache.xrootd.protocol.XrootdProtocol;
import org.dcache.xrootd.protocol.messages.AsyncResponse;
import org.dcache.xrootd.protocol.messages.ErrorResponse;
import org.dcache.xrootd.protocol.messages.OpenRequest;
import org.dcache.xrootd.protocol.messages.RedirectResponse;
import org.dcache.xrootd.protocol.messages.XrootdResponse;

public class RedirectPlugin extends ChannelOutboundHandlerAdapter
{

	final static Logger logger = LoggerFactory.getLogger(RedirectPlugin.class);
    private final String host;
    private final int port;

    public RedirectPlugin(String host, int port)
    {
        this.host = host;
        this.port = port;
    }

    
	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		
		ErrorResponse<?> error=null;
        
		if (msg instanceof ErrorResponse){
        	error = (ErrorResponse<?>) msg;
        }
        
        if (msg instanceof AsyncResponse) {
        	AsyncResponse<?> AR = (AsyncResponse<?>) msg;
        	XrootdResponse<?> eresp=AR.getResponse();
        	if (eresp instanceof ErrorResponse){
        		error=(ErrorResponse<?>) eresp;
        	}
        }
        
        if (error!=null){
            String em=error.toString();
            logger.debug("redirector intercepted error: "+em);
            if (error.getRequest() instanceof OpenRequest){
            	switch (error.getErrorNumber()){
            		case XrootdProtocol.kXR_NotFound:
            			logger.debug("file not found. redirecting upstream");
                		msg = new RedirectResponse(error.getRequest(), host, port, "", "");
            			break;
            		case XrootdProtocol.kXR_IOError:
                		logger.debug("IO error. redirecting upstream");
                		msg = new RedirectResponse(error.getRequest(), host, port, "", "");
                		break;
            		case XrootdProtocol.kXR_FSError:
                		logger.debug("FS error. redirecting upstream");
                		msg = new RedirectResponse(error.getRequest(), host, port, "", "");
//                		break;
//            		case XrootdProtocol.kXR_NotAuthorized:
//                		logger.debug("Not authorized. redirecting upstream");
//                		msg = new RedirectResponse(error.getRequest(), host, port, "", "");
            	} 
            }
        }
        
		super.write(ctx, msg, promise);

	}
	
}
