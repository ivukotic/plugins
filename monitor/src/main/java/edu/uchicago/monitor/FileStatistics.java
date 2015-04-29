package edu.uchicago.monitor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class FileStatistics {
	public final int fileCounter;
	public long filesize = -1;
	public String filename = "";
	public int mode = -1;
	public final AtomicLong bytesWritten = new AtomicLong();
	public final AtomicLong bytesVectorRead = new AtomicLong();
	public final AtomicLong bytesRead = new AtomicLong();
	public final AtomicInteger reads = new AtomicInteger();
	public final AtomicInteger vectorReads = new AtomicInteger();
	public final AtomicInteger writes = new AtomicInteger();
	public int state = 0; // bit 1- report on opening, bit 2 - report transfer,
							// 3-report file as closed and close it.

	FileStatistics(int fc) {
		this.fileCounter = fc;
	} 

	void close() {
		state |= 0x0004;
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		res.append("filename:\t").append(filename);
		res.append("\n\tmode: ").append(mode).append("\t\tsize:\t").append(filesize);
		res.append("\n\twritten:\t").append(bytesWritten).append(" bytes\t\t\tread:\t").append(bytesRead).append(
				"\t\t\t Vread:\t").append(bytesVectorRead);
		res.append("\n\treads:\t").append(reads).append("\t\t\tVreads:\t").append(vectorReads).append("\t\t\twrites:\t").append(
				writes);
		return res.toString();

	}

}
