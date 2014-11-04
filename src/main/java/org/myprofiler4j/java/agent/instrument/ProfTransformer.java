/**
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package org.myprofiler4j.java.agent.instrument;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.myprofiler4j.java.agent.Profiler;
import org.myprofiler4j.java.asm.ClassReader;
import org.myprofiler4j.java.asm.ClassVisitor;
import org.myprofiler4j.java.asm.ClassWriter;
import org.myprofiler4j.java.config.ProfFilter;


/**
 * This class determines if a given class should be instrumented
 * with profiling code or not. The property <code>debug</code>, when
 * set to <code>yes</code>, will show you which classes are being instrumented
 * and what ones are not.
 * 
 * @author nhuang
 * @since 2013-11-28
 */
public class ProfTransformer implements ClassFileTransformer {

  private final static Logger logger = Logger.getLogger(ProfTransformer.class.getName());

  public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
    if (Profiler.isShutdown() 
        || (loader != null && ProfFilter.instance().isExcludedClassLoader(loader.getClass().getName()))) {
      return classfileBuffer;
    }
    if(!ProfFilter.instance().acceptClass(className)) {
      return classfileBuffer;
    }

    if (logger.isLoggable(Level.INFO)) {
      logger.info(" ---- Profiler Debug: ClassLoader:" + loader + " ---- class: " + className);
    }

    // Record instrumented class
    Profiler.instrumentClassCount.getAndIncrement();
    try {
      ClassReader reader = new ClassReader(classfileBuffer);
      ClassWriter writer = new ClassWriter(reader, 0);
      ClassVisitor adapter = new ProfClassVisitor(writer, className);
      reader.accept(adapter, 0);
      /*if(ProfConfig.instance.isDebug) {
		reader.accept(adapter, 0);
	  } else {
	    reader.accept(adapter, ClassReader.SKIP_DEBUG);
	  }*/
      return writer.toByteArray();
    } catch (Exception e) {
      return classfileBuffer;
    }
  }
  
  public static void main(final String[] args) throws Exception {
      if (args.length != 1) {
          System.err.println("Usage: java com.sun.btrace.runtime.ErrorReturnInstrumentor <class>");
          System.exit(1);
      }

      args[0] = args[0].replace('.', '/');
      FileInputStream fis = new FileInputStream(args[0] + ".class");
      ClassReader reader = new ClassReader(new BufferedInputStream(fis));
      FileOutputStream fos = new FileOutputStream(args[0] + "New.class");
      try {
    	  ClassWriter writer = new ClassWriter(reader, 0);
    	  ClassVisitor adapter = new ProfClassVisitor(writer, "com/calix/test/ExceptionTest");
    	  reader.accept(adapter, 0);
    	  fos.write(writer.toByteArray());		
	} catch (Exception e) {
		e.printStackTrace();
	} finally {
		fos.close();
	}
  }
}
