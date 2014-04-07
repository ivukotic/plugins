package edu.uchicago.redirector;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dcache.xrootd.protocol.XrootdProtocol;
import org.dcache.xrootd.protocol.messages.ErrorResponse;
import org.dcache.xrootd.protocol.messages.OpenRequest;
import org.dcache.xrootd.protocol.messages.RedirectResponse;

public class RedirectPlugin extends SimpleChannelDownstreamHandler
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
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception
    {
        if (e.getMessage() instanceof ErrorResponse) {
            ErrorResponse error = (ErrorResponse) e.getMessage();
            String em=error.toString();
            logger.debug("redirector intercepted error:"+em);
            if (error.getRequest() instanceof OpenRequest && em.contains(String.valueOf(XrootdProtocol.kXR_NotFound)) ){
            	// && error.getErrorNumber() == XrootdProtocol.kXR_NotFound) {
                logger.debug("redirecting upstream");
                e.getChannel().write(new RedirectResponse(error.getRequest(), host, port, "", ""));
                return;
            }
        }
        ctx.sendDownstream(e);
    }
}
