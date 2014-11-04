package org.myprofiler4j.java.command;

@SuppressWarnings("serial")
public class SimpleCommand extends ProfilerCommandBase {
  public SimpleCommand(String cmdLine) throws Exception {
    super(cmdLine);
  }

  public SimpleCommand(String cmdLine, int sendBufferSize) throws Exception {
    super(cmdLine, sendBufferSize);
  }
}
