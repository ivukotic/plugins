package edu.uchicago.monitor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.dcache.xrootd.protocol.messages.AbstractResponseMessage;
import org.dcache.xrootd.protocol.messages.CloseRequest;
import org.dcache.xrootd.protocol.messages.GenericReadRequestMessage.EmbeddedReadRequest;
import org.dcache.xrootd.protocol.messages.LoginRequest;
import org.dcache.xrootd.protocol.messages.LoginResponse;
import org.dcache.xrootd.protocol.messages.OpenRequest;
import org.dcache.xrootd.protocol.messages.OpenResponse;
import org.dcache.xrootd.protocol.messages.ReadRequest;
import org.dcache.xrootd.protocol.messages.ReadVRequest;
import org.dcache.xrootd.protocol.messages.XrootdRequest;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;

public class MonitorChannelHandler extends SimpleChannelHandler
{
    
    private final Collector collector;
    private final Map<Integer, FileStatistics> descriptors=new HashMap<Integer, FileStatistics>();
    private final UUID connectionId = UUID.randomUUID();
    private int connId=0;
    private long duration;
    
    public MonitorChannelHandler(Collector aCollector){
        collector = aCollector;
    }
    
    @Override
        public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
        throws Exception
    {
        if (e instanceof MessageEvent) {
            
            MessageEvent me=(MessageEvent) e;
            System.out.println("REQ: " + me.toString());
            Object message=me.getMessage();
     
            if (message instanceof ReadRequest){
            	ReadRequest rr= (ReadRequest) message;
            	FileStatistics fs=descriptors.get(rr.getFileHandle());
            	fs.bytesRead.getAndAdd(rr.bytesToRead());
            	fs.reads.getAndIncrement();
            	collector.totBytesRead.getAndAdd(rr.bytesToRead());
            }
            
            else

            if (message instanceof ReadVRequest){
            	ReadVRequest rr= (ReadVRequest) message;
            	EmbeddedReadRequest[] err=rr.getReadRequestList();
            	Map<Integer, Integer> fts=new HashMap<Integer, Integer>();
            	for (int i=0;i<rr.NumberOfReads();i++){
            		Integer fh=err[i].getFileHandle();
            		Integer val=fts.get(fh);
            		if (val==null){
            			fts.put(fh, 0);
            		}else{
            			val+=err[i].BytesToRead();
            		}
            	}
            	int totVread=0;
            	for (Map.Entry<Integer, Integer> entry : fts.entrySet()){
            		FileStatistics fs=descriptors.get(entry.getKey());
            		if (fs!=null){
            			fs.bytesRead.getAndAdd( entry.getValue() );
                    	fs.reads.getAndIncrement();
                    	totVread+=entry.getValue();
            		}
            	}
            	collector.totBytesRead.getAndAdd(totVread);
            }
                
            else
                	
            if (message instanceof LoginRequest){
                LoginRequest lr = (LoginRequest) message;
                int protocol = lr.getClientProtocolVersion();
                System.out.println("login request: client protocol"+protocol);
            }


            // from OpenRequest we get: 
            // mode (true -> readonly, false -> new, append, update, open deleting existing)
            // filename
            else if (message instanceof OpenRequest){
                OpenRequest or=(OpenRequest)message;
                int mode = 1; 
                if (or.isReadOnly()) mode=1; else mode=0;         // not correct
                System.out.println("FILE OPEN EVENT -------------------- stream Id: "+or.getStreamId());
                System.out.println("connUUID:   "+connectionId.toString());
                System.out.println("connId:     "+connId);
                System.out.println("path:     "+ or.getPath());
                System.out.println("readonly: " + mode);
                FileStatistics fs=new FileStatistics( );
                fs.filename=or.getPath();
                fs.mode=mode;
                descriptors.put(or.getStreamId(), fs);
                System.out.println("------------------------------------");
            }
            else if (message instanceof CloseRequest){
                CloseRequest cr=(CloseRequest)message;
                System.out.println("FILE CLOSE EVENT --------------------");
                System.out.println("connUUID:   "+connectionId.toString());
                System.out.println("connId:     "+connId);
                collector.closeEvent(connectionId, descriptors.get(cr.getFileHandle()));
                descriptors.remove(cr.getFileHandle());
                System.out.println("------------------------------------");
            }

            else if (message instanceof XrootdRequest) {
                XrootdRequest req = (XrootdRequest) message;
                System.out.println("I-> streamID: "+req.getStreamId()+ "\treqID: "+req.getRequestId());
            } 
        }
        else if(e instanceof ChannelStateEvent){
            ChannelStateEvent se=(ChannelStateEvent)e;
            System.out.println("Channel State Event UP : " + se.getState().toString());// +"\t"+ se.getValue().toString());
            if (se.getState()==ChannelState.OPEN && se.getValue()!=null){
                if (se.getValue()==Boolean.TRUE){
                    System.out.println("CONNECTION OPEN ATTEMPT EVENT --------------------");
                    collector.addConnectionAttempt();
                    System.out.println("---------------------------------------");
                }
            }
            else 
            if (se.getState()==ChannelState.CONNECTED && se.getValue()==null){
                System.out.println("DISCONNECT EVENT --------------------");
                duration=System.currentTimeMillis()-duration;
                System.out.println("connID:             "+connectionId.toString());
                System.out.println("connId:             "+connId);
                System.out.println("connection duration:"+ duration);
                collector.disconnectedEvent(connectionId, duration);
                System.out.println("------------------------------------");
                System.out.println(collector.toString());
            }
            // else System.out.println(se.getValue().toString());
        }
        else if(e instanceof WriteCompletionEvent){
            // System.out.println(" write completed UP");
        }
        else{
            System.out.println("not an up message?"+e.toString());
        }
        super.handleUpstream(ctx, e);
    }







    @Override
        public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
        throws Exception
    {
        if (e instanceof MessageEvent) {
            
            MessageEvent me=(MessageEvent) e;
            System.out.println("RES: " + me.toString());
//            System.out.println("remote address: " + me.getRemoteAddress());
            Object message=me.getMessage();
            connId=me.getChannel().getId();
//            System.out.println("connId:         " + connId);
//            System.out.println("message:        " + message.toString());

            	if (message instanceof LoginResponse){
//                LoginResponse lr = (LoginResponse) message;
                // System.out.println("reqID: " + lr.getRequest().getStreamId());
                // int [] sessionID= new int[2];
                //  ChannelBuffer ssid = lr.getBuffer();
                //  StringBuffer hexString = new StringBuffer();
                //  System.out.println("buffer:"+ssid.toString("ISO-8859-1"));
                // for (int i=8;i<16;i++) {
                //     System.out.println("byte: "+i+"\t value: "+ssid.getByte(i));
                //     hexString.append(Integer.toHexString(0xFF & ssid.getByte(i) ));
                // }
                // 
                // for (int j=2;j<4;j++){
                //     sessionID[j-2]=0;
                //     for (int i = j*4; i < (j+1)*4; i++) {
                //         int shift = (4 - 1 - i) * 8;
                //         sessionID[j-2] += (ssid.getByte(i) & 0x000000FF) << shift;
                //     }
                // }
                // System.out.println("ssid : H  "+hexString);
                // System.out.println(Arrays.toString(sessionID));
                
                duration = System.currentTimeMillis();
                System.out.println("CONNECT EVENT ----------------------");
                System.out.println("connUUID:   "+connectionId.toString());
                System.out.println("connId:     "+connId);
                System.out.println("host ip:    " + e.getChannel().getRemoteAddress());
                collector.connectedEvent(connectionId, e.getChannel().getRemoteAddress());
                System.out.println("------------------------------------");
            }
            else if (message instanceof OpenResponse){
            	OpenResponse OR=(OpenResponse) message;
            	Integer streamID=OR.getRequest().getStreamId();
            	System.out.println("FILE OPEN RESPONSE ---------------------------- stream Id: "+streamID);
            	System.out.println("filehandle: "+ OR.getFileHandle());
//            	System.out.println("filesize  : "+ OR.getFileStatus().getSize());
//            	System.out.println("fileid    : "+ OR.getFileStatus().getId());
//            	System.out.println("filestatus: "+ OR.getFileStatus().toString());
            	FileStatistics fs=descriptors.get(streamID);
            	if (fs==null){
            		System.err.println("Serious problem: can not find file with handle " +streamID);
            	}
            	fs.filesize=OR.getFileStatus().getSize();
            	descriptors.put(OR.getFileHandle(), fs);
            	descriptors.remove(streamID);
            	collector.openEvent(connectionId, fs);
            	System.out.println("-----------------------------------------------");
            }
            else if ( message instanceof AbstractResponseMessage) {
//                AbstractResponseMessage ARM=(AbstractResponseMessage) message;
//                XrootdRequest req = ARM.getRequest();
//                System.out.println("I-> streamID: "+req.getStreamId()+ "\treqID: "+req.getRequestId());
//                System.out.println("IB->: "+ (ARM.getBuffer()).toString("UTF-8") ) ;

            }
        }
        else if(e instanceof ChannelStateEvent){
            ChannelStateEvent se=(ChannelStateEvent)e;
            System.out.println("Channel State Event DOWN : " + se.getState().toString()  +"\t"+ se.getValue().toString() );
        }
        else if(e instanceof WriteCompletionEvent){
            // System.out.println(" write completed DOWN");
        }
        else{
            System.out.println("not a down message?"+e.toString());
        }
        super.handleDownstream(ctx, e);
    }
    
    
    
}
