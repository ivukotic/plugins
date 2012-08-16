package edu.uchicago.monitor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

class FileStatistics
{
    public final UUID fileId = UUID.randomUUID();

    public final AtomicLong bytesWritten = new AtomicLong();
    public final AtomicLong bytesRead = new AtomicLong();
    public final AtomicLong reads = new AtomicLong();
    public final AtomicLong vectorReads = new AtomicLong();
    public final AtomicLong writes = new AtomicLong();
}
