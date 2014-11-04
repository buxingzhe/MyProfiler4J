package org.myprofiler4j.java.agent;

import java.util.Locale;
import java.util.TimeZone;

import org.myprofiler4j.java.config.ProfConfig;
import org.myprofiler4j.java.utils.RollFileWriter;

public class ThreadInfoDumpThread extends Thread {

	private RollFileWriter threadInfoWriter;

	public ThreadInfoDumpThread() {
		Locale locale = Locale.getDefault();
		TimeZone timezone = TimeZone.getDefault();
		if(threadInfoWriter == null) {
			threadInfoWriter = new RollFileWriter(ProfConfig.instance.threadInfoFilePath, 10);
			String[] headContents = new String[] {
					"#Profiler Startup Time:" + Profiler.startProfileTime,
					"#Locale:" + locale,
					"#Timezone ID:" + timezone.getID(),
					"#Thread ID:Thread Name"
			}; 
			threadInfoWriter.setLogHeadContent(headContents);
			threadInfoWriter.printLogHeadContent();
			threadInfoWriter.flushAppend();
		}
		ProfilerRuntime.class.getName();
	}

	public void run() {
		while(!Thread.interrupted()) {
			try {
				ThreadInfo threadInfo = null;
				while((threadInfo = Profiler.THREAD_QUEUE.take()) != null) {
					threadInfoWriter.append(threadInfo.toString());
					threadInfoWriter.flushAppend();
				}			
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	void appendStatistics() {
		if(threadInfoWriter != null) {			
			String threadStat = "#TotalStartedThreadCount: " + ProfilerRuntime.getTotalStartedThreadCount()
					+ ", PeakThreadCount: " + ProfilerRuntime.getPeakThreadCount()
					+ ", LiveThreadCount: " + ProfilerRuntime.getLiveThreadCount();
			threadInfoWriter.append(threadStat);
			threadInfoWriter.flushAppend();
		}
	}

	public synchronized void exit() {
		if(threadInfoWriter != null) {
			appendStatistics();
			threadInfoWriter.closeFile();
			threadInfoWriter = null;
		}
		this.interrupt();
	}
}
