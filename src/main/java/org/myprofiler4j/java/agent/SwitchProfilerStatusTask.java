package org.myprofiler4j.java.agent;

import java.util.Calendar;
import java.util.TimerTask;

import org.myprofiler4j.java.config.ProfConfig;
import org.myprofiler4j.java.utils.Utilities;

public class SwitchProfilerStatusTask extends TimerTask{

  private final boolean switchValue;
  
  public SwitchProfilerStatusTask(boolean switchValue) {
    this.switchValue = switchValue;
  }
  
  @Override
  public void run() {
    Profiler.isEnabled = switchValue;
    if(switchValue) {
      ProfConfig.instance.disableAtNextInValidTimeAfterNow();
      Profiler.METHOD_PROFILE_QUEUE.offer("#[Scheduler] - Profiler enabled at " + Utilities.getDateString(Calendar.getInstance().getTime()));
    } else {
      ProfConfig.instance.enableAtNextValidTimeAfterNow();
      Profiler.METHOD_PROFILE_QUEUE.offer("#[Scheduler] - Profiler disabled at " + Utilities.getDateString(Calendar.getInstance().getTime()));
    }
  }
}
