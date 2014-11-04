package org.myprofiler4j.java.command;

import java.nio.channels.ByteChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings("serial")
public class CompositeCommand extends ProfilerCommandBase {
  protected List<Command> subCommands;

  public CompositeCommand(String cmdline) throws Exception {
    super(cmdline);
  }

  @Override
  public List<String> call() throws Exception {
    List<String> result = new ArrayList<String>();
    if (subCommands != null && subCommands.size() > 0) {
      Iterator<Command> subCmdIter = subCommands.iterator();
      while (subCmdIter.hasNext()) {
        Command subCmd = subCmdIter.next();
        result.add(subCmd.send());
      }
    }
    return result;
  }

  @Override
  public void setChannel(ByteChannel channel) {
    super.setChannel(channel);
    if (subCommands != null) {
      for (Iterator<Command> iterator = subCommands.iterator(); iterator.hasNext();) {
        Command subCommand = (Command) iterator.next();
        if (subCommand.getChannel() == null) {
          subCommand.setChannel(channel);
        }
      }
    }
  }

  @Override
  public void addSubCommand(Command subCmd) {
    if (this.subCommands == null) {
      this.subCommands = new ArrayList<Command>();
    }
    this.subCommands.add(subCmd);
  }
}
