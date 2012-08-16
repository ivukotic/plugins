package edu.uchicago.xrootd4j;

import java.util.Map;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import javax.security.auth.Subject;

import org.dcache.xrootd.protocol.XrootdProtocol;
import org.dcache.xrootd.protocol.XrootdProtocol.FilePerm;
import org.dcache.xrootd.plugins.AuthorizationHandler;

import nl.uva.vlet.glite.lfc.LFCServer;
import nl.uva.vlet.glite.lfc.LFCConfig;
import nl.uva.vlet.glite.lfc.internal.FileDesc;
import nl.uva.vlet.glite.lfc.internal.ReplicaDesc;
import org.globus.gsi.GlobusCredential;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.Locale;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

public class AtlasAuthorizationHandler implements AuthorizationHandler
{

    public URI lfcUri = null;
    
    private LFCConfig config = null; 
    private String LFC_HOST="";
    private String SRM_HOST="";
    
    public AtlasAuthorizationHandler(String LH, String SH, LFCConfig conf){
        LFC_HOST=LH;
        SRM_HOST=SH;        
        config=conf;
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
        
        String sLFN="lfn://grid" + LFN;
        LFN = "lfn://" + System.getenv("LFC_HOST") + "//grid" + LFN;
        
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
                            e.printStackTrace();
                            System.err.println("*** Error: Can't get file description.");
                            return "";
                    }

                    if (!entry.isFile()) {
                            System.err.println("*** Error: Not a file.");
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
                  Integer ind=line.indexOf("/",6);
                  if (ind>0) {
                      PFN=line.substring(ind);
                      System.out.println("PFN: " + PFN);
                      return PFN;
                      }
                }
                // System.out.println("lcg-lr returned.");

            }catch(IOException e){
                System.out.println("IO Exception: "+e.getMessage());
            }
            
        }
    
        return "";
    }
    
    
}
