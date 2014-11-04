package org.myprofiler4j.java.client.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class MethodMetrics implements Comparable<MethodMetrics>{
  protected String _methodId = "";
  protected String _methodDesc;
  
  protected int _count;
  protected int _maxTime;
  protected int _minTime;
  protected int _totalTime;
  
  protected int _waitCount;
  protected int _maxWaitTime;
  protected int _minWaitTime;
  protected int _totalWaitTime;
  
  protected String debugInfo;
  protected int _startTime;
  protected boolean isCallTreeNode;
  
  protected Map<String, MethodMetrics> children;
  
  public MethodMetrics visitChild(String methodId, int startTime, int usedTime, boolean isWait, String debugInfo) {
    if(children == null) {
      children = new HashMap<String, MethodMetrics>();
    }
    MethodMetrics child = null;
    String key = methodId;
    if(!children.containsKey(key)) {
      child = new MethodMetrics();
      child._methodId = methodId;
      child._startTime = startTime;
      child.isCallTreeNode = true;
      child.debugInfo = debugInfo;
      child.visitMethod(usedTime, isWait);
      children.put(key, child);
    } else {
      child = children.get(key);
      child.visitMethod(usedTime, isWait);
    }
    return child;
  }
  
  public void visitMethod(int usedTime, boolean isWait) {
    if(isWait) {
      _waitCount++;
      _totalWaitTime += usedTime;
    } else {
      _count++;
      _totalTime += usedTime;
    }
  }
  
  @Override
  public int compareTo(MethodMetrics m) {
    if(m.isCallTreeNode && isCallTreeNode) {
      return  _startTime - m._startTime;
    }
    return m._totalTime - _totalTime;
  }
  
  public Iterator<MethodMetrics> sortedChildrenIterator() {
    if(children == null) return null;
    List<MethodMetrics> result = new ArrayList<MethodMetrics>(children.values());
    Collections.sort(result);
    return result.iterator();
  }
}
