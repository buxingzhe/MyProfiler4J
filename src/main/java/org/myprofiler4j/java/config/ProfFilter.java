/**
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package org.myprofiler4j.java.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Profile filter
 * 
 * @author nhuang
 * @since 2013-11-28
 */
public class ProfFilter {

  /**
   * Included packages
   */
  private Set<String> includePackage = new HashSet<String>();
  /**
   * Excluded packages
   */
  private Set<String> excludePackage = new HashSet<String>();
  /**
   * Excluded ClassLoaders
   */
  private Set<String> excludeClassLoader = new HashSet<String>();

  /**
   * Excluded Threads
   */
  private Set<Pattern> excludedThreadSet = Collections.synchronizedSet(new HashSet<Pattern>());
  
  private final static ProfFilter instance = new ProfFilter();

  private ProfFilter() {
  }

  public static ProfFilter instance() {
    return instance;
  }
  
  /**
   * 
   * @param className
   */
  public void addIncludePackage(String packageName) {
    String pkName = packageName.replace('.', '/');
    includePackage.add(pkName);
  }

  /**
   * 
   * @param className
   */
  public void addExcludePackage(String packageName) {
    String pkName = packageName.replace('.', '/');
    excludePackage.add(pkName);
  }

  /**
   * 
   * @param classLoader
   */
  public void addExcludeClassLoader(String classLoader) {
    excludeClassLoader.add(classLoader);
  }

  public boolean acceptClass(String className) {
    if(!isUnderIncludePackages(className) || isUnderExcludePackages(className)) {
      return false;
    }
    return true;
  }

  /**
   * Check if the class included
   * 
   * @param className
   * @return
   */
  private boolean isUnderIncludePackages(String className) {
    for (String v : includePackage) {
      if (className.startsWith(v)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if the class excluded
   * 
   * @param className
   * @return
   */
  private boolean isUnderExcludePackages(String className) {
    for (String v : excludePackage) {
      if (className.startsWith(v)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if the ClassLoader excluded
   * 
   * @param classLoader
   * @return
   */
  public boolean isExcludedClassLoader(String classLoader) {
    for (String v : excludeClassLoader) {
      if (classLoader.equals(v)) {
        return true;
      }
    }
    return false;
  }

  public String addExcludedThreadPattern(String t) {
    try {        
      excludedThreadSet.add(Pattern.compile(t));
    } catch (Exception e) {
      return e.getMessage();
    }
    return null;
  }

  public String removeExcludedThreadPattern(String t) {
    try {
      excludedThreadSet.remove(Pattern.compile(t));
    } catch (Exception e) {
      return e.getMessage();
    }
    return null;
  }

  public boolean isExcludedThreadPattern(String t) {
    Iterator<Pattern> iter = excludedThreadSet.iterator();
    while(iter.hasNext()) {
      Pattern pattern = iter.next();
      Matcher matcher = pattern.matcher(t);
      if(matcher.matches()) {
        return true;
      }
    }

    return false;
  }

  public void clear() {
    includePackage.clear();
    excludeClassLoader.clear();
    excludedThreadSet.clear();
    excludePackage.clear();
  }

  public static void main(String[] args) {
    Pattern pattern = Pattern.compile("SwingWorker-pool.*");
    Matcher matcher = pattern.matcher("SwingWorker-pool-1-thread-1");
    if(matcher.matches()) {
      System.out.println("Matched.");
    } else {
      System.out.println("Not matched.");
    }
  }
}
