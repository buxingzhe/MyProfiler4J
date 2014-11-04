package org.myprofiler4j.java.agent;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.MonitorInfo;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.sun.management.HotSpotDiagnosticMXBean;

public class ProfilerRuntime {
  private static final String LINE_SEPARATOR = System
      .getProperty("line.separator");
  private static final int THRD_DUMP_FRAMES = 1;
  private static String INDENT = "      ";
  private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

  private static volatile HotSpotDiagnosticMXBean hotspotMBean;
  private static volatile MemoryMXBean memoryMBean;
  private static volatile RuntimeMXBean runtimeMBean;
  private static volatile ThreadMXBean threadMBean;
  private static volatile List<GarbageCollectorMXBean> gcBeanList;
  private static volatile List<MemoryPoolMXBean> memPoolList;

  public static ThreadMXBean getThreadMXBean() {
    initThreadMBean();
    return threadMBean;
  }

  //==========================================================================================
  static MemoryUsage heapUsage() {
    initMemoryMBean();
    return memoryMBean.getHeapMemoryUsage();
  }

  static MemoryUsage nonHeapUsage() {
    initMemoryMBean();
    return memoryMBean.getNonHeapMemoryUsage();
  }

  static long finalizationCount() {
    initMemoryMBean();
    return memoryMBean.getObjectPendingFinalizationCount();
  }

  static long vmStartTime() {
    initRuntimeMBean();
    return runtimeMBean.getStartTime();
  }

  static long vmUptime() {
    initRuntimeMBean();
    return runtimeMBean.getUptime();
  }

  static List<String> getInputArguments() {
    initRuntimeMBean();
    return runtimeMBean.getInputArguments();
  }

  static String getVmVersion() {
    initRuntimeMBean();
    return runtimeMBean.getVmVersion();
  }

  static boolean isBootClassPathSupported() {
    initRuntimeMBean();
    return runtimeMBean.isBootClassPathSupported();
  }

  static String getBootClassPath() {
    initRuntimeMBean();
    return runtimeMBean.getBootClassPath();
  }

  static long getThreadCount() {
    initThreadMBean();
    return threadMBean.getThreadCount();
  }

  static long getDaemonThreadCount() {
    initThreadMBean();
    return threadMBean.getDaemonThreadCount();
  }

  static long getCurrentThreadCpuTime() {
    initThreadMBean();
    threadMBean.setThreadCpuTimeEnabled(true);
    return threadMBean.getCurrentThreadCpuTime();
  }

  static long getCurrentThreadUserTime() {
    initThreadMBean();
    threadMBean.setThreadCpuTimeEnabled(true);
    return threadMBean.getCurrentThreadUserTime();
  }

  static void dumpHeap(String fileName, boolean live) {
    initHotspotMBean();
    try {
      String name = resolveFileName(fileName);
      hotspotMBean.dumpHeap(name, live);
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception exp) {
      throw new RuntimeException(exp);
    }
  }

  private static String resolveFileName(String name) {
    if (name.indexOf(File.separatorChar) != -1) {
      throw new IllegalArgumentException("directories are not allowed");
    }
    StringBuilder buf = new StringBuilder();
    buf.append("." + File.separatorChar + "myprofiler4j");
    buf.append(File.separatorChar);
    new File(buf.toString()).mkdirs();
    buf.append(File.separatorChar);
    buf.append(name);
    return buf.toString();
  }

  static long getTotalGcTime() {
    initGarbageCollectionBeans();
    long totalGcTime = 0;
    for (GarbageCollectorMXBean gcBean : gcBeanList) {
      totalGcTime += gcBean.getCollectionTime();
    }
    return totalGcTime;
  }

  static String getMemoryPoolUsage(String poolFormat) {
    if (poolFormat == null) {
      poolFormat = "%1$s;%2$d;%3$d;%4$d;%5$d";
    }
    Object[][] poolOutput = new Object[memPoolList.size()][5];

    StringBuilder membuffer = new StringBuilder();

    for (int i = 0; i < memPoolList.size(); i++) {
      MemoryPoolMXBean memPool = memPoolList.get(i);
      poolOutput[i][0] = memPool.getName();
      poolOutput[i][1] = new Long(memPool.getUsage().getMax());
      poolOutput[i][2] = new Long(memPool.getUsage().getUsed());
      poolOutput[i][3] = new Long(memPool.getUsage().getCommitted());
      poolOutput[i][4] = new Long(memPool.getUsage().getInit());

    }
    for (Object[] memPoolOutput : poolOutput) {
      membuffer.append(String.format(poolFormat, memPoolOutput)).append("\n");
    }

    return membuffer.toString();
  }

  private static void initHotspotMBean() {
    if (hotspotMBean == null) {
      synchronized (ProfilerRuntime.class) {
        if (hotspotMBean == null) {
          hotspotMBean = getHotspotMBean();
        }
      }
    }
  }

  private static HotSpotDiagnosticMXBean getHotspotMBean() {
    try {
      return AccessController.doPrivileged(
          new PrivilegedExceptionAction<HotSpotDiagnosticMXBean>() {
            public HotSpotDiagnosticMXBean run() throws Exception {
              MBeanServer server = ManagementFactory.getPlatformMBeanServer();
              Set<ObjectName> s = server.queryNames(new ObjectName(HOTSPOT_BEAN_NAME), null);
              Iterator<ObjectName> itr = s.iterator();
              if (itr.hasNext()) {
                ObjectName name = itr.next();
                HotSpotDiagnosticMXBean bean =
                    ManagementFactory.newPlatformMXBeanProxy(server,
                        name.toString(), HotSpotDiagnosticMXBean.class);
                return bean;
              } else {
                return null;
              }
            }
          });
    } catch (Exception exp) {
      throw new UnsupportedOperationException(exp);
    }
  }

  private static void initMemoryMBean() {
    if (memoryMBean == null) {
      synchronized (ProfilerRuntime.class) {
        if (memoryMBean == null) {
          memoryMBean = getMemoryMBean();
        }
      }
    }
  }

  private static MemoryMXBean getMemoryMBean() {
    try {
      return AccessController.doPrivileged(
          new PrivilegedExceptionAction<MemoryMXBean>() {
            public MemoryMXBean run() throws Exception {
              return ManagementFactory.getMemoryMXBean();
            }
          });
    } catch (Exception exp) {
      throw new UnsupportedOperationException(exp);
    }
  }

  private static void initRuntimeMBean() {
    if (runtimeMBean == null) {
      synchronized (ProfilerRuntime.class) {
        if (runtimeMBean == null) {
          runtimeMBean = getRuntimeMBean();
        }
      }
    }
  }

  private static RuntimeMXBean getRuntimeMBean() {
    try {
      return AccessController.doPrivileged(
          new PrivilegedExceptionAction<RuntimeMXBean>() {
            public RuntimeMXBean run() throws Exception {
              return ManagementFactory.getRuntimeMXBean();
            }
          });
    } catch (Exception exp) {
      throw new UnsupportedOperationException(exp);
    }
  }

  /*private static List<MemoryPoolMXBean> getMemoryPoolMXBeans() {
    try {
        return AccessController.doPrivileged(
            new PrivilegedExceptionAction<List<MemoryPoolMXBean>>() {
                public List<MemoryPoolMXBean> run() throws Exception {
                    return ManagementFactory.getMemoryPoolMXBeans();
                }
            });
    } catch (Exception exp) {
        throw new UnsupportedOperationException(exp);
    }
}*/

  private static List<GarbageCollectorMXBean> getGarbageCollectionMBeans() {
    try {
      return AccessController.doPrivileged(
          new PrivilegedExceptionAction<List<GarbageCollectorMXBean>>() {
            public List<GarbageCollectorMXBean> run() throws Exception {
              return ManagementFactory.getGarbageCollectorMXBeans();
            }
          });
    } catch (Exception exp) {
      throw new UnsupportedOperationException(exp);
    }
  }

  private static void initGarbageCollectionBeans() {
    if (gcBeanList == null) {
      synchronized (ProfilerRuntime.class) {
        if (gcBeanList == null) {
          gcBeanList = getGarbageCollectionMBeans();
        }
      }
    }
  }

  /*private static void initMemoryPoolList() {
    if (memPoolList == null) {
        synchronized (ProfilerRuntime.class) {
            if (memPoolList == null) {
                memPoolList = getMemoryPoolMXBeans();
            }
        }
    }
}*/





  //============================================================================================



  // stack trace functions
  private static String stackTraceAllStr(int numFrames, boolean printWarning) {
    Set<Map.Entry<Thread, StackTraceElement[]>> traces = Thread
        .getAllStackTraces().entrySet();
    StringBuilder buf = new StringBuilder();
    for (Map.Entry<Thread, StackTraceElement[]> t : traces) {
      buf.append(t.getKey().toString());
      buf.append(LINE_SEPARATOR);
      buf.append(LINE_SEPARATOR);
      StackTraceElement[] st = t.getValue();
      buf.append(stackTraceStr("\t", st, 0, numFrames, printWarning));
      buf.append(LINE_SEPARATOR);
    }
    return buf.toString();
  }

  public static String stackTraceAllStr(int numFrames) {
    return stackTraceAllStr(numFrames, false);
  }

  private static String stackTraceStr(String prefix, StackTraceElement[] st,
      int strip, int numFrames, boolean printWarning) {
    strip = strip > 0 ? strip + THRD_DUMP_FRAMES : 0;
    numFrames = numFrames > 0 ? numFrames : st.length - strip;

    int limit = strip + numFrames;
    limit = limit <= st.length ? limit : st.length;

    if (prefix == null) {
      prefix = "";
    }

    StringBuilder buf = new StringBuilder();
    for (int i = strip; i < limit; i++) {
      buf.append(prefix);
      buf.append(st[i].toString());
      buf.append(LINE_SEPARATOR);
    }
    if (printWarning && limit < st.length) {
      buf.append(prefix);
      buf.append(st.length - limit);
      buf.append(" more frame(s) ...");
      buf.append(LINE_SEPARATOR);
    }
    return buf.toString();
  }

  private static void initThreadMBean() {
    if (threadMBean == null) {
      synchronized (ProfilerRuntime.class) {
        if (threadMBean == null) {
          threadMBean = getThreadMBean();
        }
      }
    }
  }

  private static ThreadMXBean getThreadMBean() {
    try {
      return AccessController
          .doPrivileged(new PrivilegedExceptionAction<ThreadMXBean>() {
            public ThreadMXBean run() throws Exception {
              return ManagementFactory.getThreadMXBean();
            }
          });
    } catch (Exception exp) {
      throw new UnsupportedOperationException(exp);
    }
  }

  public static String getVmInfo() {
    String vmName = System.getProperty("java.vm.name");
    String vmVersion = System.getProperty("java.vm.version");
    String vmInfo = System.getProperty("java.vm.info");
    String vmVendor = System.getProperty("java.vm.vendor");
    String javaVer = System.getProperty("java.runtime.version");
    return vmVendor + " " + vmName + " (" + javaVer + " " + vmVersion + " "
    + vmInfo + ")";
  }

  public static String getOSInfo() {
    String osName = System.getProperty("os.name");
    String osVersion = System.getProperty("os.version");
    String osArch = System.getProperty("os.arch");
    return osName + " " + osVersion + " " + osArch;
  }

  public static long getTotalStartedThreadCount() {
    initThreadMBean();
    return threadMBean.getTotalStartedThreadCount();
  }

  public static int getPeakThreadCount() {
    initThreadMBean();
    return threadMBean.getPeakThreadCount();
  }

  public static int getLiveThreadCount() {
    initThreadMBean();
    return threadMBean.getThreadCount();
  }

  public static String deadlocks(boolean stackTrace) {
    initThreadMBean();
    long[] tids = null;
    if (threadMBean.isSynchronizerUsageSupported()) {
      tids = threadMBean.findDeadlockedThreads();
    } else {
      tids = threadMBean.findMonitorDeadlockedThreads();
    }

    if (tids != null && tids.length > 0) {
      ThreadInfo[] infos = threadMBean.getThreadInfo(tids, true, true);
      StringBuilder sb = new StringBuilder();
      for (ThreadInfo ti : infos) {
        getThreadStackTrace(stackTrace, ti, sb);
      }
      return sb.toString();
    }
    return null;
  }

  public static void getThreadStackTrace(boolean stackTrace, ThreadInfo ti,
      StringBuilder sb) {
    sb.append("\"" + ti.getThreadName() + "\"" + " tid=" + ti.getThreadId());
    sb.append(LINE_SEPARATOR);

    sb.append("   java.lang.Thread.State: " + ti.getThreadState());
    if (ti.getLockName() != null) {
      sb.append(" on [" + ti.getLockName() + "]");
      if (ti.getLockOwnerName() != null) {
        sb.append(" owned by " + ti.getLockOwnerName() + " tid="
            + ti.getLockOwnerId());
      }
    }
    if (ti.isSuspended()) {
      sb.append(" (suspended)");
    }
    if (ti.isInNative()) {
      sb.append(" (in native)");
    }
    sb.append(LINE_SEPARATOR);

    if (stackTrace) {
      // print stack trace with locks
      StackTraceElement[] stacktrace = ti.getStackTrace();
      int i = 0;
      for (; i < stacktrace.length; i++) {
        StackTraceElement ste = stacktrace[i];
        sb.append(INDENT + "at " + ste.toString());
        sb.append(LINE_SEPARATOR);

        if (i == 0 && ti.getLockInfo() != null) {
          Thread.State ts = ti.getThreadState();
          switch (ts) {
          case BLOCKED:
            sb.append(INDENT + "- blocked on " + ti.getLockInfo());
            sb.append(LINE_SEPARATOR);
            break;
          case WAITING:
            sb.append(INDENT + "- waiting on " + ti.getLockInfo());
            sb.append(LINE_SEPARATOR);
            break;
          case TIMED_WAITING:
            sb.append(INDENT + "- waiting on " + ti.getLockInfo());
            sb.append(LINE_SEPARATOR);
            break;
          default:
            break;
          }
        }

        for (MonitorInfo mi : ti.getLockedMonitors()) {
          if (mi.getLockedStackDepth() == i) {
            sb.append(INDENT + "- locked " + mi);
            sb.append(LINE_SEPARATOR);
          }
        }
      }
      if (i < ti.getStackTrace().length) {
        sb.append(INDENT + "...");
        sb.append(LINE_SEPARATOR);
      }
      sb.append(LINE_SEPARATOR);
    }

    sb.append("   Locked ownable synchronizers:");
    sb.append(LINE_SEPARATOR);
    LockInfo[] locks = ti.getLockedSynchronizers();
    if (locks.length > 0) {
      for (LockInfo li : locks) {
        sb.append(INDENT + "- " + li);
        sb.append(LINE_SEPARATOR);
      }
    } else {
      sb.append(INDENT + "- None");
    }
    sb.append(LINE_SEPARATOR);
    sb.append(LINE_SEPARATOR);
  }
}
