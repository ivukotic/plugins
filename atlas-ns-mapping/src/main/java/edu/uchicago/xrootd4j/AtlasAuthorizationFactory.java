package edu.uchicago.xrootd4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.dcache.xrootd.plugins.AuthorizationFactory;
//import org.dcache.xrootd.plugins.AuthorizationHandler;

import nl.uva.vlet.glite.lfc.LFCConfig;
import org.globus.gsi.GlobusCredential;

public class AtlasAuthorizationFactory implements AuthorizationFactory
{
    final static String NAME = "atlas-name-to-name-plugin";
    final static Set<String> ALTERNATIVE_NAMES = new HashSet<String>(Arrays.asList(NAME));
    
    String proxyFile = null;
    private String LFC_HOST="";
    private String SRM_HOST="";
    private LFCConfig config = null;
    
    public AtlasAuthorizationFactory(Properties properties){
    	LFC_HOST=properties.getProperty("lfc_host");
		SRM_HOST=properties.getProperty("srm_host");
    	
    	if (LFC_HOST==null){
            System.err.println("*** Error: LFC_HOST parameter not defined. Please set it (in etc/dcache.conf) and restart the server.");
            System.exit(1);
        }else {
            System.out.println("Setting LFC_HOST to: "+LFC_HOST);
        }
        
        if (SRM_HOST==null){
            System.err.println("*** Error: SRM_HOST parameter not defined. Please set it (in etc/dcache.conf)  and restart the server.");
            System.exit(1);
        }else {
            System.out.println("Setting SRM_HOST to: "+SRM_HOST);
        }
        
        try{
            System.out.println("trying to get proxy...");
            config = new LFCConfig();
            config.globusCredential = getValidProxy();
        }catch (Exception e){
            e.printStackTrace();
            System.err.println("*** Can't get valid Proxy. We hope that your LFC_HOST allows for non-authenticated read-only access.");
            config=null;
        }
        
    }


    public GlobusCredential getValidProxy() throws Exception {
        GlobusCredential cred = null;

        // custom proxy
        if (proxyFile != null){
            System.out.println("Using proxy from:" + proxyFile);
            cred = new GlobusCredential(proxyFile);
        }
        else{
            System.out.println("Using default proxy file.");
            cred = GlobusCredential.getDefaultCredential();
        }

        if (cred == null)
            throw new Exception("Couldn't find valid proxy");

        if (cred.getTimeLeft() <= 0)
            throw new Exception("Expired Credential detected.");

        System.out.println("proxy timeleft=" + cred.getTimeLeft());

        return cred;
    }




    static boolean hasName(String name){
        return ALTERNATIVE_NAMES.contains(name);
    }

    @Override
    public String getName(){
        return NAME;
    }

    @Override
    public String getDescription(){
        return "ATLAS xrootd name-to-name plugin";
    }

    @Override
    public AtlasAuthorizationHandler createHandler()
    {
        return new AtlasAuthorizationHandler(LFC_HOST, SRM_HOST, config);
    }
}
