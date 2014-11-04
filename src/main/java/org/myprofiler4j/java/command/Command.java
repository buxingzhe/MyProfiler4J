package org.myprofiler4j.java.command;

import java.io.Serializable;
import java.nio.channels.ByteChannel;
import java.util.List;
import java.util.concurrent.Callable;

public interface Command extends Callable<List<String>>, Serializable {
  public final static byte START = 1;
  public final static byte STOP = 2;
  public final static byte STATUS = 3;
  public final static byte FLUSH = 4;
  public final static byte THREADDUMP = 5;
  public final static byte RUNCLASS = 6;
  public final static byte REDEFINECLASS = 7;
  public final static byte PKILL = 8;
  public final static byte LOGIN = 9;
  public final static byte ADDUSER = 10;
  public final static byte PASSWD = 11;

  public final static byte OUTPUTBLOCKTIME = 16;
  public final static byte CHANGETHRESHOLD = 17;
  public final static byte EXCLUDETHREAD = 18;
  public final static byte SCHEDULE = 19;
  public final static byte LOG = 20;

  public static final String CMD_START = "start";
  public static final String CMD_STOP = "stop";
  public static final String CMD_STATUS = "status";
  public static final String CMD_FLUSH = "flush";
  public static final String CMD_CHANGETHRESHOLD = "change-threshold";
  public static final String CMD_OUTPUTBLOCKTIME = "output-block-time";
  public static final String CMD_RUNCLASS = "jrun";
  public static final String CMD_EXCLUDETHREAD = "exclude-thread";
  public static final String CMD_SCHEDULE = "schedule";
  public static final String CMD_THREADDUMP = "jstack";
  public static final String CMD_REDEFINECLASS = "redefine";
  public static final String CMD_PKILL = "pkill";
  public static final String CMD_LOGIN = "login";
  public static final String CMD_ADDUSER = "useradd";
  public static final String CMD_PASSWD = "passwd";
  public static final String CMD_LOG = "log";

  public final static String DISABLE = "disable";
  public final static String ENABLE = "enable";
  public final static String CANCEL = "cancel";

  public byte getOperand();

  public ByteChannel getChannel();

  public String getArgument(int index);

  public void setChannel(ByteChannel channel);

  public void setReceiveBufferSize(int bufSize);

  public void appendArgument(Object arg) throws Exception;

  public void addSubCommand(Command subCmd);

  public String send() throws Exception;

  public void exit();
}
