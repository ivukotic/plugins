package edu.uchicago.monitor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.dcache.xrootd.plugins.ChannelHandlerFactory;
import org.jboss.netty.channel.ChannelHandler;

public class MonitorChannelHandlerFactory implements ChannelHandlerFactory
{
    final static String NAME = "edu.uchicago.monitor";    
    final static Set<String> ALTERNATIVE_NAMES = new HashSet<String>(Arrays.asList(NAME));
    
    private Collector collector;
    
    static boolean hasName(String name)
    {
        return ALTERNATIVE_NAMES.contains(name);
    }

    public MonitorChannelHandlerFactory(Properties properties)
    {	
    	collector = new Collector(properties);
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
        return new MonitorChannelHandler( collector );
    }
}
