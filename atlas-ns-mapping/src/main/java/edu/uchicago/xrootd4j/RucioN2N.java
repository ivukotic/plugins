package edu.uchicago.xrootd4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class RucioN2N {

	final static Logger log = LoggerFactory.getLogger(RucioN2N.class);

	private final Map<String, AtomicInteger> cmap = new ConcurrentHashMap<String, AtomicInteger>();
	private int nSpaceTokens;
	private AtomicInteger filesFound = new AtomicInteger();
	private AtomicInteger filesLookedFor = new AtomicInteger();

	public RucioN2N(Properties properties) {

		log.info("Setting up RUCIO access...");

		String overwriteSE = properties.getProperty("overwriteSE");
		if (overwriteSE == null) {
			log.info("Does not overwrite StorageTokens.");
		} else {
			log.info("Will overwriteSE according to content of: " + overwriteSE);
			String[] sts = overwriteSE.split(",");

			for (String s : sts) {
				cmap.put(s, new AtomicInteger());
			}

			if (cmap.isEmpty()) {
				log.error("nothing found it overwriteSE. Will neglect it.");
			} else
				return;
		}

		String SITE = properties.getProperty("site");
		if (SITE == null) {
			log.error("*** Error: SITE parameter not defined. Please set it (in etc/dcache.conf) and restart the server. Format is e.g. site=MWT2");
			throw new IllegalArgumentException(
					"SITE parameter not defined. Please set it (in etc/dcache.conf) and restart the server. Format is e.g. site=MWT2");
		} else {
			log.info("Setting SITE to: " + SITE);
		}

		JSONArray json;
		try {
			json = readJsonFromUrl("http://atlas-agis-api.cern.ch/request/service/query/get_se_services/?json&flavour=XROOTD");
			log.debug(json.toString());
			for (int ind = 0; ind < json.length(); ind++) {
				JSONObject site = json.getJSONObject(ind);
				String rc_name = site.getString("rc_site");
				if (rc_name.equals(SITE)) {
					log.info("get site: " + rc_name);
					JSONObject aprotocols = site.getJSONObject("aprotocols");
					JSONArray readonly = aprotocols.getJSONArray("r");
					for (int apa = 0; apa < readonly.length(); apa++) {
						JSONArray space_token = readonly.getJSONArray(apa);
						log.debug("space_token: " + space_token);
						String st = space_token.getString(2);
						log.info("adding space token: " + st);
						cmap.put(st, new AtomicInteger());
					}
				}
			}
		} catch (IOException e) {
			log.error("IOException when reading space_token info from AGIS.");
			e.printStackTrace();
		} catch (JSONException e) {
			log.error("JSONException when reading space_token info from AGIS.");
			e.printStackTrace();
		}

		if (cmap.size() == 0) {
			log.warn("Fallback to reading from backup site.");

			try {
				JSONArray sts = readJsonFromUrl("http://ivukotic.web.cern.ch/ivukotic/dropbox/space_tokens.json");

				for (int ind = 0; ind < sts.length(); ind++) {
					JSONObject site = sts.getJSONObject(ind);
					if (site.getString("rc_site").equals(SITE)) {
						JSONArray aprotocols = site.getJSONArray("aprotocols");
						log.debug("aprotocols: " + aprotocols);
						for (int apa = 0; apa < aprotocols.length(); apa++) {
							String st = aprotocols.getString(apa);
							log.info("adding space token: " + st);
							cmap.put(st, new AtomicInteger());
						}
					}
				}
			} catch (IOException e) {
				log.error("IOException when reading space_token info from backup site.");
				e.printStackTrace();
			} catch (JSONException e) {
				log.error("JSONException when reading space_token info from backup site.");
				e.printStackTrace();
			}

		}

		printCounts();

		// this won't be changed
		nSpaceTokens = cmap.size();

		log.info("Rucio setup properly.");
	}

	public static JSONArray readJsonFromUrl(String url) throws IOException, JSONException {
		InputStream is = new URL(url).openStream();
		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
			StringBuilder sb = new StringBuilder();
			int cp;
			while ((cp = rd.read()) != -1) {
				sb.append((char) cp);
			}
			String jsonText = sb.toString();
			JSONArray json = new JSONArray(jsonText);
			return json;
		} finally {
			is.close();
		}
	}

	public String translate(String gLFN) {

		filesLookedFor.incrementAndGet();
		log.debug("got to translate: {}", gLFN);
		String name = gLFN.substring(6);
		log.debug("removed /atlas: {}", name);
		String fileName, scope, scope_fileName;
		if (name.contains(":")) {
			fileName = name.substring(name.lastIndexOf(":") + 1);
			scope = name.substring(7, name.lastIndexOf(":"));
		} else {
			fileName = name.substring(name.lastIndexOf("/") + 1);
			scope = name.substring(7, name.lastIndexOf("/"));
		}

		if (fileName == null || scope == null) {
			log.error("Error in extracting scope and/or filename.");
			return null;
		}

		// log.info("scope: {}\tfilename: {}" ,scope, fileName);

		scope_fileName = scope + ":" + fileName;
		log.debug("scope+filename: " + scope_fileName);
		
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			log.error("can't get MD5 algorithm.");
			e.printStackTrace();
			return null;
		}
		
		String hashtext = "";
		try {
			scope_fileName = scope_fileName.replaceAll("/", ".");
			log.debug("scope+filename for md5: " + scope_fileName);
			byte[] bytesOfMessage = scope_fileName.getBytes("US-ASCII");
			byte[] thedigest = md.digest(bytesOfMessage);
			BigInteger bigInt = new BigInteger(1, thedigest);
			hashtext = bigInt.toString(16);
			while (hashtext.length() < 32) {
				hashtext = "0" + hashtext;
			}
		} catch (UnsupportedEncodingException e) {
			log.error("gLFN is not in US-ASCII format!");
			e.printStackTrace();
		}

		log.debug(hashtext);
		name = "/rucio/" + scope + "/" + hashtext.substring(0, 2) + "/" + hashtext.substring(2, 4) + "/" + fileName;

		log.info("expanded filename: " + name);

		log.debug("sorting space tokens.");

		HashSet<String> found = new HashSet<String>();

		for (int i = 0; i < nSpaceTokens; i++) {
			String maxKey = "";
			int maxValue = -1;
			for (Map.Entry<String, AtomicInteger> entry : cmap.entrySet()) {
				if (entry.getValue().longValue() >= maxValue && !found.contains(entry.getKey())) {
					maxValue = entry.getValue().intValue();
					maxKey = entry.getKey();
				}
			}
			log.debug("st -> {}  \t{}", maxValue, maxKey);
			found.add(maxKey);
			String fullName = maxKey + name;
			log.debug("looking for: {}", fullName);
			Path path = Paths.get(fullName);
			if (Files.exists(path)) {
				log.info("found at:" + maxKey);
				cmap.put(maxKey, new AtomicInteger(cmap.get(maxKey).incrementAndGet()));
				if (filesFound.incrementAndGet() % 1000 == 0)
					printCounts();
				return fullName;
			}

		}

		return null;
	}

	public void printCounts() {
		for (Map.Entry<String, AtomicInteger> entry : cmap.entrySet()) {
			log.info("space token: {}  value  {}", entry.getKey(), entry.getValue());
		}
	}
}
