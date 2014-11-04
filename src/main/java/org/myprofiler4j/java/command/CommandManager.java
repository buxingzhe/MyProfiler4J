package org.myprofiler4j.java.command;

import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CommandManager {
  private final static Executor EXECUTOR = Executors.newCachedThreadPool();

  private CommandManager() {
  }

  public static CommandFuture sendCommand(Command command, SocketChannel channel) {
    command.setChannel(channel);
    CommandFuture commandFuture = new CommandFutureTask(command);
    EXECUTOR.execute(commandFuture);
    return commandFuture;
  }
}
