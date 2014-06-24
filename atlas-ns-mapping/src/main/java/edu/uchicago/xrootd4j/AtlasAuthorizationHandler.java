package edu.uchicago.xrootd4j;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;

import javax.security.auth.Subject;

import org.dcache.xrootd.core.XrootdException;
import org.dcache.xrootd.plugins.AuthorizationHandler;
import org.dcache.xrootd.protocol.XrootdProtocol;
import org.dcache.xrootd.protocol.XrootdProtocol.FilePerm;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class AtlasAuthorizationHandler implements AuthorizationHandler {

	final static Logger log = LoggerFactory.getLogger(AtlasAuthorizationHandler.class);

	private static RucioN2N rucio = null;

	public AtlasAuthorizationHandler(RucioN2N rc, Properties properties) throws IllegalArgumentException, MissingResourceException {
		rucio = rc;
	}


	@Override
	public String authorize(Subject subject, InetSocketAddress localAddress, InetSocketAddress remoteAddress, String path, Map<String, String> opaque,
			int request, FilePerm mode) throws SecurityException, GeneralSecurityException, XrootdException {

		log.info("GOT to translate: " + path);

		if (path.startsWith("/atlas/rucio/")) {
			String pfn = rucio.translate(path);
			if (pfn == null) {
				log.info("rucio name not found.");
				pfn = "";
				throw new XrootdException(XrootdProtocol.kXR_NotFound, "rucio name not found. ");
			} else {
				log.info("rucio translated name: " + pfn);
				return pfn;
			}
		}
		
		return path;
	}

}
