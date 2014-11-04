/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package org.myprofiler4j.java.agent;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.myprofiler4j.java.agent.instrument.ProfTransformer;

/**
 * Profiler agent main class
 * 
 * @author nhuang
 * @since 2013-11-28
 */
public class Main {
  private static final Logger logger = Logger.getLogger(Main.class.getName());

  static volatile Instrumentation INST = null;

  /**
   * Agent premain entry
   * @param args
   * @param inst
   */
  public static void premain(String args, Instrumentation inst) {
    main(args, inst);
  }

  /**
   * Agent main entry
   * @param args
   * @param inst
   */
  public static void agentmain(String args, Instrumentation inst) {
    StringTokenizer stn = new StringTokenizer(args, ";");
    while(stn.hasMoreTokens()) {
      String[] prop = stn.nextToken().split("=");
      if(prop.length == 2) {
        System.setProperty(prop[0], prop[1]);
      }
    }
    main(args, inst);
  }

  private static synchronized void main(String args, Instrumentation inst) {
    if(INST == null) {
      INST = inst;
    } else {
      return;
    }

    Manager.instance().initialization();
    Manager.instance().startupThreads();
    if("silent".equalsIgnoreCase(System.getProperty("profile.mode"))) {
      Profiler.isShutdown = true;
      logger.info("Profiler running in silent mode.");
    } else {
    	Profiler.isShutdown = false;
    }
    inst.addTransformer(new ProfTransformer(), true);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        Manager.instance().stopThreads();
      }
    });
  }

  public static String redefineClass(String clzName, byte[] redefinedClass) throws Exception{
    if(clzName.startsWith("org.myprofiler4j.java.")) {
      return "Failed: Profiler classes are not allowed to redefine.";
    }
    
    Class<?> targetClass = null;
    try {
      targetClass = Class.forName(clzName);
    } catch (ClassNotFoundException e) {
      targetClass = findClass(clzName);
    }
    if(targetClass != null) {
      ClassDefinition clzDef = new ClassDefinition(targetClass, redefinedClass);
      INST.redefineClasses(clzDef);
      if(logger.isLoggable(Level.INFO)) {
        logger.info("Class - " + clzName + " successfully redefined.");
      }
      return "Success";
    }
    return "Failed: Class not found";
  }

  public static Class<?> findClass(String clzName) {
    Class<?>[] allClz = INST.getAllLoadedClasses();
    if(logger.isLoggable(Level.INFO)) {
      logger.info("VM totally loaded " + allClz.length + " classes.");
    }
    if(allClz != null && allClz.length > 0) {
      for(Class<?> clz : allClz) {
        String name = clz.getName();
        if(name.equals(clzName) && INST.isModifiableClass(clz)) {
          return clz;
        }
      }
    }
    return null;
  }
}
