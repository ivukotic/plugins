package edu.uchicago.monitor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class FileStatistics {
	public int fileCounter;
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
		String res = "filename:\t" + filename;
		res += "\n\tmode: " + mode + "\t\tsize:\t" + filesize;
		res += "\n\twritten:\t" + bytesWritten + " bytes\t\t\tread:\t" + bytesRead + "\t\t\t Vread:\t" + bytesVectorRead;
		res += "\n\treads:\t" + reads + "\t\t\tVreads:\t" + vectorReads + "\t\t\twrites:\t" + writes;
		return res;

	}

}
