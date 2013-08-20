package edu.uchicago.monitor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class FileStatistics
{
//	public final UUID fileId = UUID.randomUUID();
	public final int fileId;
	public long filesize=-1;
	public String filename="";
	public int mode=-1;
    public final AtomicLong bytesWritten = new AtomicLong();
    public final AtomicLong bytesVectorRead = new AtomicLong();
    public final AtomicLong bytesRead = new AtomicLong();
    public final AtomicInteger reads = new AtomicInteger();
    public final AtomicInteger vectorReads = new AtomicInteger();
    public final AtomicInteger writes = new AtomicInteger();
    public int state=0; //bit 1- report on opening, bit 2 - report transfer, 3-report file as closed and close it. 
    
    FileStatistics(int dictid){
    	fileId=dictid;
    }
    
    @Override
    public String toString(){
    	String res="filename:\t"+filename;
    	res+="\nfileId:\t"+fileId+"\t\t\tmode:\t"+mode+"\t\t\tsize:\t"+filesize;
    	res+="\nbytes written:\t"+bytesWritten+" bytes\t\t\tread:\t"+bytesRead+"\t\t\t vector read:\t"+bytesVectorRead;
    	res+="\nreads:\t"+reads+"\t\t\tVreads:\t"+vectorReads+"\t\t\twrites:\t"+writes;
    	return res;
    	
    }
    
}
