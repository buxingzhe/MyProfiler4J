package org.myprofiler4j.java.command;

import java.util.List;
import java.util.concurrent.RunnableFuture;

public interface CommandFuture extends RunnableFuture<List<String>> {
  public Command getCommand();
}
