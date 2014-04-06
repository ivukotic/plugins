package edu.uchicago.redirector;

import java.util.Properties;

import org.dcache.xrootd.plugins.ChannelHandlerProvider;
import org.dcache.xrootd.plugins.ChannelHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RedirectPluginProvider implements ChannelHandlerProvider {

	final static Logger logger = LoggerFactory.getLogger(RedirectPluginProvider.class);

    @Override
    public ChannelHandlerFactory createFactory(String plugin, Properties properties)
    {
    	logger.error("CREATEDDDDDDDDDDDDDDDDDDDD..."+plugin);
    	
        if (RedirectPluginFactory.hasName(plugin)) {
        	logger.error("HASNAME...");
            return new RedirectPluginFactory(properties);
        }
        return null;
    }
}
