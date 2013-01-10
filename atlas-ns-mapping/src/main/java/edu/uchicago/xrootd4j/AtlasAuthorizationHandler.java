package edu.uchicago.xrootd4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;

import javax.security.auth.Subject;

import nl.uva.vlet.glite.lfc.LFCConfig;
import nl.uva.vlet.glite.lfc.LFCServer;
import nl.uva.vlet.glite.lfc.internal.FileDesc;
import nl.uva.vlet.glite.lfc.internal.ReplicaDesc;

import org.dcache.xrootd.plugins.AuthorizationHandler;
import org.dcache.xrootd.protocol.XrootdProtocol.FilePerm;
import org.globus.gsi.GlobusCredential;
import org.globus.gsi.GlobusCredentialException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtlasAuthorizationHandler implements AuthorizationHandler {

	final Logger logger = LoggerFactory.getLogger(AtlasAuthorizationHandler.class);
	public URI lfcUri = null;

	private LFCConfig config = null;
	private String LFC_HOST = "";
	private String SRM_HOST = "";

	String proxyFile = null;

	public AtlasAuthorizationHandler(Properties properties) throws IllegalArgumentException, MissingResourceException {

		LFC_HOST = properties.getProperty("lfc_host");
		SRM_HOST = properties.getProperty("srm_host");

		if (LFC_HOST == null) {
			logger.error("*** Error: LFC_HOST parameter not defined. Please set it (in etc/dcache.conf) and restart the server.");
			throw new IllegalArgumentException("LFC_HOST parameter not defined. Please set it (in etc/dcache.conf) and restart the server.");
		} else {
			logger.info("Setting LFC_HOST to: " + LFC_HOST);
		}

		if (SRM_HOST == null) {
			logger.error("*** Error: SRM_HOST parameter not defined. Please set it (in etc/dcache.conf)  and restart the server.");
			throw new IllegalArgumentException("SRM_HOST parameter not defined. Please set it (in etc/dcache.conf) and restart the server.");
		} else {
			logger.info("Setting SRM_HOST to: " + SRM_HOST);
		}
		try {
			config = new LFCConfig();
			 logger.info("trying to get proxy...");
			 config.globusCredential = getValidProxy();
		} catch (Exception e) {
			logger.info("*** Can't get valid Proxy. We hope that your LFC_HOST allows for non-authenticated read-only access and you have gLite.");
			logger.debug(e.getMessage());
			config = null;
		}
	}

	public GlobusCredential getValidProxy() {
		GlobusCredential cred = null;

		// custom proxy
		if (proxyFile != null) {
			logger.info("Using proxy from:" + proxyFile);
			try {
				cred = new GlobusCredential(proxyFile);
			} catch (GlobusCredentialException e) {
				logger.error("*** Error: problem when getting credential from file: " + proxyFile+"\n"+e.getMessage());
				throw new MissingResourceException("*** Error: problem when getting credential from file: " + proxyFile, "GlobusCredential", "");
			}
		} else {
			logger.info("Using default proxy file.");
			try {
				cred = GlobusCredential.getDefaultCredential();
			} catch (GlobusCredentialException e) {
				logger.warn("*** Can't get default proxy. "+e.getMessage());
				throw new MissingResourceException("*** Can't get default proxy.", "GlobusCredential", "");
			}
		}

		if (cred == null) {
			logger.error("Couldn't find valid proxy");
			throw new MissingResourceException("*** Error: no valid default proxy.", "GlobusCredential", "");
		}

		if (cred.getTimeLeft() <= 0) {
			logger.error("Expired Credential detected.");
			throw new MissingResourceException("*** Error: Expired Credential detected.", "GlobusCredential", "");
		}

		logger.info("proxy timeleft=" + cred.getTimeLeft());

		return cred;
	}

	@Override
	public String authorize(Subject subject, InetSocketAddress localAddress, InetSocketAddress remoteAddress, String path, Map<String, String> opaque,
			int request, FilePerm mode) throws SecurityException, GeneralSecurityException {

		logger.info("GOT to translate: " + path);

		if (path.startsWith("pnfs/")) {
			return path;
		}

		String LFN = path;
		if (!LFN.startsWith("/atlas/")) {
			logger.error("*** Error: LFN must start with /atlas/. ");
			return "";
		}

		String sLFN = "lfn://grid" + LFN;
		LFN = "lfn://" + LFC_HOST + "//grid" + LFN;

		logger.info("FINALY translating: " + LFN);

		if (config != null) { // access through API

			// if (config.globusCredential.getTimeLeft() <= 60)
			// getValidProxy();

			try {
				lfcUri = new URI(LFN);
				logger.debug("Is a proper url.");
			} catch (URISyntaxException e) {
				logger.error("*** Error: Invalid URI:" + LFN);
				logger.error(e.getMessage());
				return "";
			}

			LFCServer lfcServer;
			try {
				lfcServer = new LFCServer(config, lfcUri);
				logger.debug("LFC server created.");
			} catch (Exception e) {
				logger.error("*** Could not connect to LFC. Giving up.");
				logger.error(e.getMessage());
				return "";
			}

			String guid = "";
			int gu = LFN.lastIndexOf("GUID=");

			if (gu > 0) {
				guid = LFN.substring(gu + 5);
				if (guid.length() != 36) {
					logger.error("*** Error: GUID has to have 36 characters. 32 hex numbers and 4 minuses");
					return "";
				}
			} else {
				FileDesc entry = new FileDesc();
				try {
					entry = lfcServer.fetchFileDesc(lfcUri.getPath());
				} catch (Exception e) {
					logger.error("*** Can't get file description for entry: " + lfcUri.getPath());
					logger.error("*** Can't get file description for entry: " + e.getMessage());
				}

				if (entry.getGuid() == null && LFN.contains("/user/")) {
					logger.info("maybe this is pathena registered file. Trying that...");
					entry = ifInputIsPathenaRegistered(lfcUri.getPath(), lfcServer);
				}

				if (entry.getGuid() == null) {
					logger.info("maybe got container and not dataset. Trying that...");
					entry = ifInputIsContainerDS(lfcUri.getPath(), lfcServer);
				}

				if (!entry.isFile()) {
					logger.error("*** Error: No such file or not a file.");
					return "";
				}
				guid = entry.getGuid();
			}

			logger.debug("guid:" + guid);

			try {
				ArrayList<ReplicaDesc> replicas = lfcServer.getReplicas(guid);
				if (replicas.isEmpty()) {
					logger.info("*** Error: No replica exists in this LFC.");
					return "";
				} else {
					String PFN = "";
					logger.debug("found " + replicas.size() + " replicas.");
					for (ReplicaDesc replica : replicas) {
						String line = replica.getSfn();
						if (replica.getHost().equals(SRM_HOST)) {
							logger.debug("replica found \n " + replica.toString());
							int li = line.lastIndexOf("=") + 1;
							if (li < 1) {
								logger.debug("could not find = sign. looking for /pnfs");
								li = line.indexOf("/pnfs/");
							}
							PFN = line.substring(li);
							logger.info("PFN: " + PFN);
							return PFN;
						}
					}
					logger.error("*** Error: No replica coresponding to this SRM_HOST exists in this LFC.");
					return "";
				}
			} catch (Exception e) {
				logger.error("*** Error: Can't get list of Replicas.");
				logger.error(e.getMessage());
				return "";
			}

		}

		else { // access through lcg-lr

			logger.info("Trying to use lcg-lr " + sLFN);
			ProcessBuilder pb = new ProcessBuilder("lcg-lr", sLFN, "--connect-timeout", "10");
			Map<String, String> env = pb.environment();
			env.put("LFC_HOST", LFC_HOST);
			try {
				Process p = pb.start();
				InputStream is = p.getInputStream();
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line;
				String PFN;
				while ((line = br.readLine()) != null) {
					// logger.error(line);
					if (line.indexOf(SRM_HOST) == -1)
						continue;
					Integer ind = line.indexOf("=") + 1;
					if (ind > 0) { // long form
						PFN = line.substring(ind);
						logger.error("PFN: " + PFN);
						return PFN;
					}
					ind = line.indexOf("/", 6);
					if (ind > 0) { // short form
						PFN = line.substring(ind);
						logger.error("PFN: " + PFN);
						return PFN;
					}
					logger.error("Could not interpret LFC name: " + line);
					return line;
				}
				// logger.error("lcg-lr returned.");

			} catch (IOException e) {
				logger.error("IO Exception: " + e.getMessage());
			}

		}

		return "";
	}

	public FileDesc ifInputIsContainerDS(String path, LFCServer lfcServer) {
		FileDesc entry = new FileDesc();
		logger.debug("path -> " + path);
		path = path.replaceAll("\\b//\\b", "/");
		logger.debug("stripped path -> " + path);

		int li = path.lastIndexOf("/");
		String fn = path.substring(li + 1);
		logger.debug("filename -> " + fn);

		path = path.substring(0, li); // no filename
		li = path.lastIndexOf("/");
		String DSname = path.substring(li + 1);
		logger.debug("dsname -> " + DSname);
		String dirname = path.substring(0, li);
		logger.debug("dir -> " + dirname);

		try {
			ArrayList<FileDesc> dirsdescs = lfcServer.listDirectory(dirname);
			logger.debug("got list of " + dirsdescs.size() + " data sets in this container.");
			for (FileDesc dirdesc : dirsdescs) {
				logger.debug(dirdesc.getFileName());
				if (dirdesc.getFileName().indexOf(DSname) != -1) {
					String newFN = dirname + "/" + dirdesc.getFileName() + "/" + fn;
					logger.debug("found matching DS! trying: " + newFN);
					try {
						return lfcServer.fetchFileDesc(newFN);
					} catch (Exception e) {
						logger.error("*** Not this one.");
					}
				}
			}
		} catch (Exception e1) {
			logger.info("***  There is no dataset container named: " + dirname);
			return entry;
		}
		return entry;
	}

	public FileDesc ifInputIsPathenaRegistered(String path, LFCServer lfcServer) {
		FileDesc entry = new FileDesc();
		path = path.replaceAll("\\b//\\b", "/");
		logger.debug("stripped path -> " + path);

		path = path.replace("user", "users/pathena");
		logger.debug("filename changed to pathena one -> " + path);

		try {
			entry = lfcServer.fetchFileDesc(lfcUri.getPath());
		} catch (Exception e1) {
			logger.info("*** It did not work. ");
			return entry;
		}
		return entry;
	}

}
