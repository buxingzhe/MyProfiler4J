/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package org.myprofiler4j.java.agent;

import org.myprofiler4j.java.config.ProfConfig;
import org.myprofiler4j.java.config.ProfFilter;

/**
 * Profiler Manager class
 * 
 * @author nhuang
 * @since 2013-11-28
 */
public class Manager {
  /**
   * Singleton instance
   */
  private static Manager manager = new Manager();

  /**
   * Thread for socket communication with client
   */
  private Thread socketThread;

  /**
   * Internal socket handler
   */
  private ProfilerSocketListenerThread adminSocket;

  /**
   * Thread for dump profiling data to disk/DB
   */
  private MethodProfileDataDumpThread dumpThread;
  /**
   * Thread for dump thread info to file
   */
  private ThreadInfoDumpThread threadInfoDumpThread;
  /**
   * Thread for dump class allocation data
   */
  private ClassAllocationDumpThread classAllocDumpThread;
  /**
   * Thread for SQL profile data dump
   */
  private SQLProfileDataDumpThread sqlDumpThread;

  /**
   * private constructor
   */
  private Manager() {}

  /**
   * Manager initialization
   */
  public void initialization() {
    ProfConfig.instance.init();
    setProfFilter();
    LogConfiguration.configure();
  }

  /**
   * @return
   */
  public static Manager instance() {
    return manager;
  }
  
  /**
   * Set profile package, class loader, and thread filter 
   * 
   */
  private synchronized void setProfFilter() {
    //Exclude profiler itself classes
    ProfFilter.instance().addExcludePackage("org/myprofiler4j/java");
    ProfFilter.instance().addExcludedThreadPattern("^MyProfiler4J-.*$");
    
    String separator = ";";
    String classLoader = ProfConfig.instance.excludeClassLoader;
    if (classLoader != null && classLoader.trim().length() > 0) {
      String[] _classLoader = classLoader.split(separator);
      for (String pack : _classLoader) {
        ProfFilter.instance().addExcludeClassLoader(pack);
      }
    }
    String include = ProfConfig.instance.includePackageStartsWith;
    if (include != null && include.trim().length() > 0) {
      String[] _includes = include.split(separator);
      for (String pack : _includes) {
        ProfFilter.instance().addIncludePackage(pack);
      }
    }
    String includeJDBCClasses = ProfConfig.instance.jdbcStatementClasses;
    if (includeJDBCClasses != null && includeJDBCClasses.trim().length() > 0) {
      String[] jdbcClasses = includeJDBCClasses.split(separator);
      for (String clz : jdbcClasses) {
        ProfFilter.instance().addIncludePackage(clz);
      }
    }
    String exclude = ProfConfig.instance.excludePackageStartsWith;
    if (exclude != null && exclude.trim().length() > 0) {
      String[] _excludes = exclude.split(";");
      for (String pack : _excludes) {
        ProfFilter.instance().addExcludePackage(pack);
      }
    }

    String excludeThreads = ProfConfig.instance.excludedThreadNames;
    if(excludeThreads != null && !excludeThreads.isEmpty()) {
      String[] _threadLikes = excludeThreads.split(separator);
      for (String nameLike : _threadLikes) {
        ProfFilter.instance().addExcludedThreadPattern(nameLike);
      }
    }
  }

  public void flushClassAlloc() {
    if(classAllocDumpThread != null) {
      classAllocDumpThread.flushClassAlloc();
    }
  }

  public void flushMethodProfileData() {
    if(dumpThread != null) {
      dumpThread.flushAllData();
    }
  }

  public void flush() {
    flushMethodProfileData();
    flushClassAlloc();
  }

  /**
   * Start internal threads
   */
   public void startupThreads() {
     Profiler.startup();
     
     if(adminSocket == null) {
       adminSocket = new ProfilerSocketListenerThread(ProfConfig.instance.port);
       socketThread = new Thread(adminSocket);
       socketThread.setName("MyProfiler4J-InnerSocket");
       socketThread.setDaemon(true);
       socketThread.start();
     }

     if(dumpThread == null) {
       dumpThread = new MethodProfileDataDumpThread();
       dumpThread.setName("MyProfiler4J-MethodDataDump");
       dumpThread.setDaemon(true);
       dumpThread.start();
     }
     
     if(threadInfoDumpThread == null) {
    	 threadInfoDumpThread = new ThreadInfoDumpThread();
    	 threadInfoDumpThread.setName("MyProfiler4J-ThreadInfoDump");
    	 threadInfoDumpThread.setDaemon(true);
    	 threadInfoDumpThread.start();
     }
     
     if(classAllocDumpThread == null) {          
       classAllocDumpThread = new ClassAllocationDumpThread();
       classAllocDumpThread.setName("MyProfiler4J-ClassAllocDump");
       classAllocDumpThread.setDaemon(true);
       classAllocDumpThread.start();
     }
     
     if(sqlDumpThread == null && !ProfConfig.instance.jdbcStatementClasses.isEmpty()) {
       sqlDumpThread = new SQLProfileDataDumpThread();
       sqlDumpThread.setName("MyProfiler4J-SQLProfDump");
       sqlDumpThread.setDaemon(true);
       sqlDumpThread.start();
     }
   }

   public void stopThreads() {
     Profiler.shutdown();
     ProfFilter.instance().clear();
     Main.INST = null;
     ProfConfig.instance.reset();
     LogConfiguration.clear();
     
     if(dumpThread != null) {	      
       dumpThread.exit();
       dumpThread = null;
     }
     if(threadInfoDumpThread != null) {
    	 threadInfoDumpThread.exit();
    	 threadInfoDumpThread = null;
     }
     if(classAllocDumpThread != null) {
       classAllocDumpThread.exit();	      
       classAllocDumpThread = null;
     }
     if(adminSocket != null) {
       adminSocket.exit();
       adminSocket = null;
     }
     if(sqlDumpThread != null) {
       sqlDumpThread.exit();
       sqlDumpThread = null;
     }
   }
}
