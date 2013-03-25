package edu.uchicago.xrootd4j;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;

import javax.security.auth.Subject;

import org.dcache.xrootd.plugins.AuthorizationHandler;
import org.dcache.xrootd.protocol.XrootdProtocol.FilePerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtlasAuthorizationHandler implements AuthorizationHandler {

	final Logger logger = LoggerFactory.getLogger(AtlasAuthorizationHandler.class);


	public AtlasAuthorizationHandler(Properties properties) throws IllegalArgumentException, MissingResourceException {

	}



	@Override
	public String authorize(Subject subject, InetSocketAddress localAddress, InetSocketAddress remoteAddress, String path, Map<String, String> opaque,
			int request, FilePerm mode) throws SecurityException, GeneralSecurityException {

		logger.info("GOT to translate: " + path);

		if (path.startsWith("pnfs/")) {
			return path;
		}

		String LFN = path;
		if (!LFN.startsWith("rucio/")) {
			logger.error("*** Error: gLFN must start with /rucio/. ");
			return "";
		}

		String sLFN = "lfn://grid" + LFN;
		LFN = "lfn://" + "//grid" + LFN;

		logger.info("FINALY translating: " + LFN);


		return "";
	}

}
