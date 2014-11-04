package org.myprofiler4j.java.command;

import java.util.TimerTask;

public class CommandTimerTask extends TimerTask {

  private Command cmd;

  private int times;

  private int counter = 0;

  private Object lock = null;

  public CommandTimerTask(Command cmd, int times) {
    this.cmd = cmd;
    this.times = times;
  }

  public void setLockObject(Object lock) {
    this.lock = lock;
  }

  private void notifyWaitor() {
    if (lock != null) {
      synchronized (lock) {
        lock.notify();
      }
    }
  }

  public String getResult() {
    if (counter == times) {
      return "Fully completed.";
    } else if (counter > 0) {
      return "Partially completed.";
    } else {
      return "Failed.";
    }
  }

  @Override
  public void run() {
    try {
      System.out.println(cmd.send());
      cmd.exit();
      counter++;
      if (times == counter) {
        this.cancel();
        notifyWaitor();
      }
    } catch (Throwable e) {
      this.cancel();
      notifyWaitor();
      e.printStackTrace();
    }
  }
}
