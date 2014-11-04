package org.myprofiler4j.java.agent;

public class ClassAllocation {

  protected String _classContructor;

  protected volatile int _count = 0;

  protected volatile int _usedTime = 0;

  @Override
  public String toString() {
    return _classContructor + Profiler.SEP + _count + Profiler.SEP + _usedTime;
  }

  public void reset() {
    _count = 0;
    _usedTime = 0;
  }
}
