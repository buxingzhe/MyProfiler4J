package org.myprofiler4j.java.client.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MethodCallStatisticsGenerator {

  private static final String NEW_LINE = System.getProperty("line.separator");

  private static String outFile = "methodAudit";

  private final static String SEP = ParserUtils.SEP;

  private static PrintWriter writer;

  private static final Map<String, MethodMetrics> METHOD_METRIC_MAP = new HashMap<String, MethodMetrics>();
  private static Map<String, String> MEHTHOD_INFO_MAP = new LinkedHashMap<String, String>();

  public static void main(String[] args) {
    if(args == null || args.length < 1) {
      System.out.println("Arguments: MethodProfileFilePath [startTime] [endTime]");
      System.out.println("Time format: MM/dd/yyyy-HH:mm:ss");
      System.exit(-1);
    }

    String profileFilePath = args[0];
    if(!ParserUtils.isMethodProfilingFile(profileFilePath)) {
      System.out.println("The profiling file path is not correct, please check.");
      System.out.println("The file name should be like methodProfile_*.txt or methodProfile_*.txt.1");
      System.exit(-1);
    }
    
    outFile = outFile + ParserUtils.getFilePostfix(profileFilePath);
    
    long startTimePoint = 0;
    long endTimePoint = Long.MAX_VALUE;
    try {
      if(args.length > 2) {
        startTimePoint = ParserUtils.getTimeInMilliseconds(args[1]);
        endTimePoint = ParserUtils.getTimeInMilliseconds(args[2]);
      } else if(args.length > 1) {
        startTimePoint = ParserUtils.getTimeInMilliseconds(args[1]);
      }
      if(endTimePoint <= startTimePoint) {
        System.out.println("Error: Start time is later than end time.");
        System.exit(0);
      }
    } catch (Exception e) {
      System.out.println("Time format error!");
      System.exit(-1);
    }
    
    
    try {
      String methodFilePath = profileFilePath.replace("methodProfile", "methodInfo");
      ParserUtils.readMethodInfo(methodFilePath, MEHTHOD_INFO_MAP, null);
      ParserUtils.initTimeZone(methodFilePath);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
    
    String fileBase = null;
    int index = 0;
    if(profileFilePath.endsWith(".txt")) {
      fileBase = profileFilePath;
    } else {
      int lastDotIndex = profileFilePath.lastIndexOf('.');
      fileBase = profileFilePath.substring(0, lastDotIndex);
      index = Integer.valueOf(profileFilePath.substring(lastDotIndex + 1));
    }
    readMethodProfileData(fileBase, index, startTimePoint, endTimePoint);

    printFileHeader();
    printMethodStatistics();
  }
  

  private static void printFileHeader() {
    try {
      if(writer == null) {
        try {
            writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(outFile))), true);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }
    Calendar calendar = Calendar.getInstance(ParserUtils.TIMEZONE, ParserUtils.LOCALE);
    calendar.setTimeInMillis(ParserUtils.STARTUP_TIME);
    writer.write("+---------------------------------------------------------------------------");
    writer.write(NEW_LINE);
    writer.write("| File: " + outFile + NEW_LINE);
    writer.write("| Date: " + new SimpleDateFormat("yyyy'.'MM'.'dd' 'HH':'mm':'ss").format(calendar.getTime()));
    writer.write(NEW_LINE);
    writer.write("+---------------------------------------------------------------------------");
    writer.write(NEW_LINE);
    writer.write(NEW_LINE);
    writer.write("====================================================================================================");
    writer.write(NEW_LINE);
    writer.write("Count  Total    Wait    Net      Avg(Wait)  Avg(Total)  Method");
    writer.write(NEW_LINE);
    writer.write("=====  =====    =====   =====    =========  ==========  ==============================");
    writer.write(NEW_LINE);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private static void printMethodStatistics() {
    try {
      MethodMetrics[] methodMetrics = METHOD_METRIC_MAP.values().toArray(new MethodMetrics[0]);
      Arrays.sort(methodMetrics);
      for(int i = 0; i < methodMetrics.length; i ++) {
        MethodMetrics m = methodMetrics[i];
        writer.printf("%-7d",       m._count);
        writer.printf("%-9d",       m._totalTime);
        writer.printf("%-8d",       m._totalWaitTime);
        writer.printf("%-9d",       m._totalTime - m._totalWaitTime);
        writer.printf("%-11.1f",    (double)(m._waitCount == 0 ? 0 : m._totalWaitTime/m._waitCount));
        writer.printf("%-12.1f",    (double)(m._totalTime/m._count));
        writer.printf("%-20s",      m._methodDesc);
        writer.write(NEW_LINE);
      }
      writer.flush();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if(writer != null) {
        try {
          writer.close();
        } catch (Exception e2) {
          e2.printStackTrace();
        }
      }
    }
  }

  private static void readMethodProfileData(String fileBase, int index, long startTimePoint, long endTimePoint) {
    long offsetStart = 0;
    if(startTimePoint > 0) {
      offsetStart = startTimePoint - ParserUtils.STARTUP_TIME;
      if(offsetStart < 0) {
        offsetStart = 0;
      }
    }
    
    long offsetEnd = endTimePoint - ParserUtils.STARTUP_TIME;
    if(offsetEnd <= 0) {
      System.out.println("Error: The end time is before application startup time.");
      return;
    }
    boolean isEnd = false;
    while(index >= 0 && !isEnd) {
      BufferedReader reader = null;
      try {
        if(index == 0) {
          reader = new BufferedReader(new FileReader(new File(fileBase)));
        } else {        
          reader = new BufferedReader(new FileReader(new File(fileBase + "." + index)));
        }
        String line = null;
        while((line = reader.readLine()) != null) {
          try {
            if(line.startsWith("#")) {
              continue;
            }
            
            String[] values = line.split(SEP);
            if(values.length < 6) {
              continue;
            }
            int startTime  = Integer.valueOf(values[4]);
            int usedTime = Integer.valueOf(values[5]);
            if(startTime < offsetStart) {
              int endTime = startTime + usedTime;
              if(endTime > offsetEnd) {
                isEnd = true;
                break;
              }
            }
            
            String methodId = values[2];
            boolean isWaitFlag = !"N".equals(values[3]);
            

            String methodDesc = MEHTHOD_INFO_MAP.get(methodId);
            if(methodDesc == null) {
              methodDesc = "Unknown";
            }
            MethodMetrics metric = METHOD_METRIC_MAP.get(methodDesc);
            if(metric == null) {
              metric = new MethodMetrics();
              metric._methodId = values[2];
              metric._methodDesc = methodDesc;
              metric._minWaitTime = usedTime;
              metric._maxWaitTime = usedTime;
              metric._minTime = usedTime;
              metric._maxTime = usedTime;
              
              METHOD_METRIC_MAP.put(methodDesc, metric);
            }
            if(isWaitFlag) {
              metric._totalWaitTime += usedTime;
              metric._waitCount++;
              if(metric._minWaitTime > usedTime) {
                metric._minWaitTime = usedTime;
              }
              if(metric._maxWaitTime < usedTime) {
                metric._maxWaitTime = usedTime;
              }
            } else {
              metric._totalTime += usedTime;
              metric._count++;
              if(metric._minTime > usedTime) {
                metric._minTime = usedTime;
              }
              if(metric._maxTime < usedTime) {
                metric._maxTime = usedTime;
              }
            }
          } catch (Exception e) {
            continue;
          }
        }
      } catch (FileNotFoundException fnfEx) {
        fnfEx.printStackTrace();
        break;
      } catch (Exception e) {
        e.printStackTrace();
        break;
      } finally {
        index --;
        if(reader != null) {
          try {
            reader.close();
          } catch (Exception ex) {}
        }
      }
    }
  }
}
