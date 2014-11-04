package org.myprofiler4j.java.hotloader;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.myprofiler4j.java.agent.Main;

/**
 * Run the specified class which is transform from the incoming bytes
 * 
 * @author nhuang
 *
 */
public class JavaClassExecutor {
  private final static Logger logger = Logger.getLogger(JavaClassExecutor.class.getName());

  public static String execute(byte[] classBytes) {
    Class<?> clz = null;
    try {
      HackSystem.clearBuffer();
      ClassModifier cm = new ClassModifier(classBytes);
      byte[] modifiedBytes = cm.modifyUTF8Constant("java/lang/System", "org/myprofiler4j/java/hotloader/HackSystem");
      HotSwapClassLoader loader = new HotSwapClassLoader();
      clz = loader.loadBytes(modifiedBytes);
      Object obj = clz.newInstance();
      if(obj instanceof IRunnableClass) {
        String clzName = ((IRunnableClass)obj).getClassName();
        if(clzName == null) {
          return "IRunnableClass.getClassName() return null.";
        }
        Class<?> targetClz = Main.findClass(clzName);
        if(targetClz == null) {
          return "Class not found - " + clzName;
        }
        loader = new HotSwapClassLoader(targetClz.getClassLoader());
        clz = loader.loadBytes(modifiedBytes);
      }
      Method m = clz.getMethod("main", new Class[] {String[].class});
      m.invoke(null, (Object[])new String[]{ null });
      logger.log(Level.INFO, "Class[" + clz.getName() + "] executed successfully.");
    } catch (Throwable e) {
      e.printStackTrace(HackSystem.out);
      logger.log(Level.SEVERE, "Failed to run class: " + (clz == null ? "Unknown" : clz.getName()), e);
    }
    String rs = HackSystem.getBufferString();
    if(rs == null || rs.isEmpty()) {
      return "Success";
    } else {
    	logger.log(Level.INFO, rs);
    }
    return rs;
  }
}
