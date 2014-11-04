/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package org.myprofiler4j.java.agent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.myprofiler4j.java.config.ProfConfig;

/**
 * The class used for collecting method execution statistics which invoked
 * during method instrumentation.
 * 
 * @author nhuang
 * @since 2013-11-28
 */
public class Profiler {
  private final static transient Logger logger = Logger.getLogger(Profiler.class.getName());
  
  /**
   * Instrumented class count
   */
  public final static AtomicInteger instrumentClassCount = new AtomicInteger(0);
  /**
   * Instrumented method count
   */
  public final static AtomicInteger instrumentMethodCount = new AtomicInteger(0);
  /**
   * Profiling startup time
   */
  public static long startProfileTime = 0;
  /**
   * Separator for profile data file
   */
  final static String SEP = ":";
  /**
   * ProfileData
   */
  static BlockingQueue<String> METHOD_PROFILE_QUEUE = null;
  /**
   * Object creation statistics container
   */
  static ConcurrentHashMap<String, ClassAllocation> OBJECT_CREAT_TRACE = null;
  /**
   * Thread info queue
   */
  static BlockingQueue<ThreadInfo> THREAD_QUEUE = null;
  /**
   * SQL profile data queue
   */
  static BlockingQueue<String> SQL_PROFILE_QUEUE = null;

  /**
   * ThreadStack
   */
  private static volatile ThreadLocal<ThreadFrameStack> THREADS_DATA = null;
  private final static int MAX_DEPTH = ThreadFrameStack.MAX_STACK_DEPTH;
  static volatile boolean isEnabled = false;
  static volatile boolean outputBlockTime = false;
  static volatile boolean isTraceObjectAlloc = false;
  static volatile boolean isDebugMode = false;
  static volatile int methodThreshold = 10;
  static volatile int sqlThreshold = 500;
  
  /**
   * Is Profiler threads alive?
   */
  static volatile boolean isShutdown = true;

  /**
   * Begin profile at method entry
   * 
   * @param methodId
   */
  public final static void start() {
	  if(isShutdown) {
		  return;
	  }

	  ThreadFrameStack stackData = THREADS_DATA.get();
	  if (stackData.isExcluded) {
		  return;
	  }

	  stackData.depth ++;
	  if (stackData.depth > MAX_DEPTH) {
		  if(stackData.logEnabled) {
			  logger.log(Level.WARNING, stackData.threadInfo.toString() + " may run recursively:", new Exception());
			  stackData.logEnabled = false;
		  }
		  return;
	  } else {
		  stackData.pushStartTime((int) (System.currentTimeMillis() - startProfileTime));
	  }
  }
  
  /**
   * End method profile before return
   * 
   * @param methodId
   */
  public final static void end(String methodDesc) {
	  if(isShutdown) {
		  return;
	  }
	  ThreadFrameStack stackData = THREADS_DATA.get();
	  if (stackData.isExcluded) {
		  return;
	  }

	  if (stackData.depth > MAX_DEPTH) {
		  stackData.depth--;
		  return;
	  }
	  
	  if (isEnabled) {
		  recordTime(stackData, methodDesc);
	  }
	  
	  stackData.depth--;
	  stackData.cursor--;
  }
  
  private static void recordTime(ThreadFrameStack stackData, String methodDesc) {
	  int endTime = (int) (System.currentTimeMillis() - startProfileTime);
	  int startTime = stackData.getStartTime();
	  int usedTime = endTime - startTime;
	  if (isTraceObjectAlloc && methodDesc.indexOf("<init>") != -1) {
		  ClassAllocation co = OBJECT_CREAT_TRACE.get(methodDesc);
		  if (co == null) {
			  co = new ClassAllocation();
			  co._classContructor = methodDesc;
			  OBJECT_CREAT_TRACE.put(methodDesc, co);
		  }
		  co._count++;
		  co._usedTime += usedTime;
	  }

	  if (usedTime > methodThreshold) {
		  // ThreadInfo(threadname:threadId) + stackIndex + methodDesc + Type + startTime + usedTime
		  String info = stackData.threadInfo.threadId + SEP + (stackData.depth) + SEP + methodDesc + SEP + 'N' + SEP + startTime + SEP + usedTime;
		  if (isDebugMode) {
			  stackData.crawlCallLocation(methodDesc);
			  info +=  (SEP + stackData.sourceFile + SEP + stackData.lineNumber);
		  }
		  METHOD_PROFILE_QUEUE.offer(info);
	  }
  }

  public final static void beginWait() {
    if(isShutdown) {
      return;
    }
    ThreadFrameStack stackData = THREADS_DATA.get();
    if (stackData.isExcluded || stackData.depth > MAX_DEPTH) {
      return;
    }
    stackData.waitStartTime = (int) (System.currentTimeMillis() - startProfileTime);
  }

  public final static void endWait(String methodDesc) {
    if(isShutdown) {
      return;
    }
    ThreadFrameStack stackData = THREADS_DATA.get();
    if (stackData.isExcluded || stackData.depth > MAX_DEPTH) {
      return;
    }
    final int endWaitTime = (int) (System.currentTimeMillis() - startProfileTime);
    final int startWaitTime = stackData.waitStartTime;
    final int waitedTime = endWaitTime - startWaitTime;
    stackData.waitStartTime = 0;
    if (isEnabled && outputBlockTime && waitedTime > methodThreshold) {
      // ThreadInfo(threadname:threadId) + stackIndex + methodDesc + Type + startTime + usedTime
      String info = stackData.threadInfo.threadId + SEP + (stackData.depth) + SEP + methodDesc + SEP + startWaitTime + SEP + waitedTime;
      METHOD_PROFILE_QUEUE.offer(info);
    }
  }

  public final static void logException(Throwable th) {
	  if (isShutdown) {
		  return;
	  }
	  ThreadFrameStack stackData = THREADS_DATA.get();
	  if(stackData.lastException == th) {
		  return;
	  }
	  if(logger.isLoggable(Level.WARNING)) {
		  logger.log(Level.WARNING, "Handled exception:",  th);
	  }
	  THREADS_DATA.get().lastException = th;
  }
  
  public final static void logUnCaughtException(Throwable th) {
    if (isShutdown) {
      return;
    }
    ThreadFrameStack stackData = THREADS_DATA.get();
    if(th != stackData.lastException && logger.isLoggable(Level.SEVERE)) {
    	logger.log(Level.SEVERE, "UnHandled exception:",  th);
    }
    stackData.lastException = th;
  }
  
  public final static void beginExecuteStatement(byte methodId) {
    if(isShutdown || !isEnabled) {
      return;
    }
    ThreadFrameStack stackData = THREADS_DATA.get();
    if(stackData.stmtDepth > 0) {
      stackData.stmtDepth ++;
      return;
    }
    stackData.stmtDepth ++;
    stackData.stmtMethodId = methodId;
    stackData.stmtStartTime = (int) (System.currentTimeMillis() - startProfileTime);
  }
  
  public final static void endExecuteStatement(Object statement) {
    if(isShutdown || !isEnabled) {
      return;
    }
    ThreadFrameStack stackData = THREADS_DATA.get();
    if(stackData.stmtDepth > 1 || statement == null) {
      stackData.stmtDepth --;
      return;
    } else {
      int endTime = (int) (System.currentTimeMillis() - startProfileTime);
      int cost = endTime - stackData.stmtStartTime;
      stackData.stmtDepth --;
      if (cost > sqlThreshold) {
        SQL_PROFILE_QUEUE.offer(stackData.threadInfo.threadId + SEP + stackData.stmtMethodId + SEP + cost + SEP + statement.toString());
      }
    }
  }
  
  public static boolean isShutdown() {
    return isShutdown;
  }
  
  static void shutdown() {
    isShutdown = true;
    METHOD_PROFILE_QUEUE.clear();
    OBJECT_CREAT_TRACE.clear();
    THREAD_QUEUE.clear();
    SQL_PROFILE_QUEUE.clear();
    MethodInfo.clearCache();
    isEnabled = false;
    outputBlockTime = false;
    isTraceObjectAlloc = false;
    methodThreshold = Integer.MAX_VALUE;
    isDebugMode = false;
    sqlThreshold = Integer.MAX_VALUE;
  }

  static void startup() {
    isShutdown = false;
    isEnabled = ProfConfig.instance.isProfileOn;
    outputBlockTime = ProfConfig.instance.isOutputMethodBlockingTime;
    isDebugMode = ProfConfig.instance.isDebug;
    isTraceObjectAlloc = ProfConfig.instance.isTraceObjectAlloc;
    methodThreshold = ProfConfig.instance.methodExcutionTimeThreshold;
    startProfileTime = System.currentTimeMillis();
    sqlThreshold = ProfConfig.instance.sqlThreshold;
    THREADS_DATA = new ThreadLocal<ThreadFrameStack>() {
    	@Override
    	protected ThreadFrameStack initialValue() {
    		return new ThreadFrameStack();
    	}
    };
    if(METHOD_PROFILE_QUEUE == null) {
      METHOD_PROFILE_QUEUE = new LinkedBlockingQueue<String>(30000);
    } else {
      METHOD_PROFILE_QUEUE.clear();
    }
    if(OBJECT_CREAT_TRACE == null) {      
      OBJECT_CREAT_TRACE = new ConcurrentHashMap<String, ClassAllocation>();
    } else {
      OBJECT_CREAT_TRACE.clear();
    }
    if(THREAD_QUEUE == null) {
      THREAD_QUEUE = new LinkedBlockingQueue<ThreadInfo>();
    } else {
      THREAD_QUEUE.clear();
    }
    if(SQL_PROFILE_QUEUE == null) {
      SQL_PROFILE_QUEUE = new LinkedBlockingQueue<String>();
    } else {
      SQL_PROFILE_QUEUE.clear();
    }
  }
}
