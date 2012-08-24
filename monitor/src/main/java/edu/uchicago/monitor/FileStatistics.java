package edu.uchicago.monitor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

class FileStatistics
{
	public final UUID fileId = UUID.randomUUID();
	public long filesize=-1;
	public String filename="";
	public int mode=-1;
    public final AtomicLong bytesWritten = new AtomicLong();
    public final AtomicLong bytesRead = new AtomicLong();
    public final AtomicLong reads = new AtomicLong();
    public final AtomicLong vectorReads = new AtomicLong();
    public final AtomicLong writes = new AtomicLong();
    
    @Override
    public String toString(){
    	String res="filename:\t"+filename;
    	res+="\nfileId:\t"+fileId.toString();
    	res+="\nmode:\t"+mode;
    	res+="\nsize:\t"+filesize;
    	res+="\nwrite:\t"+bytesWritten+" bytes";
    	res+="\nread:\t"+bytesRead+" bytes";
    	res+="\nreads:\t"+reads;
    	res+="\nVreads:\t"+vectorReads;
    	res+="\nwrites:\t"+writes;
    	return res;
    	
    }
    
}
