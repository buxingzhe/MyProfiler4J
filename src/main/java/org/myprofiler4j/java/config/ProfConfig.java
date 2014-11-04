/**
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package org.myprofiler4j.java.config;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.myprofiler4j.java.agent.SwitchProfilerStatusTask;
import org.myprofiler4j.java.utils.Utilities;

/**
 * Profile configurations
 * 
 * @author nhuang
 * @since 2013-11-28
 */
public class ProfConfig {

  private static final Logger logger = Logger.getLogger(ProfConfig.class.getName());

  /**
   * Configuration file name
   */
  public static final String CONFIG_FILE_NAME = "profile.properties";

  /**
   * Default file path: ~/.myprofiler4j/profile.properties
   */
  private File DEFAULT_PROFILE_PATH = new File(System.getProperty("user.home"), "/.myprofiler4j/" + CONFIG_FILE_NAME);

  public final static ProfConfig instance = new ProfConfig();

  private volatile boolean isInit = false;

  /**
   * Property names
   */
  private final static String PROFILER_SWITCH   = "profiler";
  private final static String LISTENER_PORT     = "port";
  private final static String TRACK_OBJ_ALLOC   = "trackObjectAlloc";
  private final static String OUTPUT_SIGNATURE  = "outputMethodSignatures";
  private final static String METHOD_THRESHOLD  = "methodProfileThresholdMs";
  private final static String IS_DEBUG_MODE        = "debugMode";
  private final static String IGNORE_GETSET     = "ignoreGetSetMethod";
  private final static String LOG_FILE_PATH     = "logFilePath";
  private final static String METHOD_FILE_PATH  = "methodFilePath";
  private final static String CLASS_FILE_PATH   = "classAllocFilePath";
  private final static String THREAD_FILE_PATH  = "threadInfoFilePath";
  private final static String SQL_DTL_FILE_PATH = "sqlDetailFilePath";
  private final static String SQL_DMP_FILE_PATH = "sqlDumpFilePath";
  private final static String EXCLUDE_CLD       = "excludeClassLoader";
  private final static String INCLUDE_PKG_PREFIX= "includePackageStartsWith";
  private final static String EXCLUDE_PKG_PREFIX= "excludePackageStartsWith";
  private final static String OUTPUT_BLOCKTIME  = "outputMethodBlockTime";
  private final static String EXCLUDE_THREADS   = "excludeThreadsLike";
  private final static String SCHEDULER_CRON_EXPRESSION = "schedulerCronExpression";
  private final static String SAMPLING_DURATION = "samplingDuration";
  private final static String MAX_CLIENT_SESSION= "maxClientSessions";
  private final static String JDBC_STMT_CLASSES = "jdbcStatementClasses";
  private final static String SQL_THRESHOLD     = "sqlThreshold";

  /**
   * Profile start time
   */
  public String startProfTime;

  /**
   * Profile end time
   */
  public String endProfTime;

  /**
   * log file path
   */
  public String logFilePath;

  /**
   * Method file path
   */
  public String methodFilePath;

  /**
   * method file path
   */
  public String classAllocationFilePath;

  /**
   * Thread info path
   */
  public String threadInfoFilePath;
  
  /**
   * SQL dump file path
   */
  public String sqlDumpFilePath;
  
  /**
   * SQL detail file path
   */
  public String sqlDetailFilePath;
  
  /**
   * Excluded ClassLoader
   */
  public String excludeClassLoader;

  /**
   * Included packages
   */
  public String includePackageStartsWith;

  /**
   * Excluded packages
   */
  public String excludePackageStartsWith;

  /**
   * Excluded threads
   */
  public String excludedThreadNames;
  
  /**
   * JDBC statement classes
   */
  public String jdbcStatementClasses = "";
  
  /**
   * If skip Get/Set methods
   */
  public boolean ignoreGetSetMethod = true;

  /**
   * If run in debug mode
   */
  public volatile boolean isDebug = false;

  /**
   * Server socket port number
   */
  public int port;

  public volatile boolean isProfileOn = false;

  public volatile int methodExcutionTimeThreshold = 1;

  public volatile boolean isTraceObjectAlloc = false;

  public volatile boolean isOutputMethodSignature = false;

  public volatile boolean isOutputMethodBlockingTime = false;
  
  public volatile int sqlThreshold = 500;

  public int maxClientSessions = 2;

  private static int DEFAULT_DURATION = 5;  //minutes
  public volatile int duration = DEFAULT_DURATION;    

  private volatile CronExpression cronExpression = null;

  private volatile Timer scheduler = null;
  /**
   * Constructor
   */
  private ProfConfig() {
  }

  public void init() {
    if(isInit) return;
    isInit = true;
    try {
      /*
       * Profile configuration search sequence:
       * 1. System property: -Dprofile.properties=/path/profile.properties
       * 2. Current directory: profile.properties
       * 3. User home directory: ~/.myprofiler4j/profile.properties
       * 4. Jar package: profile.properties
       */
      String specifiedConfigFileName = System.getProperty(CONFIG_FILE_NAME);
      File configFiles[] = {
          specifiedConfigFileName == null ? null : new File(specifiedConfigFileName), 
              new File(CONFIG_FILE_NAME), 
              DEFAULT_PROFILE_PATH
      };

      for (File file : configFiles){
        if (file != null && file.exists() && file.isFile()) {
          logger.info(String.format("load configuration from \"%s\".", file.getAbsolutePath()));
          parseProperty(file);
          return;
        }
      }

      logger.info(String.format("load configuration from \"%s\".", DEFAULT_PROFILE_PATH.getAbsolutePath()));
      extractDefaultProfile();
      parseProperty(DEFAULT_PROFILE_PATH);
    } catch (IOException e) {
      isInit = false;
      throw new RuntimeException("error load config file " + DEFAULT_PROFILE_PATH, e);
    }
  }

  /**
   * Extract the default configuration file to ~/.tprofiler/profile.properties, user can be edit based on the template
   * @throws IOException
   */
  private void extractDefaultProfile() throws IOException {
    InputStream in = new BufferedInputStream(getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME));
    OutputStream out = null;
    try{
      File profileDirectory = DEFAULT_PROFILE_PATH.getParentFile();
      if (!profileDirectory.exists()){
        profileDirectory.mkdirs();
      }
      out = new BufferedOutputStream(new FileOutputStream(DEFAULT_PROFILE_PATH));
      byte[] buffer = new byte[1024];
      for (int len = -1; (len = in.read(buffer)) != -1;){
        out.write(buffer, 0, len);
      }
    }finally{
      in.close();
      out.close();
    }
  }

  /**
   * Parse user configuration file
   * @param path
   */
  private void parseProperty(File path) {
    Properties properties = new Properties();
    try {
      properties.load(new FileReader(path));

      Properties context = new Properties(); 
      context.putAll(System.getProperties());
      context.putAll(properties);

      loadConfig(new ConfigureProperties(properties, context));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Load configurations
   * @param properties
   */
  private void loadConfig(Properties properties) throws Exception {
    this.isProfileOn = getBoolean(properties, PROFILER_SWITCH, false);
    this.isDebug = getBoolean(properties, IS_DEBUG_MODE, false);
    this.ignoreGetSetMethod = getBoolean(properties, IGNORE_GETSET, true);
    this.isTraceObjectAlloc = getBoolean(properties, TRACK_OBJ_ALLOC, false);
    this.isOutputMethodSignature = getBoolean(properties, OUTPUT_SIGNATURE, false);
    this.isOutputMethodBlockingTime = getBoolean(properties, OUTPUT_BLOCKTIME, false);

    this.port = getInt(properties, LISTENER_PORT, 15599);
    this.methodExcutionTimeThreshold = getInt(properties, METHOD_THRESHOLD, 10);
    this.sqlThreshold = getInt(properties, SQL_THRESHOLD, 500);
    this.maxClientSessions = getInt(properties, MAX_CLIENT_SESSION, 2);
    DEFAULT_DURATION = getInt(properties, SAMPLING_DURATION, 1);
    this.duration = DEFAULT_DURATION;

    this.excludedThreadNames = getString(properties, EXCLUDE_THREADS, "");
    this.logFilePath = getString(properties, LOG_FILE_PATH, "${user.home}/logs/${logFileName}");
    this.methodFilePath = getString(properties, METHOD_FILE_PATH, "${user.home}/logs/${methodFileName}");
    this.classAllocationFilePath = getString(properties, CLASS_FILE_PATH, "${user.home}/logs/${classAllocFileName}");
    this.threadInfoFilePath = getString(properties, THREAD_FILE_PATH, "${user.home}/logs/${threadInfoFileName}");
    this.sqlDetailFilePath = getString(properties, SQL_DTL_FILE_PATH, "${user.home}/logs/${sqlDetailFileName}");
    this.sqlDumpFilePath = getString(properties, SQL_DMP_FILE_PATH, "${user.home}/logs/${sqlDumpFileName}");
    this.includePackageStartsWith = getString(properties, INCLUDE_PKG_PREFIX, "");
    this.excludePackageStartsWith = getString(properties, EXCLUDE_PKG_PREFIX, "");
    this.excludeClassLoader = getString(properties, EXCLUDE_CLD, "");
    this.jdbcStatementClasses = getString(properties, JDBC_STMT_CLASSES, "");
    
    String cronExpressionStr = properties.getProperty(SCHEDULER_CRON_EXPRESSION);
    if(cronExpressionStr != null) {
      try {
        cronExpression = new CronExpression(cronExpressionStr);
        cronExpression.setTimeZone(TimeZone.getDefault());
        Date now = Calendar.getInstance().getTime();
        Date nextValidTime = cronExpression.getNextValidTimeAfter(now);
        schedule(true, nextValidTime);
      } catch (Exception e) {
        cronExpression = null;
      }
    }
  }

  private String getString(Properties prop, String key, String defValue) {
    return prop.getProperty(key, defValue);
  }

  private int getInt(Properties prop, String key, int defValue) {
    String v = prop.getProperty(key, String.valueOf(defValue));
    try {
      return Integer.valueOf(v);
    } catch (Exception e) {
      //ignored
    }
    return defValue;
  }

  private boolean getBoolean(Properties prop, String key, boolean defValue) {
    String v = prop.getProperty(key);
    try {
      return "yes".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
    } catch (Exception e) {
      //ignored
    }
    return defValue;
  }

  public void enableAtNextValidTimeAfterNow() {
    if(cronExpression != null) {
      Date d = cronExpression.getNextValidTimeAfter(Calendar.getInstance().getTime());
      schedule(true, d);
    }
  }

  public void disableAtNextInValidTimeAfterNow() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.MINUTE, duration);
    schedule(false, cal.getTime());
  }
  
  public CronExpression getCronExpression() {
    return this.cronExpression;
  }

  public synchronized void setCronExpression(CronExpression expression, int duration) {
    this.cronExpression = expression;
    if(cronExpression == null) return;
    if(duration > 0) {
      this.duration = duration;
    } else {
      this.duration = DEFAULT_DURATION;
    }

    if(scheduler != null) {
      scheduler.cancel();
      scheduler = null;
    }

    Date nextValidTime = cronExpression.getNextValidTimeAfter(Calendar.getInstance().getTime());
    schedule(true, nextValidTime);
  }

  private synchronized void schedule(boolean switchValue, Date date) {
    if(date == null) return;
    if(scheduler == null) {
      scheduler = new Timer("Profiler-Scheduler", true);
    }
    scheduler.schedule(new SwitchProfilerStatusTask(switchValue), date);
    if(logger.isLoggable(Level.INFO)) {
      if(switchValue) {
        logger.info("Profiler will be enabled at: " + Utilities.getDateString(date));
      } else {
        logger.info("Profiler will be disabled at: " + Utilities.getDateString(date));
      }
    }
  }

  public synchronized void reset() {
    this.isInit = false;
    if(scheduler != null) {
      scheduler.cancel();
      scheduler = null;
    }
  }
}
