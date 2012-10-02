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

public class AtlasAuthorizationHandler implements AuthorizationHandler
{

    public URI lfcUri = null;
    
    private LFCConfig config = null; 
    private String LFC_HOST="";
    private String SRM_HOST="";

    String proxyFile = null;
    
    public AtlasAuthorizationHandler(Properties properties){
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
    

    public GlobusCredential getValidProxy() {
        GlobusCredential cred = null;

        // custom proxy
        if (proxyFile != null){
            System.out.println("Using proxy from:" + proxyFile);
            try{
            	cred = new GlobusCredential(proxyFile);
            }catch(GlobusCredentialException e){
            	System.err.println("*** Error: problem when getting credential from file: " + proxyFile);
				e.printStackTrace();
                System.exit(1);
            }
        }
        else{
            System.out.println("Using default proxy file.");
            try {
				cred = GlobusCredential.getDefaultCredential();
			} catch (GlobusCredentialException e) {
            	System.err.println("*** Error: problem when getting credential from file: " + proxyFile);
				e.printStackTrace();
                System.exit(1);
			}
        }

        if (cred == null){
            System.err.println("Couldn't find valid proxy");
        	System.exit(1);
    	}
        
        if (cred.getTimeLeft() <= 0){
            System.err.println("Expired Credential detected.");
        	System.exit(1);
    	}

        System.out.println("proxy timeleft=" + cred.getTimeLeft());

        return cred;
    }


    
    
    @Override
    public String authorize(Subject subject,
                            InetSocketAddress localAddress,
                            InetSocketAddress remoteAddress,
                            String path,
                            Map<String, String> opaque,
                            int request,
                            FilePerm mode)
            throws SecurityException, GeneralSecurityException
    {


        if (path.startsWith("pnfs/")){
                return path;
        }
    
        String LFN=path;
        if (!LFN.startsWith("/atlas/") ){
                System.err.println("*** Error: LFN must start with /atlas/. ");
                return "";
        }
        
        if (config.globusCredential.getTimeLeft() <= 60) getValidProxy();
        
        String sLFN="lfn://grid" + LFN;
        LFN = "lfn://" + LFC_HOST+ "//grid" + LFN;
        
        System.out.println("GOT to translate: " + LFN);
        
        if (config!=null){   // access through API
            
            try{
                lfcUri = new URI(LFN);
            } catch (URISyntaxException e){
                e.printStackTrace();
                System.err.println("*** Error: Invalid URI:" + LFN);
                return "";
            }
            System.out.println("Is proper url: " + LFN);
            
            
            System.out.println("creating LFC server.");
            LFCServer lfcServer;
            try{
                lfcServer = new LFCServer(config, lfcUri);
                System.out.println("LFC server created.");
            }catch (Exception e){
                e.printStackTrace();
                System.err.println("*** Could not connect to LFC. Giving up.");
                return "";
            }
        
            String guid="";
            int gu=LFN.lastIndexOf("GUID=");
        
       
            if (gu>0) {
                    guid=LFN.substring(gu+5);
                    if (guid.length()!=36){
                            System.err.println("*** Error: GUID has to have 36 characters. 32 hex numbers and 4 minuses");
                            return "";
                    }
            }
            else{
                    FileDesc entry=new FileDesc();
                    try{
                            entry = lfcServer.fetchFileDesc(lfcUri.getPath());
                    }catch(Exception e){
//                            e.printStackTrace();
                            System.err.println("*** Can't get file description for entry: " + lfcUri.getPath());
                    }
                    
                    if (entry.getGuid()==null){
                    	System.out.println("maybe got container and not dataset. Trying that...");
                    	entry = ifInputIsContainerDS(lfcUri.getPath(), lfcServer);
                    }

                    if (!entry.isFile()) {
                            System.err.println("*** Error: No such file or not a file.");
                            return "";
                    }
                    guid=entry.getGuid();
            }


            try{
                    System.out.println(guid);
                    ArrayList<ReplicaDesc> replicas=lfcServer.getReplicas(guid);
                    if (replicas.isEmpty()){
                            System.err.println("*** Error: No replica exists in this LFC.");
                            return "";
                    }else{
                            String PFN="";
                            System.out.println("found "+replicas.size()+" replicas.");
                            for (ReplicaDesc replica : replicas){
                                String line=replica.getSfn();
                                if (replica.getHost().equals(SRM_HOST)) {
                                    System.out.println("replica found \n "+ replica.toString());
                                    int li=line.lastIndexOf("=")+1;
                                    if (li<1) {
                                    	System.out.println("could not find = sign. looking for /pnfs");
                                    	li=line.indexOf("/pnfs/");
                                    }
                                    PFN=line.substring(li);
                                    System.out.println("PFN: " + PFN);
                                    return PFN;
                                }
                            }
                            System.err.println("*** Error: No replica coresponding to this SRM_HOST exists in this LFC.");
                            return "";
                    }
            }catch (Exception e){
                    e.printStackTrace();
                    System.err.println("*** Error: Can't get list of Replicas.");
                    return "";      
            }

        }
        
        else{ // access through lcg-lr
            
            System.out.println("Trying to use lcg-lr " + sLFN);
            ProcessBuilder pb = new ProcessBuilder("lcg-lr", sLFN, "--connect-timeout","10");
            Map<String, String> env = pb.environment();
            env.put("LFC_HOST", LFC_HOST);
            try{
                Process p = pb.start();
                InputStream is = p.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line;
                String PFN;
                while ((line = br.readLine()) != null) {
                  // System.out.println(line);
                  if (line.indexOf(SRM_HOST)==-1) continue;
                  Integer ind=line.indexOf("=")+1;
                  if (ind > 0){ //long form
                        PFN=line.substring(ind);
                        System.out.println("PFN: " + PFN);
                        return PFN;
                  }
                  ind=line.indexOf("/",6);
                  if (ind>0) { //short form
                      PFN=line.substring(ind);
                      System.out.println("PFN: " + PFN);
                      return PFN;
                      }
                   System.out.println("Could not interpret LFC name: "+line);
                   return line;  
                }
                // System.out.println("lcg-lr returned.");

            }catch(IOException e){
                System.out.println("IO Exception: "+e.getMessage());
            }
            
        }
    
        return "";
    }
    
    
    public FileDesc ifInputIsContainerDS(String path, LFCServer lfcServer){
    	FileDesc entry=new FileDesc();
        System.out.println("path -> "+path);
        path=path.replaceAll("\\b//\\b","/");
        System.out.println("stripped path -> "+path);
        
        int li=path.lastIndexOf("/");
        String fn=path.substring(li+1);
        System.out.println("filename -> "+fn);
        
        path=path.substring(0,li); // no filename
        li=path.lastIndexOf("/");
        String DSname= path.substring(li+1);
        System.out.println("dsname -> "+DSname);
        String dirname=path.substring(0,li);
        System.out.println("dir -> "+dirname);
        
        try{
        	ArrayList<FileDesc> dirsdescs= lfcServer.listDirectory(dirname);
        	System.out.println("got list of "+dirsdescs.size()+ " data sets in this container.");
        	for (FileDesc dirdesc : dirsdescs){
        		System.out.println(dirdesc.getFileName());
        		if (dirdesc.getFileName().indexOf(DSname)!=-1){
        			String newFN=dirname+"/"+dirdesc.getFileName()+"/"+fn;
        			System.out.println("found matching DS! trying: "+newFN);
                    try{
                    	return lfcServer.fetchFileDesc(newFN);
                    }catch(Exception e){
                        System.err.println("*** Not this one.");
                    }
        		}
        	}
        }catch(Exception e1){
            System.err.println("***  There is no dataset container named: " + dirname);
            return entry;
        }
    	return entry;
    }
}
