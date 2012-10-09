package edu.uchicago.monitor;

import java.util.Properties;

import org.dcache.xrootd.plugins.ChannelHandlerProvider;
import org.dcache.xrootd.plugins.ChannelHandlerFactory;

public class MonitorChannelHandlerProvider implements ChannelHandlerProvider
{
    @Override
    public ChannelHandlerFactory createFactory(String plugin, Properties properties)
    {
        if (MonitorChannelHandlerFactory.hasName(plugin)) {
            return new MonitorChannelHandlerFactory(properties);
        }
        return null;
    }
}
