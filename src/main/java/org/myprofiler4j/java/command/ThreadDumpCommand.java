package org.myprofiler4j.java.command;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.myprofiler4j.java.utils.Utilities;

@SuppressWarnings("serial")
public class ThreadDumpCommand extends ProfilerCommandBase {
  private int interval = 0;
  private int times = 0;
  private final Object lock = new Object();

  public ThreadDumpCommand(String commandline, int sendBufferSize)
      throws Exception {
    super(commandline, sendBufferSize);
    receiveBufferSize = -1;
    if (commandArgs.size() > 1) {
      interval = Integer.valueOf(getArgument(1));
      times = Integer.valueOf(getArgument(2));
    }
  }

  @Override
  protected void validateCommand() throws Exception {
    if (commandArgs.size() == 1) {
      return;
    }
    if (commandArgs.size() > 1 && commandArgs.size() < 3) {
      throw new IllegalArgumentException("Miss argument.");
    }
    Integer.valueOf(getArgument(1));
    Integer.valueOf(getArgument(2));
  }

  @Override
  protected String execute() throws Exception {
    if (commandArgs.size() == 1) {
      return super.send();
    }
    CommandTimerTask task = new CommandTimerTask(this, times);
    task.setLockObject(lock);
    Utilities.scheduleTask(task, 0, interval * 1000);
    synchronized (lock) {
      lock.wait(interval * 1000 * times);
    }
    return task.getResult();
  }

  @Override
  protected String writeFile(ByteBuffer bf) throws IOException {
    String fn = null;
    if (bf != null) {
      String content = Utilities.toString(bf);
      if (writer == null) {
        String name = content.substring(0, content.indexOf('\n')).trim();
        File file = new File(name);
        writer = new FileWriter(file, true);
        fn = file.getAbsolutePath();
      }
      writer.write(content);
      writer.flush();
    }
    return fn;
  }
}
