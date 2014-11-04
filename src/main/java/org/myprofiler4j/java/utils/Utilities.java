/**
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */

package org.myprofiler4j.java.utils;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Profiler utility class
 *
 * @author nhuang
 * @since 2013-11-28
 */
public class Utilities {
  private static final int CLASS_MAGIC = 0xCAFEBABE;
  private final static DateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  private final static SimpleDateFormat sdf = new SimpleDateFormat("'_'yyyyMMdd'_'HHmmss");  
  private final static Charset UTF8_CHARSET = Charset.forName("UTF-8");
  private final static CharsetDecoder UTF8_DECODER = UTF8_CHARSET.newDecoder();
  private static Timer TIMER = null;
  private static String PROFILER_HOME;
  /* private static final String IPADDRESS_PATTERN = 
      "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
          "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
          "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
          "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";*/
  //public static final Pattern IP_PATTERN   = Pattern.compile(IPADDRESS_PATTERN);
  //public static final Pattern CTRL_PATTERN = Pattern.compile("\\cC");
  public static final String PROCESS_NAME = ManagementFactory.getRuntimeMXBean().getName();;
  public static final String LINE_SEPARATOR = System.getProperty("line.separator");
  public static final String FILE_SEPARATOR = System.getProperty("file.separator");

  private Utilities() {
  }

  static {
    initEnv();
  }

  private static void initEnv() {
    if(PROFILER_HOME == null) {
      try {
        String filePath = Utilities.getCodeBasePath(Utilities.class);
        int index = filePath.lastIndexOf(Utilities.FILE_SEPARATOR);
        String libDir = filePath.substring(0, index);
        index = libDir.lastIndexOf(Utilities.FILE_SEPARATOR);
        PROFILER_HOME = filePath.substring(0, index);
      } catch (SecurityException e) {
        PROFILER_HOME = System.getProperty("user.home");
      }
    }
  }

  public static String getProfilerHomePath() {
    return PROFILER_HOME;
  }

  /**
   * Variable replacement,Replace the variables like ${user.home} with the real value.
   * @param source string, e.x. ${user.dir}/${user.language}/tprofiler.log
   * @param context, to find the real value
   * @return final string
   * @throws VariableNotFoundException
   */
  public static String replaceVariables(String source, Map<Object, Object> context) throws Exception{
    if (source == null){
      throw new IllegalArgumentException("source can't be null");
    }

    if (context == null){
      throw new IllegalArgumentException("context can't be null");
    }

    int p = source.lastIndexOf('}');
    while (p != -1){
      int p1 = source.lastIndexOf("${");
      //No match found
      if (p1 == -1){
        return source;
      }

      String key = source.substring(p1 + 2, p);
      if (!context.containsKey(key)){
        throw new NoSuchFieldException("variable " + key + " not found");
      }
      String value = String.valueOf(context.get(key));
      String start = source.substring(0, p1);
      String end = source.substring(p + 1);
      source = start + value + end;
      p = source.lastIndexOf('}');
    }

    return source;
  }

  public static String getDateString(Date date) {
    return DATE_FORMATTER.format(date);
  }

  public static String generateTxtFileName(String filePath, Date date) {
    if(date == null) {
      date = Calendar.getInstance().getTime();
    }
    return filePath + "_" + Utilities.PROCESS_NAME + sdf.format(date) + ".txt";
  }

  public static String toString(ByteBuffer buffer) {
    try {
      CharBuffer charBuffer = UTF8_DECODER.decode(buffer);
      return charBuffer.toString();
    } catch (Exception e) {
      return e.getMessage();
    }
  }

  public static String toString(byte[] byteArray) {
    return new String(byteArray, UTF8_CHARSET);
  }

  public static byte[] toBytes(String message) {
    if(message == null) {
      return null;
    }
    return message.getBytes(UTF8_CHARSET);
  }

  public static String generateStackFileName() {
    return "threadstack_" + PROCESS_NAME + sdf.format(Calendar.getInstance().getTime()) + ".txt";
  }

  public static String getCodeBasePath(Class<?> clz) {
    File file = new File(clz.getProtectionDomain().getCodeSource().getLocation().getPath());
    String filePath = file.getAbsolutePath();
    if(filePath.endsWith(FILE_SEPARATOR)) {
      filePath = filePath.substring(0, filePath.length() - 1);
    }
    return filePath;
  }

  public static byte[] readFile(File file, int offset, int len) throws IOException {
    DataInputStream dataInput = null;
    byte[] bytes;
    try {
      dataInput = new DataInputStream(new FileInputStream(file));
      int fileLen = (int) file.length();
      if(offset < 0) {
        offset = 0;
      }
      if(len <= 0 || (len + offset) > fileLen) {
        // Get the size of the file
        len = fileLen - offset;
      }
      
      // Create the byte array to hold the data
      bytes = new byte[(int)len];
      if(offset > 0) {        
        dataInput.skipBytes(offset);
      }
      dataInput.readFully(bytes);
    }
    finally {
      // Close the input stream and return bytes
      if(dataInput != null){
        try {
          dataInput.close();
        } catch (Exception e) {}
      }
    }
    return bytes;
  }

  public static String getClassFQN(InputStream inputStream) throws IOException {
    DataInputStream in = null;
    try {
      in = new DataInputStream(inputStream);
      if(in.readInt() != CLASS_MAGIC) {
        // Not a class file
        throw new IOException("Not a class file");
      }
      in.readUnsignedShort();// Minor version
      in.readUnsignedShort();// Major version
      in.readUnsignedShort();// length
      in.readByte();// CLASS=7
      in.readUnsignedShort();//Skip
      in.readByte();// UTF8=1
      String cn = in.readUTF();//class name
      return cn.replace('/', '.');
    } finally {
      if(in != null){
        try {
          in.close();
        } catch (Exception e) {}
      }
    }
  }

  /*public static boolean validIPAddress(String ipAddress) {
    Matcher matcher = IP_PATTERN.matcher(ipAddress);
    return matcher.matches();
  }*/

  public static synchronized void scheduleTask(TimerTask task, long delay, long period) {
    if(TIMER == null) {
      TIMER = new Timer("Thread-dumpTimer", true);
    }
    TIMER.schedule(task, delay, period);
  }
}
