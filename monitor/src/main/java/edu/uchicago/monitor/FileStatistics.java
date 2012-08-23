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
    
}
