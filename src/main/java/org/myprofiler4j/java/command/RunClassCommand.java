package org.myprofiler4j.java.command;

import java.io.File;
import java.io.FileNotFoundException;

import org.myprofiler4j.java.utils.Utilities;

@SuppressWarnings("serial")
public class RunClassCommand extends ProfilerCommandBase {

  public RunClassCommand(String commandline, int sendBufferSize)
      throws Exception {
    super(commandline, sendBufferSize);
  }

  protected void initArgument() throws Exception {
    initCommandBuffer();
    String clzFilePath = getArgument(1);
    File f = new File(clzFilePath);
    if (!f.exists()) {
      String classFolder = Utilities.getProfilerHomePath()
          + Utilities.FILE_SEPARATOR + "classes";
      f = new File(classFolder + Utilities.FILE_SEPARATOR + clzFilePath);
      if (!f.exists()) {
        throw new FileNotFoundException("File not found: " + clzFilePath);
      }
    }

    appendArgument(f);
  }
}
