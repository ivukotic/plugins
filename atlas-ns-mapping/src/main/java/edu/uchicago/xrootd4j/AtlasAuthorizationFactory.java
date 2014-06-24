package edu.uchicago.xrootd4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.dcache.xrootd.plugins.AuthorizationFactory;

public class AtlasAuthorizationFactory implements AuthorizationFactory {
	final static String NAME = "atlas-name-to-name-plugin";
	final static Set<String> ALTERNATIVE_NAMES = new HashSet<String>(Arrays.asList(NAME));

	private final RucioN2N rucio;

	private final AtlasAuthorizationHandler AAH;

	public AtlasAuthorizationFactory(Properties properties) {
		rucio = new RucioN2N(properties);
		AAH = new AtlasAuthorizationHandler(rucio, properties);
	}

	static boolean hasName(String name) {
		return ALTERNATIVE_NAMES.contains(name);
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getDescription() {
		return "ATLAS xrootd name-to-name plugin";
	}

	@Override
	public AtlasAuthorizationHandler createHandler() {
		return AAH;
	}
}
