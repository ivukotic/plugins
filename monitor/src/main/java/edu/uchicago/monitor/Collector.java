package edu.uchicago.monitor;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.util.CharsetUtil;

public class Collector
{  

    private final int port;
    private final String address;
    private final String sitename;
    private final int delay;
    
    private  String info;
    private  long tos;
    private int pid;
    
    private DatagramChannelFactory f ;
    private ConnectionlessBootstrap b;
    
    private AtomicInteger connectionAttempts    = new AtomicInteger();
    private AtomicInteger currentConnections    = new AtomicInteger();
    private AtomicInteger successfulConnections = new AtomicInteger();
    public AtomicLong totBytesRead             = new AtomicLong();
    public AtomicLong totBytesWriten           = new AtomicLong();
    
    Timer timer = new Timer();
    
	Collector( String sitename, String address, int port, int delay){
        this.port=port;
	    this.address=address;
	    this.sitename=sitename;
	    this.delay=delay;
	    init();
    }


	private void init(){

	    info="<stats id=\"info\"><host>xxx."+sitename+"</host><port>"+port+"</port><name>anon</name></stats>";
	    tos=System.currentTimeMillis() / 1000L;
	    
        try{
        	pid = Integer.parseInt(new File("/proc/self").getCanonicalFile().getName());
        }catch(Exception e){
        	System.err.println("could not get PID from /proc/self. Setting it to 123456.");
        	pid=123456;
        }
        
        f = new NioDatagramChannelFactory(Executors.newCachedThreadPool());
	    b = new ConnectionlessBootstrap(f);
	    
        b.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(
                         new StringEncoder(CharsetUtil.ISO_8859_1),
                         new StringDecoder(CharsetUtil.ISO_8859_1),
                         new SimpleChannelUpstreamHandler());
             }
         });
        b.setOption( "receiveBufferSizePredictorFactory", new FixedReceiveBufferSizePredictorFactory(1024));

	    timer.schedule(new SendStatisticsTask(), 0, delay*1000);
	}
    
    public void addConnectionAttempt(){
        connectionAttempts.getAndIncrement();
    }
    
    public void openEvent(UUID connectionId, FileStatistics fs)
    {
        // Note that status may be null - only available if client requested it
        System.out.println(">>>Opened " + connectionId + "\n" + fs.toString());
    }

    public void closeEvent(UUID connectionId, FileStatistics fs)
    {
        System.out.println(">>>Closed " + connectionId +"\n" +fs.toString());
        
    }

    public void connectedEvent(UUID connectionId, SocketAddress remoteAddress)
    {   
        currentConnections.getAndIncrement();
        System.out.println(">>>Connected " + connectionId + " " + remoteAddress);
    }

    public void disconnectedEvent(UUID connectionId, long duration)
    {   
        currentConnections.getAndDecrement();
        successfulConnections.getAndIncrement();
        System.out.println(">>>Disconnected " + connectionId + " " + duration + "ms");
    }
    
    @Override
    public String toString(){
        String res = new String();
        res+="SUMMARY ----------------------------------\n";
        res+="Connection Attempts:     "+connectionAttempts.get()+"\n";
        res+="Current Connections:     "+currentConnections.toString()+"\n";
        res+="Connections established: "+successfulConnections.toString()+"\n";
        res+="Bytes Read:              "+totBytesRead.toString()+"\n";
        res+="Bytes Written:           "+totBytesWriten.toString()+"\n";
        res+="SUMMARY ----------------------------------\n";
        return res;
    }
    
    private class SendStatisticsTask extends TimerTask {
        public void run() {
        	long tod=System.currentTimeMillis() / 1000L - 60;
            String sgen="<stats id=\"sgen\"><as>1</as><et>60000</et><toe>"+(tod+60)+"</toe></stats>";
            String link="<stats id=\"link\"><num>1</num><maxn>1</maxn><tot>20</tot><in>"+totBytesWriten.toString()+"</in><out>"+totBytesRead.toString()+"</out><ctime>0</ctime><tmo>0</tmo><stall>0</stall><sfps>0</sfps></stats>";
            String xmlmessage="<statistics tod=\""+tod+"\" ver=\"v1.9.12.21\" tos=\""+tos+"\" src=\"xxx."+sitename+"\" pgm=\"xrootd\" ins=\"anon\" pid=\""+pid+"\">"+info+sgen+link+"</statistics>";
//            System.out.println(xmlmessage);
            send(xmlmessage);
        }
    }
    
    public void send(String res) {
    	
        DatagramChannel c = (DatagramChannel) b.bind(new InetSocketAddress(0));
        
        c.write(res, new InetSocketAddress(address, port));
        
//         will close the DatagramChannel. If the channel is not closed within 100 milliseconds, print an error message and quit.
//          if (!c.getCloseFuture().awaitUninterruptibly(100)) {
//              System.err.println("Done.");
//              c.close().awaitUninterruptibly();
//          }
//          f.releaseExternalResources();
                    
          
      } 

    
}
