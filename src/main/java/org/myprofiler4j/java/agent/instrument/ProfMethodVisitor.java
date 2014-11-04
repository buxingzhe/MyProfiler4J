/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package org.myprofiler4j.java.agent.instrument;

import java.util.HashSet;
import java.util.Set;

import org.myprofiler4j.java.agent.Profiler;
import org.myprofiler4j.java.asm.Label;
import org.myprofiler4j.java.asm.MethodVisitor;
import org.myprofiler4j.java.asm.Opcodes;


/**
 * This class is responsible for instrumenting a method to 
 * call the profiler in order for performance
 * data to be gathered. The basic idea is that the profiler is called
 * when a method starts and when it exists which allows the profiler
 * to gather performance data (note that a method can be exited from
 * when an exception is thrown as well as when return is called). The
 * one big caveate is static initializers. They are not called as part
 * of the flow of the program; they are called by the classloader.
 * Since they whole premise of the profiler is built on the idea of a
 * orderly call stack, static initializers are not instrumented. 
 * 
 * @author nhuang
 * @since 2013-11-28
 */
public class ProfMethodVisitor extends MethodVisitor {
  private static final String PROFILER_CLASS = "org/myprofiler4j/java/agent/Profiler";
  private static final Set<String> BLOCK_CLASSFQN_SET = new HashSet<String>(10, 1);
  private Label startLbl = new Label();
  private Label endLbl = new Label();
  
  static {
    BLOCK_CLASSFQN_SET.add("java/lang/Object");
    BLOCK_CLASSFQN_SET.add("java/lang/Thread");
    BLOCK_CLASSFQN_SET.add("java/util/concurrent/locks/Lock");
    BLOCK_CLASSFQN_SET.add("java/util/concurrent/locks/ReentrantLock");
    BLOCK_CLASSFQN_SET.add("java/util/concurrent/locks/Condition");
    BLOCK_CLASSFQN_SET.add("java/util/concurrent/TimeUnit");
  }

  /**
   * Method FQN(class name + method signature)
   */
  private String methodFqn;
  private boolean isToExit;
  byte jdbcMethodId = 0;
  
  /**
   * @param visitor
   * @param fileName
   * @param className
   * @param methodName
   */
  public ProfMethodVisitor(MethodVisitor visitor, String className, String methodName) {
    super(Opcodes.ASM5, visitor);
    this.methodFqn = className + "." + methodName;
    Profiler.instrumentMethodCount.getAndIncrement();
  }

  /* (non-Javadoc)
   * @see org.objectweb.asm.MethodVisitor#visitCode()
   */
  public void visitCode() {
	visitLabel(startLbl);
    if(jdbcMethodId > 0){
      this.visitLdcInsn(jdbcMethodId);
      this.visitMethodInsn(Opcodes.INVOKESTATIC, PROFILER_CLASS, "beginExecuteStatement", "(B)V", false);
    } else {      
      this.visitMethodInsn(Opcodes.INVOKESTATIC, PROFILER_CLASS, "start", "()V", false);
    }
    super.visitCode();
  }
  
  /* (non-Javadoc)
   * @see org.objectweb.asm.MethodVisitor#visitInsn(int)
   */
  public void visitInsn(int inst) {
	  switch (inst) {
	  case Opcodes.ARETURN:
	  case Opcodes.DRETURN:
	  case Opcodes.FRETURN:
	  case Opcodes.IRETURN:
	  case Opcodes.LRETURN:
	  case Opcodes.RETURN:
		  if(this.jdbcMethodId > 0) {
			  this.visitVarInsn(Opcodes.ALOAD, jdbcMethodId > 9 ? 0 : 1);
			  this.visitMethodInsn(Opcodes.INVOKESTATIC, PROFILER_CLASS, "endExecuteStatement", "(Ljava/lang/Object;)V", false);
		  } else {
			  this.visitLdcInsn(methodFqn);
			  this.visitMethodInsn(Opcodes.INVOKESTATIC, 
					  PROFILER_CLASS,
					  "end", 
					  "(Ljava/lang/String;)V", false);
		  }
		  super.visitInsn(inst);
		  break;
	  case Opcodes.MONITORENTER:
		  this.visitMethodInsn(Opcodes.INVOKESTATIC, 
				  PROFILER_CLASS,
				  "beginWait", 
				  "()V", false);
		  
		  super.visitInsn(inst);
		  
		  this.visitLdcInsn(methodFqn + ":" + 'M');
		  this.visitMethodInsn(Opcodes.INVOKESTATIC, 
				  PROFILER_CLASS, 
				  "endWait", 
				  "(Ljava/lang/String;)V", false);
		  break;
	  case Opcodes.ATHROW:
		  printStacktrace(isToExit);
		  super.visitInsn(inst);
		  break;
	  default:
		  super.visitInsn(inst);
		  break;
	  }
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
    char w = waitInsn(opcode, owner, name, desc);
    switch(w) {
    case 'N':
      super.visitMethodInsn(opcode, owner, name, desc, itf);
      break;
    default:
      this.visitMethodInsn(Opcodes.INVOKESTATIC, 
          PROFILER_CLASS, 
          "beginWait", 
          "()V", false);

      super.visitMethodInsn(opcode, owner, name, desc, itf);

      this.visitLdcInsn(methodFqn + ":" + w);
      this.visitMethodInsn(Opcodes.INVOKESTATIC, 
          PROFILER_CLASS, 
          "endWait",
          "(Ljava/lang/String;)V", false);
      break;
    }
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
	  visitLabel(endLbl);
	  visitTryCatchBlock(startLbl, endLbl, endLbl, "java/lang/Throwable");
	  if(this.jdbcMethodId > 0) {
		  this.visitVarInsn(Opcodes.ALOAD, jdbcMethodId > 9 ? 0 : 1);
		  this.visitMethodInsn(Opcodes.INVOKESTATIC, PROFILER_CLASS, "endExecuteStatement", "(Ljava/lang/Object;)V", false);
	  } else {
		  this.visitLdcInsn(methodFqn);
		  this.visitMethodInsn(Opcodes.INVOKESTATIC, 
				  PROFILER_CLASS,
				  "end", 
				  "(Ljava/lang/String;)V", false);
	  }
	  isToExit = true;
	  visitInsn(Opcodes.ATHROW);
	  
	  super.visitMaxs(maxStack + 2, maxLocals);
  }

  private void printStacktrace(boolean isExit) {
	  visitInsn(Opcodes.DUP);
	  if(isExit) {
		  visitMethodInsn(Opcodes.INVOKESTATIC, 
				  PROFILER_CLASS,
				  "logUnCaughtException",
				  "(Ljava/lang/Throwable;)V", false);
	  } else {
		  visitMethodInsn(Opcodes.INVOKESTATIC, 
				  PROFILER_CLASS,
				  "logException",
				  "(Ljava/lang/Throwable;)V", false);
	  }
  }
  
  private static char waitInsn(int opcode, String owner, String name, String desc) {
    if(desc == null || !BLOCK_CLASSFQN_SET.contains(owner)) {
      return 'N';
    }

    if(opcode == Opcodes.INVOKEVIRTUAL) {
      if("wait".equals(name)) {
        return 'W';
      }else if("sleep".equals(name)) {
        return 'S';
      } else if("join".equals(name)) {
        return 'J';
      } else if(name.startsWith("lock")) {
        return 'L';
      } else if("tryLock".equals(name) && "(JLjava/util/concurrent/TimeUnit;)Z".equals(desc)) {
        return 'T';
      }
    } else if(opcode == Opcodes.INVOKESTATIC) {
      if("sleep".equals(name)) {
        return 'S';
      } else if("yield".equals(name)) {
        return 'Y';
      }
    } else if(opcode == Opcodes.INVOKEINTERFACE) {
      if(name.startsWith("lock")) {
        return 'L';
      } else if(name.startsWith("await")) {
        return 'C';
      } else if("tryLock".equals(name) && "(JLjava/util/concurrent/TimeUnit;)Z".equals(desc)) {
        return 'T';
      }
    }
    return 'N';
  }

}
