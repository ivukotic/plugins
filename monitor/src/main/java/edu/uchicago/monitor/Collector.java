package edu.uchicago.monitor;

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
    
    public void openEvent(UUID connectionId, FileStatistics fs)
    {
        // Note that status may be null - only available if client requested it
        System.out.println(">>>Opened " + connectionId + " " + fs.fileId + " " + fs.filename + " " + fs.filesize + " " + fs.mode);
    }

    public void closeEvent(UUID connectionId, FileStatistics fs)
    {
        System.out.println(">>>Closed " + connectionId + "\nfileid "+ fs.fileId + "\nbytesRead " + fs.bytesRead + "\n bytesWritten " +
            fs.bytesWritten + "\nreads " + fs.reads + "\nwrites  " +
            fs.writes + "\nvectorReads " + fs.vectorReads + ".");
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
