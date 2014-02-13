package edu.uchicago.monitor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.dcache.xrootd.plugins.ChannelHandlerFactory;
import org.jboss.netty.channel.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitorChannelHandlerFactory implements ChannelHandlerFactory{
	

	final static Logger logger = LoggerFactory.getLogger(MonitorChannelHandlerFactory.class);
	
    final static String NAME = "edu.uchicago.monitor";    
    final static Set<String> ALTERNATIVE_NAMES = new HashSet<String>(Arrays.asList(NAME));
    
    final static Collector collector=new Collector();
    
    
    static boolean hasName(String name)
    {
        return ALTERNATIVE_NAMES.contains(name);
    }

    public MonitorChannelHandlerFactory(Properties properties)
    {	
    	
    		logger.debug("setting collector properties...");
    		collector.init(properties);
    	
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Monitor xrootd plugin";
    }

    @Override
    public ChannelHandler createHandler()
    {	
    	logger.debug("creating a new MonitorChannelHandler...");
        return new MonitorChannelHandler( collector );
    }
}
