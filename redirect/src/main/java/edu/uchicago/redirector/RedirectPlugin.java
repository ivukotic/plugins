package edu.uchicago.redirector;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dcache.xrootd.protocol.XrootdProtocol;
import org.dcache.xrootd.protocol.messages.ErrorResponse;
import org.dcache.xrootd.protocol.messages.OpenRequest;
import org.dcache.xrootd.protocol.messages.RedirectResponse;

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
		
        if (msg instanceof ErrorResponse) {
            ErrorResponse<?> error = (ErrorResponse<?>) msg;
            String em=error.toString();
            logger.debug("redirector intercepted error:"+em);
            if (error.getRequest() instanceof OpenRequest && em.contains(String.valueOf(XrootdProtocol.kXR_NotFound)) ){
            	// && error.getErrorNumber() == XrootdProtocol.kXR_NotFound) {
                logger.debug("redirecting upstream");
                msg = new RedirectResponse(error.getRequest(), host, port, "", "");
            }
        }
		
		super.write(ctx, msg, promise);

	}
	
}
