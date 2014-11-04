package org.myprofiler4j.java.command;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.myprofiler4j.java.utils.Utilities;

@SuppressWarnings("serial")
public class RedefineClassCommand extends CompositeCommand {
  private static Map<String, Long> FILE_LAST_MODIFY_DATE = new HashMap<String, Long>();

  public RedefineClassCommand(String commandLine) throws Exception {
    super(commandLine);
  }

  protected void initArgument() throws Exception {
    redefineClasses();
  }

  @Override
  public List<String> call() throws Exception {
    List<String> result = new ArrayList<String>();
    if (subCommands != null && subCommands.size() > 0) {
      Iterator<Command> subCmdIter = subCommands.iterator();
      while (subCmdIter.hasNext()) {
        ProfilerCommandBase subCmd = (ProfilerCommandBase) subCmdIter.next();
        result.add(subCmd.commandArgs.get(1) + " - " + subCmd.send());
      }
    }
    return result;
  }

  private void redefineClasses() throws Exception {
    List<File> fl = new ArrayList<File>();
    String clzFilePath = getArgument(1);
    String classFolder = Utilities.getProfilerHomePath()
        + Utilities.FILE_SEPARATOR + "classes";
    if (clzFilePath != null) {
      File f = new File(clzFilePath);
      if (!f.exists()) {
        f = new File(classFolder + Utilities.FILE_SEPARATOR + clzFilePath);
        if (!f.exists()) {
          throw new FileNotFoundException("File not found: " + clzFilePath);
        }
      }
      fl.add(f);
    } else {
      File clzfd = new File(classFolder);
      if (!clzfd.exists() || !clzfd.isDirectory()) {
        throw new FileNotFoundException("Profiler class folder not exist: "
            + classFolder);
      }
      File[] classFiles = clzfd.listFiles(new FileFilter() {
        @Override
        public boolean accept(File f) {
          if (f.getName().endsWith(".class")) {
            return true;
          }
          return false;
        }
      });
      for (int i = 0; i < classFiles.length; i++) {
        String fn = classFiles[i].getName();
        long modifyDate = classFiles[i].lastModified();
        Long lmd = FILE_LAST_MODIFY_DATE.get(fn);
        if (lmd != null && lmd == modifyDate) {
          continue;
        }
        FILE_LAST_MODIFY_DATE.put(fn, modifyDate);
        if (fn.indexOf('$') != -1) {
          fl.add(0, classFiles[i]);
        } else {
          fl.add(classFiles[i]);
        }
      }
    }

    if (fl.size() > 0) {
      for (Iterator<File> iterator = fl.iterator(); iterator.hasNext();) {
        File clzFile = null;
        try {
          clzFile = (File) iterator.next();
          String clzFQN = Utilities.getClassFQN(new FileInputStream(clzFile));
          Command subCommand = new SimpleCommand(Command.CMD_REDEFINECLASS,
              (int) (clzFile.length() + 128));
          subCommand.appendArgument(clzFQN);
          subCommand.appendArgument(clzFile);
          addSubCommand(subCommand);
        } catch (Exception e) {
          System.out.println("Redefine " + clzFile.getName() + " failure: "
              + e.getMessage());
          throw e;
        }
      }
    } else {
      System.out
          .println("No class redefined as no class modified since last redefinition.");
    }
  }

}
