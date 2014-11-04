package org.myprofiler4j.java.agent;

public class ThreadInfo {

  protected long threadId;

  protected String threadName;

  @Override
  public String toString() {
    return threadId + Profiler.SEP + threadName;
  }

}
