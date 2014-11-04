package org.myprofiler4j.java.agent;

import java.util.Calendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import org.myprofiler4j.java.config.ProfConfig;
import org.myprofiler4j.java.utils.RollFileWriter;
import org.myprofiler4j.java.utils.Utilities;

public class ClassAllocationDumpThread extends Thread {

  /**
   * log writer
   */
  private RollFileWriter fileWriter;

  private static int MAX_CLASS_COUNT = 800;

  public ClassAllocationDumpThread() {
    Locale locale = Locale.getDefault();
    TimeZone timezone = TimeZone.getDefault();
    
    if(ProfConfig.instance.isTraceObjectAlloc) {
      if(fileWriter == null) { 
        fileWriter = new RollFileWriter(ProfConfig.instance.classAllocationFilePath, 512);
        String[] headContents = new String[] {
            "#Profiler Startup Time:" + Profiler.startProfileTime,
            "#Locale:" + locale,
            "#Timezone ID:" + timezone.getID(),
            "#Constructor:Total Invocation Count:Total Time Cost(ms)"
        };        
        fileWriter.setLogHeadContent(headContents);
        fileWriter.printLogHeadContent();
        fileWriter.flushAppend();
      }
    }
  }
  
  @Override
  public void run() {
    while(!isInterrupted()) {
      try {
        Thread.sleep(30000);
        if(ProfConfig.instance.isTraceObjectAlloc) {
          dumpClassAllocData(false);
        }
      } catch (InterruptedException interEx) {
        break;
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private void dumpClassAllocData(boolean isForced) {
    if(!isForced && Profiler.OBJECT_CREAT_TRACE.size() <= MAX_CLASS_COUNT) {
      return;
    }
    Iterator<ClassAllocation> iter = Profiler.OBJECT_CREAT_TRACE.values().iterator();
    try {
      fileWriter.append("#Dump timestamp:" + Utilities.getDateString(Calendar.getInstance().getTime()));
      while(iter.hasNext()) {
        ClassAllocation ca = iter.next();
        String info = ca.toString();
        if(!isForced) {
          if(Profiler.OBJECT_CREAT_TRACE.size() > MAX_CLASS_COUNT) {
            iter.remove();
            fileWriter.append(info);
          }
        } else {
          if(Profiler.OBJECT_CREAT_TRACE.size() > MAX_CLASS_COUNT) {
            iter.remove();
          } else {
            ca.reset();
          }
          fileWriter.append(info);
        }
      }//end while
      
      fileWriter.flushAppend();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void flushClassAlloc() {
    try {
      if(ProfConfig.instance.isTraceObjectAlloc) {
        dumpClassAllocData(true);
        fileWriter.flushAppend();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void exit() {
      this.interrupt();
      flushClassAlloc();
      if(fileWriter != null) {
          fileWriter.closeFile();
          fileWriter = null;
      }
  }
}
