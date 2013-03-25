package edu.uchicago.xrootd4j;

import java.util.Properties;

import org.dcache.xrootd.plugins.AuthorizationProvider;
import org.dcache.xrootd.plugins.AuthorizationFactory;

public class AtlasAuthorizationProvider implements AuthorizationProvider
{
    @Override
    public AuthorizationFactory
        createFactory(String plugin, Properties properties)
    {
        if (AtlasAuthorizationFactory.hasName(plugin)) {
            return new AtlasAuthorizationFactory(properties);
        }
        return null;
    }
}
