/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package org.myprofiler4j.java.agent.instrument;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.myprofiler4j.java.asm.ClassVisitor;
import org.myprofiler4j.java.asm.FieldVisitor;
import org.myprofiler4j.java.asm.MethodVisitor;
import org.myprofiler4j.java.asm.Opcodes;
import org.myprofiler4j.java.config.ProfConfig;


/**
 * Profiler ClassVistor
 * 
 * @author nhuang
 * @since 2013-11-28
 */
public class ProfClassVisitor extends ClassVisitor {
    private final static String CLINIT    = "<clinit>";
    private final static String INIT      = "<init>";
    private final static Map<String, String> STATEMENT_METHODS = new HashMap<String, String>(10, 1);
    private final static Map<String, String> PREPARED_STMT_METHODS = new HashMap<String, String>(5, 1);
    
    static {
      STATEMENT_METHODS.put("execute(Ljava/lang/String;)Z", "1");
      STATEMENT_METHODS.put("execute(Ljava/lang/String;I)Z", "2");
      STATEMENT_METHODS.put("execute(Ljava/lang/String;[I)Z", "3");
      STATEMENT_METHODS.put("execute(Ljava/lang/String;[Ljava/lang/String;)Z", "4");
      STATEMENT_METHODS.put("executeQuery(Ljava/lang/String;)Ljava/sql/ResultSet;", "5");
      STATEMENT_METHODS.put("executeUpdate(Ljava/lang/String;)I", "6");
      STATEMENT_METHODS.put("executeUpdate(Ljava/lang/String;I)I", "7");
      STATEMENT_METHODS.put("executeUpdate(Ljava/lang/String;[I)I", "8");
      STATEMENT_METHODS.put("executeUpdate(Ljava/lang/String;[Ljava/lang/String;)I", "9");
      PREPARED_STMT_METHODS.put("execute()Z", "10");
      PREPARED_STMT_METHODS.put("executeQuery()Ljava/sql/ResultSet;", "11");
      PREPARED_STMT_METHODS.put("executeUpdate()I", "12");
      PREPARED_STMT_METHODS.put("executeBatch()[I", "13");
    }
    
	/**
	 * Class name
	 */
	private String mClassName;
	
	/**
	 * Field name list
	 */
	private Set<String> fieldNameList = new HashSet<String>();

	/**
	 * @param visitor
	 * @param theClass
	 */
	public ProfClassVisitor(ClassVisitor visitor, String theClass) {
		super(Opcodes.ASM5, visitor);
		this.mClassName = theClass.replace('/', '.');
	}
	
	private byte getStatementMethodType(String methodName, String desc) {
	  if(ProfConfig.instance.jdbcStatementClasses.indexOf(this.mClassName) != -1) {
	    String methodDesc = methodName + desc;
	    if(STATEMENT_METHODS.containsKey(methodDesc)) {
	      return Byte.valueOf(STATEMENT_METHODS.get(methodDesc));
	    } else if(PREPARED_STMT_METHODS.containsKey(methodDesc)) {
	      return Byte.valueOf(PREPARED_STMT_METHODS.get(methodDesc));
	    }
	  }
	  return 0;
	}
	
	/*@Override
	public void visitSource(String source, String debug) {
		super.visitSource(source, debug);
		this.mFileName = source;
	}*/

	/* (non-Javadoc)
	 * @see org.objectweb.asm.ClassVisitor#visitField(int, java.lang.String, java.lang.String, java.lang.String, java.lang.Object)
	 */
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		if(ProfConfig.instance.ignoreGetSetMethod) {
			String up = Character.toUpperCase(name.charAt(0)) + name.substring(1);
			String getFieldName = "get" + up;
			String setFieldName = "set" + up;
			String isFieldName = "is" + up;
			fieldNameList.add(getFieldName);
			fieldNameList.add(setFieldName);
			fieldNameList.add(isFieldName);
		}

		return super.visitField(access, name, desc, signature, value);
	}

	/* (non-Javadoc)
	 * @see org.objectweb.asm.ClassVisitor#visitMethod(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
	 */
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		if (ProfConfig.instance.ignoreGetSetMethod && fieldNameList.contains(name)) {
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}
		// Skip static code area
		if (CLINIT.equals(name) || (!ProfConfig.instance.isTraceObjectAlloc && INIT.equals(name))) {
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}

		MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
		ProfMethodVisitor pmv = null;
		if(ProfConfig.instance.isOutputMethodSignature && descriptor != null) {
			pmv = new ProfMethodVisitor(mv, mClassName, name + descriptor);
		} else {
			pmv = new ProfMethodVisitor(mv, mClassName, name);
		}
		pmv.jdbcMethodId = getStatementMethodType(name, descriptor);
		return pmv;
	}
	
	@Override
	public void visitEnd() {
	  super.visitEnd();
	  fieldNameList.clear();
	  fieldNameList = null;
	}

}
