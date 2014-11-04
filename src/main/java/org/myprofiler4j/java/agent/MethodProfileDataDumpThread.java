package org.myprofiler4j.java.agent;

import java.util.Locale;
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.myprofiler4j.java.config.ProfConfig;
import org.myprofiler4j.java.utils.RollFileWriter;

public class MethodProfileDataDumpThread extends Thread {
  
  /**
   * log writer
   */
  private RollFileWriter fileWriter;
  
  private RollFileWriter methodInfoWriter;
  
  //private int counter = 0;
  
  public MethodProfileDataDumpThread() {
    Locale locale = Locale.getDefault();
    TimeZone timezone = TimeZone.getDefault();
    
    if(fileWriter == null) {
      fileWriter = new RollFileWriter(ProfConfig.instance.logFilePath, 1024);
      String[] headContents = new String[] {
          "#Profiler Startup Time:" + Profiler.startProfileTime,
          "#Locale:" + locale,
          "#Timezone ID:" + timezone.getID(),
          "#Thread ID:Stack Depth:Method ID:Concurrency Call Flag:Enter method time-offset(ms):Time Cost(ms)",
          "#Concurrency Call Flag:",
          "#  N - Normal case",
          "#  M - Synchronized monitor case",
          "#  W - java.lang.Object.wait(long) case",
          "#  L - java.util.concurrent.locks.Lock.lock() case",
          "#  C - java.util.concurrent.locks.Condition.await(long) case",
          "#  T - java.util.concurrent.locks.Lock.tryLock(java.util.concurrent.TimeUnit) case",
          "#  J - java.lang.Thread.join() case",
          "#  S - java.lang.Thread.sleep(long) case"
      };
      fileWriter.setLogHeadContent(headContents);
      fileWriter.printLogHeadContent();
      fileWriter.flushAppend();
      MethodInfo.class.getClass();
    }
    
    if(methodInfoWriter == null) {
      methodInfoWriter = new RollFileWriter(ProfConfig.instance.methodFilePath, 128);
      String[] headContents = new String[] {
          "#Profiler Startup Time:" + Profiler.startProfileTime,
          "#Locale:" + locale,
          "#Timezone ID:" + timezone.getID(),
          "#Method ID:<Class Name>.<Method Name>"
      };
      methodInfoWriter.setLogHeadContent(headContents);
      methodInfoWriter.printLogHeadContent();
      methodInfoWriter.flushAppend();
    }
  }
  
  @Override
  public void run() {
    while(!isInterrupted()) {
      try {
        dumpMethodProfileData();
      }  catch (InterruptedException interEx) {
    	  break;
      } catch (Exception e) {
        e.printStackTrace();
        fileWriter.flushAppend();
      }
    }
  }
  
  private void dumpMethodProfileData() throws Exception{
    String data = null;
    while((data = Profiler.METHOD_PROFILE_QUEUE.take()) != null) {
      handleProfileData(data);
    }
  }
  
  private void flush() {
    String data = null;
    while((data = Profiler.METHOD_PROFILE_QUEUE.poll()) != null) {
      handleProfileData(data);
    }
    fileWriter.append("#Total instrumented classes:" + Profiler.instrumentClassCount.get());
    fileWriter.append("#Total instrumented methods:" + Profiler.instrumentMethodCount.get());
    fileWriter.flushAppend();
  }

  private void handleProfileData(String data) {
    if(data.charAt(0) == '#') {
      fileWriter.append(data);
      return;
    }
    MethodInfo mi;
    StringTokenizer stn = new StringTokenizer(data, Profiler.SEP);
    int i = 0;
    String methodDesc = null;
    while(stn.hasMoreTokens()) {
      String nextStr = stn.nextToken();
      if(i == 2) {
        methodDesc = nextStr;
        break;
      }
      i++;
    }
    
    if(methodDesc == null) return;

    mi = MethodInfo.getMethodInfo(methodDesc);
    if(mi == null || mi.methodId <= 0) {
      if(mi == null) {
        mi = MethodInfo.putInCache(methodDesc);
      }
      mi.initMethodId();
      methodInfoWriter.append(mi.toString());
      methodInfoWriter.flushAppend();
    }
    
    fileWriter.append(data.replace(methodDesc, String.valueOf(mi.methodId)));
    fileWriter.flushAppend();
    /*if(++counter >= 80) {
      fileWriter.flushAppend();
      counter = 0;
    }*/
  }
  
  public void flushAllData() {
    try {      
      flush();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  public void exit() {
    try {
      this.interrupt();
      flushAllData();
      if(fileWriter != null) {
        fileWriter.closeFile();
        fileWriter = null;
      }
      if(methodInfoWriter != null) {
        methodInfoWriter.closeFile();
        methodInfoWriter = null;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
