package org.myprofiler4j.java.agent;

import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Java logging configuration class for profiler logging
 * 
 * @author nhuang
 *
 */
public class LogConfiguration {
  private static Logger ROOT = null;
  
  private LogConfiguration() {
  }

  public final static void configure() {
    FileHandler fh = null;
    try {
      ROOT = Logger.getLogger("org.myprofiler4j.java");
      ROOT.setUseParentHandlers(false);
      ROOT.setLevel(Level.SEVERE);
      fh = new FileHandler("%h/logs/profilerLog.txt", 1 << 24, 20, true);
      fh.setFormatter(new SimpleFormatter());
      fh.setEncoding("UTF-8");
      fh.setLevel(Level.SEVERE);
      ROOT.addHandler(fh);
    } catch (Exception e) {
    	e.printStackTrace();
    	if(fh != null) {
    		try {
    			fh.close();            
    		} catch (Exception e2) {
    		}
    	}
    }
  }
  
  public static void clear() {
	  Handler[] handlers = ROOT.getHandlers();
      for (Handler handler : handlers) {
    	  ROOT.removeHandler(handler);
      }
  }
  
  public static synchronized String setLogLevel(String level) {
	  if(ROOT != null) {
		  Level lvl = Level.parse(level.toUpperCase());
		  ROOT.setLevel(lvl);
		  Handler[] handlers = ROOT.getHandlers();
		  if(handlers != null) {
			  for(Handler handler: handlers) {
				  handler.setLevel(lvl);
			  }
		  }
	  }
	  return null;
  }
  
  public static Level getLogLevel() {
	  return ROOT.getLevel();
  }
}
