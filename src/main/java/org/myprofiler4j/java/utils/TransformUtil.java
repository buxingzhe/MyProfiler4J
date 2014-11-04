package org.myprofiler4j.java.utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.myprofiler4j.java.agent.instrument.ProfClassVisitor;
import org.myprofiler4j.java.asm.ClassReader;
import org.myprofiler4j.java.asm.ClassVisitor;
import org.myprofiler4j.java.asm.ClassWriter;
import org.myprofiler4j.java.config.ProfConfig;

public class TransformUtil {

  public static void generateTransformedClassFile(String clzFileName) {
    OutputStream out = null;
    try {
      File file = new File(clzFileName);
      if(!file.exists()) {
        System.out.println("File: " + file.getAbsolutePath() + " not exist.");
        return;
      }
      byte[] clzBytes = Utilities.readFile(file, 0, -1);
      ByteArrayInputStream input = new ByteArrayInputStream(clzBytes);
      String className = Utilities.getClassFQN(input);
      
      ClassReader reader = new ClassReader(clzBytes);
      ClassWriter writer = new ClassWriter(reader, 0);
      ClassVisitor adapter = new ProfClassVisitor(writer, className);
      reader.accept(adapter, 0);
      
      byte[] newClzBytes = writer.toByteArray();
      int index = className.lastIndexOf('.');
      String fileName = className.substring(index + 1);
      File newFile = new File(fileName + ".class");
      if(newFile.exists()) {
        newFile = new File(fileName + "_1.class");
      }
      out = new FileOutputStream(newFile);
      out.write(newClzBytes);
      out.flush();
      System.out.println("Trasformed class path: " + newFile.getAbsolutePath());
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if(out != null) {
        try {
          out.close();
        } catch (IOException e) {
        }
      }
    }
  }
  
  public static void main(String[] args) {
    if(args != null && args.length > 0) {
      ProfConfig.instance.init();
      generateTransformedClassFile(args[0]);
    }
  }
}
