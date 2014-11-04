package org.myprofiler4j.java.command;

import java.util.List;
import java.util.concurrent.FutureTask;

public class CommandFutureTask extends FutureTask<List<String>> implements
    CommandFuture {
  private Command cmd;

  public CommandFutureTask(Command cmd) {
    super(cmd);
    this.cmd = cmd;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean v = super.cancel(mayInterruptIfRunning);
    if (v) {
      cmd.exit();
    }
    return v;
  }

  @Override
  protected void done() {
    super.done();
    cmd.exit();
  }

  @Override
  public Command getCommand() {
    return cmd;
  }
}
