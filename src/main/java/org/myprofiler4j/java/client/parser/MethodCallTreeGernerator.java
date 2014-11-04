package org.myprofiler4j.java.client.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.myprofiler4j.java.utils.ReverseLineReader;

public class MethodCallTreeGernerator {
  private static Map<String, String> methodInfoMap = new LinkedHashMap<String, String>();
  private static Map<String, String> threadInfoMap = new LinkedHashMap<String, String>();
  private static final String NEW_LINE = System.getProperty("line.separator");
  private final static String SEP = ParserUtils.SEP;
  private static String outFile = "methodCallTree";
  private static PrintWriter writer;
  private static Set<String> THREAD_ID_SET = new HashSet<String>();
  private static Map<String, Map<Integer, MethodMetrics>> THREAD_METRICS_MAP = new HashMap<String, Map<Integer,MethodMetrics>>();
  private static Map<String, Map<String, MethodMetrics>> THREAD_CURRENT_FRAME = new HashMap<String, Map<String, MethodMetrics>>();
  
  public static void main(String[] args) {
    if(args == null || args.length < 1) {
      System.out.println("Usage: methodProfileFilePath [threadId1,threadId2,threadId3|none] [startTime] [endTime]");
      System.exit(-1);
    }

    String profileFilePath = args[0];
    if(!ParserUtils.isMethodProfilingFile(profileFilePath)) {
      System.out.println("The profiling file path is not correct, please check.");
      System.out.println("The file name should be like methodProfile_*.txt or methodProfile_*.txt.1");
      System.exit(-1);
    }
    outFile = outFile + ParserUtils.getFilePostfix(profileFilePath);
    
    String fileBase = null;
    int index = 0;
    if(profileFilePath.endsWith(".txt")) {
      fileBase = profileFilePath;
    } else {
      int lastDotIndex = profileFilePath.lastIndexOf('.');
      fileBase = profileFilePath.substring(0, lastDotIndex);
      index = Integer.valueOf(profileFilePath.substring(lastDotIndex + 1));
    }
    
    if(args.length > 1) {
      String[] threadIds = args[1].split(",");
      for (int i = 0; i < threadIds.length; i++) {
        THREAD_ID_SET.add(threadIds[i]);
      }
    }
    
    String methodFilePath = profileFilePath.replace("methodProfile", "methodInfo");
    String threadInfoFile = profileFilePath.replace("methodProfile", "threadInfo");
    
    try {
      ParserUtils.readMethodInfo(methodFilePath, methodInfoMap, null);
      ParserUtils.readThreadInfo(threadInfoFile, threadInfoMap, THREAD_ID_SET);
      ParserUtils.initTimeZone(threadInfoFile);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
    
    long startTimePoint = 0;
    long endTimePoint = Long.MAX_VALUE;
    try {
      if(args.length > 3) {
        startTimePoint = ParserUtils.getTimeInMilliseconds(args[2]);
        endTimePoint = ParserUtils.getTimeInMilliseconds(args[3]);
      } else if(args.length > 2) {
        startTimePoint = ParserUtils.getTimeInMilliseconds(args[2]);
      }
      if(endTimePoint <= startTimePoint) {
        System.out.println("Error: Start time is later than end time.");
        System.exit(0);
      }
    } catch (Exception e) {
      System.out.println("Time format error!");
      System.exit(-1);
    }
    
    parseMethodProfileData(fileBase, index, startTimePoint, endTimePoint);
    printFileHeader();
    printThreadCallTree();
  }
  
  private static void printThreadCallTree() {
    try {
      Iterator<String> iter = THREAD_METRICS_MAP.keySet().iterator();
      while(iter.hasNext()) {
        String threadId = iter.next();
        printThreadHeader(threadId);
        Map<Integer, MethodMetrics> threadMetrics = THREAD_METRICS_MAP.get(threadId);
        MethodMetrics m = threadMetrics.get(0);
        dump(writer, 0, m, m._totalTime);
        writer.append(NEW_LINE);
        writer.append(NEW_LINE);
        writer.flush();
      }      
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
  
  private static void dump(PrintWriter writer, 
      int depth, 
      MethodMetrics metrics, 
      double threadTotalTime) {
    String methodName = methodInfoMap.get(metrics._methodId);
    if(methodName == null) {
      Iterator<MethodMetrics> iter = metrics.sortedChildrenIterator();
      if(iter != null) {
        while(iter.hasNext()) {
          MethodMetrics next = iter.next();
          dump(writer, depth + 1, next, next._totalTime);
        }
      }
      return;
    }
    
    long total = metrics._totalTime;
    long net = metrics._totalTime - metrics._totalWaitTime;
    writer.printf("%6d ", metrics._count);
    writer.printf("%8d ", total);
    writer.printf("%8d ", net);

    if (total > 0 ) {
      double percent = (total/threadTotalTime) * 100;
      writer.printf("%7.1f ", percent);
    } else {
      writer.print("        ");
    }

    if (net > 0 && threadTotalTime > 0.000) {
      double percent = (net/threadTotalTime) * 100;;

      if (percent > 0.1) {
        writer.printf("%7.1f ", percent);
      } else {
        writer.print("        ");
      }
    } else {
      writer.print("        ");
    }

    writer.print(" ");
    for (int i = 1; i< depth; i++) {
      writer.print("| ");
    }

    writer.print("+--");
    if(metrics.debugInfo != null) {
      writer.print(methodName + "(" + metrics.debugInfo + ")");
    } else {      
      writer.print(methodName);
    }
    writer.print(NEW_LINE);

    Iterator<MethodMetrics> iter = metrics.sortedChildrenIterator();
    if(iter != null) {
      while(iter.hasNext()) {
        MethodMetrics next = iter.next();
        dump(writer, depth + 1, next, threadTotalTime);
      }
    }
  }
  
  private static void parseMethodProfileData(String fileBase, int index, long startTimePoint, long endTimePoint) {
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
    ReverseLineReader reader = null;
    while(index >= 0 && !isEnd) {
      try {
        if(index == 0) {
          reader = new ReverseLineReader(new File(fileBase), "UTF-8");
        } else {          
          reader = new ReverseLineReader(new File(fileBase + "." + index), "UTF-8");
        }
        
        String line = null;
        MethodMetrics lastMethod = null;
        while((line = reader.readLine()) != null) {
          try {
            if(line.startsWith("#")) {
              continue;
            }
            
            String[] values = line.split(SEP);
            String debugInfo = null;
            String depthStr = values[1];
            Integer depth = Integer.valueOf(depthStr);
            String methodId = values[2];
            boolean isWaitFlag = !"N".equals(values[3]);
            
            if(values.length < 6) {
              continue;
            }else if(depth != 1 && values.length > 6) {
              debugInfo = values[6] + SEP + values[7];
            }
            
            int startTime  = Integer.valueOf(values[4]);
            int usedTime = Integer.valueOf(values[5]);
            if(startTime < offsetStart) {
              int endTime = startTime + usedTime;
              if(endTime > offsetEnd) {
                isEnd = true;
                continue;
              }
            }
            
            String threadId = values[0];
            if(THREAD_ID_SET.size() > 0 && !THREAD_ID_SET.contains(threadId)) {
              continue;
            }
           
            Map<Integer, MethodMetrics> lastMethodNodes = THREAD_METRICS_MAP.get(threadId);
            if(lastMethodNodes == null) {
              lastMethodNodes = new LinkedHashMap<Integer, MethodMetrics>();
              MethodMetrics root = new MethodMetrics();
              root.isCallTreeNode = true;
              lastMethodNodes.put(0, root);
              THREAD_METRICS_MAP.put(threadId, lastMethodNodes);
            }
            
            Map<String, MethodMetrics> frameMap = THREAD_CURRENT_FRAME.get(threadId);
            if(frameMap == null) {
              frameMap = new HashMap<String, MethodMetrics>();
              THREAD_CURRENT_FRAME.put(threadId, frameMap);
            }
            
            if(depth == 1) {
              frameMap.clear();
              MethodMetrics methodMetrics = lastMethodNodes.get(0);
              methodMetrics.visitMethod(usedTime, isWaitFlag);
              lastMethod = methodMetrics.visitChild(methodId, startTime, usedTime, isWaitFlag, debugInfo);
            } else {
              MethodMetrics metrics = frameMap.get(String.valueOf(depth - 1));
              if(metrics == null) {
                frameMap.clear();
                metrics = lastMethodNodes.get(0);
                metrics.visitMethod(usedTime, isWaitFlag);
                lastMethod = metrics.visitChild(methodId, startTime, usedTime, isWaitFlag, debugInfo);
              } else {
                lastMethod = metrics.visitChild(methodId, startTime, usedTime, isWaitFlag, debugInfo);
              }
            }
            
            frameMap.put(depthStr, lastMethod);
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
          reader.close();
        }
      }
    }
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
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private static void printThreadHeader(String threadId) {
    try {
      writer.print("+---------------------------------------------------------------------------");
      writer.print(NEW_LINE);
      writer.print("|Thread ID: " + threadId + ", Thread Name: " + threadInfoMap.get(threadId));
      writer.print(NEW_LINE);
      writer.print("+---------------------------------------------------------------------------");
      writer.print(NEW_LINE);     
      writer.print("              Time            Percent    ");
      writer.print(NEW_LINE);
      writer.print("       ----------------- ---------------");
      writer.print(NEW_LINE);
      writer.print(" Count    Total      Net   Total     Net  Location");
      writer.print(NEW_LINE);
      writer.print(" =====    =====      ===   =====     ===  =========");
      writer.print(NEW_LINE);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
