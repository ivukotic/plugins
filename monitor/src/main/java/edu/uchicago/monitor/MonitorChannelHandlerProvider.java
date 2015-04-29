package edu.uchicago.monitor;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.dcache.xrootd.plugins.ChannelHandlerProvider;
import org.dcache.xrootd.plugins.ChannelHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitorChannelHandlerProvider implements ChannelHandlerProvider
{
	private final static Logger logger = LoggerFactory.getLogger(MonitorChannelHandlerProvider.class);
	
	 private final static String[] PROPERTIES = 
	     { "xrootd.monitor.site", "xrootd.monitor.vo", "xrootd.monitor.summary", "xrootd.monitor.detailed", "xrootd.monitor.servername" };
	 private final static Map<Map<String,String>, MonitorChannelHandlerFactory> factories = new HashMap<>();
	 
    @Override
    public ChannelHandlerFactory createFactory(String plugin, Properties properties)
    {
    	logger.debug("creating Factory");
    	
        if (MonitorChannelHandlerFactory.hasName(plugin)) {
        	logger.info("found monitoring plugin");
        	Map<String,String> config = new HashMap<>();
            for (String name : PROPERTIES) {
            	logger.info("property: {} value:{}",name, properties.getProperty(name));
                config.put(name, properties.getProperty(name));
            }
            MonitorChannelHandlerFactory factory = factories.get(config);
            if (factory == null) {
            	logger.info("creating new monitoring factory");
                factory = new MonitorChannelHandlerFactory(properties);
                factories.put(config, factory);
            }
            logger.debug("Monitoring factories created: "+factories.size());

            return factory;
            
        }
        return null;
    }
}
