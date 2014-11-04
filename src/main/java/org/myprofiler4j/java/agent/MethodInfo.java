package org.myprofiler4j.java.agent;

import java.util.concurrent.atomic.AtomicInteger;

import org.myprofiler4j.java.utils.LRU;

public class MethodInfo {

  private static final int MAX_CACHE_SIZE = 6000;

  private static final LRU<String, MethodInfo> methodCache = new LRU<String, MethodInfo>(MAX_CACHE_SIZE);

  private static AtomicInteger METHOD_ID_GEN = new AtomicInteger(1);

  private static final StringBuilder STRING_BUILDER = new StringBuilder(60);

  private String methodDesc;

  int methodId;

  private MethodInfo(String methodDesc) {
    this.methodDesc = methodDesc;
  }

  void initMethodId() {
    this.methodId = METHOD_ID_GEN.getAndIncrement();
  }

  static MethodInfo putInCache(String methodDesc) {
    MethodInfo info = new MethodInfo(methodDesc);
    methodCache.put(methodDesc, info);
    return info;
  }

  static void clearCache() {
    methodCache.clear();
    METHOD_ID_GEN.set(1);
  }

  static MethodInfo getMethodInfo(String methodDesc) {
    return methodCache.get(methodDesc);
  }

  public String toString() {
    STRING_BUILDER.append(methodId);
    STRING_BUILDER.append(Profiler.SEP);
    STRING_BUILDER.append(methodDesc);
    String rs = STRING_BUILDER.toString();
    STRING_BUILDER.setLength(0);
    return rs;
  }
}
