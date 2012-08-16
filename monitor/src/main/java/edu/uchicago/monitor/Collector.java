package edu.uchicago.monitor;

import org.dcache.xrootd.util.FileStatus;

import java.net.SocketAddress;
import java.util.UUID;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Collector
{  
    private AtomicInteger connectionAttempts    = new AtomicInteger();
    private AtomicInteger currentConnections    = new AtomicInteger();
    private AtomicInteger successfulConnections = new AtomicInteger();
    private AtomicLong totBytesRead             = new AtomicLong();
    private AtomicLong totBytesWriten           = new AtomicLong();

    public void addConnectionAttempt(){
        connectionAttempts.getAndIncrement();
    }
    
    public void openEvent(UUID connectionId, String path, UUID fileId, FileStatus status, int options)
    {
        // Note that status may be null - only available if client requested it
        System.out.println(">>>Opened " + connectionId + " " + path + " " + fileId + " " + status + " " + options);
    }

    public void closeEvent(UUID connectionId, FileStatistics statistics)
    {
        System.out.println(">>>Closed " + connectionId + " " + statistics.bytesRead + " bytes read " +
            statistics.bytesWritten + " bytes written " + statistics.reads + " reads " +
            statistics.writes + " writes " + statistics.vectorReads + " vector reads");
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
    
    public void timedEvent(UUID connectionId)
    {
        System.out.println(">>>Timed event " + connectionId );
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
}
