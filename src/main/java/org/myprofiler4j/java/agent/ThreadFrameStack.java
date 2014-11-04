package org.myprofiler4j.java.agent;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.myprofiler4j.java.config.ProfFilter;

public class ThreadFrameStack {
	private final static transient Logger logger = Logger.getLogger(ThreadFrameStack.class.getName());
    private final static int DEFAULT_STACK_DEPTH = 1 << 3;
    final static int MAX_STACK_DEPTH = 1 << 9;
    boolean logEnabled = true;
    boolean isExcluded = false;
    int[] timeStack;
    int waitStartTime;
    int stmtStartTime;
    int depth = 0;
    int cursor = -1;
    // For SQL profile
    byte stmtMethodId;
    byte stmtDepth = 0;
    // For debug
    String sourceFile;
    int lineNumber;
    Throwable topStackTrace;
    Throwable lastException;
    
    final ThreadInfo threadInfo;
    
    protected ThreadFrameStack() {
        String threadName = Thread.currentThread().getName();
        threadInfo = new ThreadInfo();
        threadInfo.threadId = Thread.currentThread().getId();
        threadInfo.threadName = threadName.replace(Profiler.SEP, "-");
        if (ProfFilter.instance().isExcludedThreadPattern(threadName)) {
            isExcluded = true;
        } else {
        	Profiler.THREAD_QUEUE.offer(threadInfo);
        	timeStack = new int[DEFAULT_STACK_DEPTH];
        }
    }

    protected void pushStartTime(int startTime) {
        if (++cursor == timeStack.length) {
            int[] newTimeStack = new int[timeStack.length << 1];
            System.arraycopy(timeStack, 0, newTimeStack, 0, timeStack.length);
            timeStack = newTimeStack;
        }
        timeStack[cursor] = startTime;
    }

    protected int getStartTime() {
    	if(cursor < 0) {
    		logger.log(Level.SEVERE, "Oh God, Impossible!!!ArrayIndexOutOfBoundsException in thread: " + threadInfo.threadName, new Exception());
    		return (int)(System.currentTimeMillis() - Profiler.startProfileTime);
    	}
        return timeStack[cursor];
    }

    protected void crawlCallLocation(String mDesc) {
        try {
            this.sourceFile = "?";
            this.lineNumber = 0;
            int d = mDesc.indexOf('(');
            if (d != -1) {
                mDesc = mDesc.substring(0, d);
            }
            StackTraceElement[] stackTraces = null;
            if(this.topStackTrace == null) {
            	this.topStackTrace = new Throwable();
            }
            int offset = 1;
            if(this.depth == 1) {
            	stackTraces = this.topStackTrace.getStackTrace();
            	this.topStackTrace = null;
            	offset = 0;
            } else {
            	stackTraces = new Throwable().getStackTrace();
            }
            for (int i = 3; i < stackTraces.length; i++) {
                if (mDesc.equals(stackTraces[i].getClassName() + "." + stackTraces[i].getMethodName())) {
                    this.sourceFile = stackTraces[i + offset].getFileName();
                    this.lineNumber = stackTraces[i + offset].getLineNumber();
                    break;
                }
            }
        } catch (Exception e) {
        }
    }
}
