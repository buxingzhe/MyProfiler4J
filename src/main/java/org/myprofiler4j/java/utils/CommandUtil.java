package org.myprofiler4j.java.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.myprofiler4j.java.command.Command;
import org.myprofiler4j.java.config.CronExpression;

/**
 * Utility class for command handle
 * @author nhuang
 */
public class CommandUtil {
  public static byte getOperand(String cmdStr) {
    if(cmdStr == null || cmdStr.isEmpty()) {
      throw new UnsupportedOperationException("Unkown command.");
    } else {
      cmdStr = cmdStr.trim().toLowerCase();
    }
    if(cmdStr.startsWith(Command.CMD_LOGIN)) {
      return Command.LOGIN;
    }else if(cmdStr.startsWith(Command.CMD_ADDUSER)) {
      return Command.ADDUSER;
    }else if(cmdStr.startsWith(Command.CMD_PASSWD)) {
      return Command.PASSWD;
    }else if(cmdStr.startsWith(Command.CMD_START)) {
      return Command.START;
    }else if(cmdStr.startsWith(Command.CMD_STOP)) {
      return Command.STOP;
    }else if(cmdStr.startsWith(Command.CMD_STATUS)) {
      return Command.STATUS;
    }else if(cmdStr.startsWith(Command.CMD_FLUSH)) {
      return Command.FLUSH;
    }else if(cmdStr.startsWith(Command.CMD_OUTPUTBLOCKTIME)) {
      return Command.OUTPUTBLOCKTIME;
    }else if(cmdStr.startsWith(Command.CMD_CHANGETHRESHOLD)) {
      return Command.CHANGETHRESHOLD;
    }else if(cmdStr.startsWith(Command.CMD_RUNCLASS)) {
      return Command.RUNCLASS;
    }else if(cmdStr.startsWith(Command.CMD_SCHEDULE)) {
      return Command.SCHEDULE;
    }else if(cmdStr.startsWith(Command.CMD_EXCLUDETHREAD)) {
      return Command.EXCLUDETHREAD;
    }else if(cmdStr.startsWith(Command.CMD_THREADDUMP)) {
      return Command.THREADDUMP;
    }else if(cmdStr.startsWith(Command.CMD_REDEFINECLASS)) {
      return Command.REDEFINECLASS;
    }else if(cmdStr.startsWith(Command.CMD_PKILL)) {
      return Command.PKILL;
    }else if(cmdStr.startsWith(Command.CMD_LOG)) {
      return Command.LOG;
    }
    throw new UnsupportedOperationException("Unkown command.");
  }

  public static String getCommandName(byte operand) {
    switch (operand) {
    case Command.START:
      return Command.CMD_START;
    case Command.STOP:
      return Command.CMD_STOP;
    case Command.STATUS:
      return Command.CMD_STATUS;
    case Command.CHANGETHRESHOLD:
      return Command.CMD_CHANGETHRESHOLD;
    case Command.EXCLUDETHREAD:
      return Command.CMD_EXCLUDETHREAD;
    case Command.FLUSH:
      return Command.CMD_FLUSH;
    case Command.OUTPUTBLOCKTIME:
      return Command.CMD_OUTPUTBLOCKTIME;
    case Command.REDEFINECLASS:
      return Command.CMD_REDEFINECLASS;
    case Command.RUNCLASS:
      return Command.CMD_RUNCLASS;
    case Command.SCHEDULE:
      return Command.CMD_SCHEDULE;
    case Command.THREADDUMP:
      return Command.CMD_THREADDUMP;
    case Command.PKILL:
      return Command.CMD_PKILL;
    case Command.LOGIN:
      return Command.CMD_LOGIN;
    case Command.ADDUSER:
      return Command.CMD_ADDUSER;
    case Command.PASSWD:
      return Command.CMD_PASSWD;
    case Command.LOG:
      return Command.CMD_LOG;
    default:
      break;
    }
    return "Unknown";
  }

  public static void checkCommand(byte operand, String[] args) throws Exception{
    if(operand < 16) {
      return;
    }

    if(args == null || args.length < 2 || args[1] == null) {
      throw new IllegalArgumentException("No argument specified.");
    }

    switch (operand) {
    case Command.OUTPUTBLOCKTIME:
      if(!Command.ENABLE.equalsIgnoreCase(args[1]) && !Command.DISABLE.equalsIgnoreCase(args[1])) {
        throw new IllegalArgumentException("Argument error, it should be enable or disable.");
      }
      break;
    case Command.CHANGETHRESHOLD:
      Integer.valueOf(args[1]);
      break;
    case Command.EXCLUDETHREAD:
      if(args[1].charAt(0) != '+' && args[1].charAt(0) != '-') {
        throw new IllegalArgumentException("Command format error.");
      }
      Pattern.compile(args[1].substring(1));
      break;
    case Command.SCHEDULE:
      if(Command.CANCEL.equalsIgnoreCase(args[1])) {
        return;
      } else if(args.length < 3 || args[1] == null || args[2] == null) {
        throw new IllegalArgumentException("Command format error.");
      }
      Integer.valueOf(args[1]);
      CronExpression.validateExpression(args[2]);
      break;
    case Command.LOG:
      Level.parse(args[1].toUpperCase());
      break;
    default:
      break;
    }
  }

  public static List<String> parseCommandLine(String cmdline) {
    if(cmdline == null || cmdline.isEmpty()) {
      return null;
    }
    StringTokenizer tokennizer = new StringTokenizer(cmdline);
    String operand = tokennizer.nextToken();
    String arg1 = null, arg2 = "";
    List<String> args = new ArrayList<String>(3);
    if(tokennizer.hasMoreTokens()){
      arg1 = tokennizer.nextToken();
      while(tokennizer.hasMoreTokens()) {
        arg2 += (tokennizer.nextToken() + " ");
      }
      arg2 = arg2.trim();
    }
    if(arg2.length() == 0) {
      arg2 = null;
    }

    args.add(operand);

    if(arg1 != null) {
      args.add(arg1);
    }
    if(arg2 != null) {
      args.add(arg2);
    }

    return args;
  }
}
