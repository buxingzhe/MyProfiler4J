package org.myprofiler4j.java.agent;

import java.security.MessageDigest;
import java.util.Map;

import org.myprofiler4j.java.config.ProfConfig;
import org.myprofiler4j.java.utils.LRU;
import org.myprofiler4j.java.utils.RollFileWriter;
import org.myprofiler4j.java.utils.Utilities;

/**
 * SQL profile data dump thread class
 * 
 * @author nhuang
 *
 */
public class SQLProfileDataDumpThread extends Thread{

  private RollFileWriter sqlDetailfileWriter;
  
  private RollFileWriter sqlProfDumpWriter;

  private Map<String, String> sqlUUIDMap = new LRU<String, String>(5000);
  
  private int id = 1;

  private MessageDigest digest = null;
  
  private StringBuilder builder = new StringBuilder(70);
  
  public SQLProfileDataDumpThread() {
    if(sqlDetailfileWriter == null) {
      sqlDetailfileWriter = new RollFileWriter(ProfConfig.instance.sqlDetailFilePath, 1 << 11);
      String[] headContents = new String[] {
          "#SQL detail file",
          "#SQL ID:SQL statement"
      };        
      sqlDetailfileWriter.setLogHeadContent(headContents);
      sqlDetailfileWriter.printLogHeadContent();
      sqlDetailfileWriter.flushAppend();
    }
    
    if(sqlProfDumpWriter == null) {
      sqlProfDumpWriter = new RollFileWriter(ProfConfig.instance.sqlDumpFilePath, 1 << 11);
      String[] headContents = new String[] {
          "#SQL profile dump file",
          "#Thread ID:Method ID:Time cost(ms):SQL ID"
      };
      sqlProfDumpWriter.setLogHeadContent(headContents);
      sqlProfDumpWriter.printLogHeadContent();
      sqlProfDumpWriter.flushAppend();
    }
    
    try {
      digest = MessageDigest.getInstance("MD5");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  @Override
  public void run() {
    String record = null;
    try {
      while((record = Profiler.SQL_PROFILE_QUEUE.take()) != null) {
        int idx = record.indexOf(Profiler.SEP);
        idx = record.indexOf(Profiler.SEP, idx + 1);
        idx = record.indexOf(Profiler.SEP, idx + 1);
        String preCont = record.substring(0, idx);
        String sql = record.substring(idx + 1);
        
        byte[] md5Bytes = digest.digest(Utilities.toBytes(sql));
        String digestMsg = toString(md5Bytes);
        String ID = sqlUUIDMap.get(digestMsg);
        if(ID == null) {
          ID = String.valueOf(id++);
          sqlUUIDMap.put(digestMsg, ID);
          sqlDetailfileWriter.append(ID + Profiler.SEP + sql);
          sqlDetailfileWriter.flushAppend();
        }
        sqlProfDumpWriter.append(preCont + Profiler.SEP + ID);
        sqlProfDumpWriter.flushAppend();
      }
    } catch (InterruptedException e) {
      return;
    }
  }
  
  public void exit() {
    try {
      this.interrupt();
      
      if(sqlDetailfileWriter != null) {
        sqlDetailfileWriter.closeFile();
        sqlDetailfileWriter = null;
      }
      if(sqlProfDumpWriter != null) {
        sqlProfDumpWriter.closeFile();
        sqlProfDumpWriter = null;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private String toString(byte[] by) {
    builder.setLength(0);
    int len = by.length;
    for(int i = 0; i < len - 1; i++) {
      builder.append(by[i] + ",");
    }
    builder.append(by[len - 1]);
    return builder.toString();
  }
}
