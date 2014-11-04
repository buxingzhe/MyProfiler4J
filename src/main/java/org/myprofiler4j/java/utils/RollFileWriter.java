/**
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package org.myprofiler4j.java.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.myprofiler4j.java.agent.Profiler;

/**
 * File writer which can be rolling by file size
 * 
 * @author nhuang
 * @since 2013-11-28
 */
public class RollFileWriter {
  /**
   * File name
   */
  private String fileName;

  /**
   * BufferedWriter
   */
  private BufferedWriter bufferedWriter;

  /**
   * Log head
   */
  private String[] logHeadContent = null;

  /**
   * Counter for appending
   */
  private volatile int writtenBytes = 0;

  /**
   * Date formater
   */
  private final static SimpleDateFormat sdf = new SimpleDateFormat(
      "'_'MMddyyyy'_'HHmmss");

  private int fileIndex = 1;

  private long maxFileLength = 1 << 24; // Default file size 16M

  private int bufferSize = 1 << 13;
  
  /**
   * @param filePath
   */
  public RollFileWriter(String filePath, int bufferSize) {
    this.bufferSize = bufferSize;
    Date now = new Date(Profiler.startProfileTime);
    fileName = filePath + "_" + Utilities.PROCESS_NAME + sdf.format(now) + ".txt";
    createWriter(fileName);
  }

  /**
   * @param head
   */
  public void setLogHeadContent(String[] head) {
    logHeadContent = head;
  }

  /**
     * 
     */
  public void printLogHeadContent() {
    if (logHeadContent != null && logHeadContent.length > 0) {
      append(logHeadContent);
    }
    append("#Timestamp:"
        + Utilities.getDateString(Calendar.getInstance().getTime()));
  }

  /**
   * @param log
   */
  public void append(String[] log) {
    subappend(log);
  }

  public void append(String log) {
    try {
      checkSize();
      bufferedWriter.append(log);
      bufferedWriter.newLine();
      writtenBytes += (log.length() + 1);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void checkSize() {
    if (writtenBytes >= maxFileLength) {
      try {
        bufferedWriter.append("#Timestamp:"
            + Utilities.getDateString(Calendar.getInstance().getTime()));
        bufferedWriter.newLine();
        bufferedWriter.flush();
        rolling();
        printLogHeadContent();
      } catch (Exception e) {
        // ignored
      } finally {
        writtenBytes = 0;
      }
    }
  }

  /**
     * 
     */
  public void flushAppend() {
    if (bufferedWriter != null) {
      try {
        bufferedWriter.flush();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public void setMaxFileLength(long len) {
    this.maxFileLength = len;
  }

  /**
   * @param log
   */
  private void subappend(String[] log) {
    try {
      if (log != null && log.length > 0) {
        for (int i = 0; i < log.length; i++) {
          checkSize();
          bufferedWriter.append(log[i]);
          bufferedWriter.newLine();
          writtenBytes += (log[i].length() + 1);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
     * 
     */
  public void closeFile() {
    if (bufferedWriter != null) {
      try {
        bufferedWriter.flush();
      } catch (IOException e) {
        e.printStackTrace();
      }
      try {
        bufferedWriter.close();
      } catch (Exception e) {
        // ignored
      }
      bufferedWriter = null;
    }
  }

  /**
   * @param now
   */
  private void rolling() {
    File file = null;
    for (int i = fileIndex; i > 1; i--) {
      file = new File(fileName + "." + (i - 1));
      if (file.exists()) {
        file.renameTo(new File(fileName + "." + i));
      }
    }
    closeFile();
    File target = new File(fileName + ".1");
    file = new File(fileName);
    file.renameTo(target);

    createWriter(fileName);
    fileIndex++;
  }

  /**
   * Overwrite the older file directly
   * 
   * @param file
   */
  private void createWriter(String fileName) {
    try {
      File file = new File(fileName);
      file = file.getCanonicalFile();
      File parent = file.getParentFile();
      if (parent != null && !parent.exists()) {
        parent.mkdirs();
      }
      bufferedWriter = new BufferedWriter(new FileWriter(file), this.bufferSize); // Buffer
                                                                           // size:
                                                                           // 2K
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
