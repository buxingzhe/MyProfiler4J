/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package org.myprofiler4j.java.agent;

import java.io.IOException;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.myprofiler4j.java.command.Command;
import org.myprofiler4j.java.config.CronExpression;
import org.myprofiler4j.java.config.ProfConfig;
import org.myprofiler4j.java.config.ProfFilter;
import org.myprofiler4j.java.hotloader.JavaClassExecutor;
import org.myprofiler4j.java.nio.PooledNioSocketHandler;
import org.myprofiler4j.java.utils.SecurityUtil;
import org.myprofiler4j.java.utils.Utilities;

/**
 * Server socket for remote client
 * 
 * @author nhuang
 * @since 2013-11-28
 */
public class ProfilerSocketListenerThread extends PooledNioSocketHandler {
  private final static Logger logger = Logger.getLogger(ProfilerSocketListenerThread.class.getName()); 
  private final static String SUCCESS_RESPONSE = "Success";
  private final static String READ_PARAMETER_FAILURE = "Failed to read parameter.";
  private final static byte[] NEWLINE_BYTES = Utilities.toBytes(Utilities.LINE_SEPARATOR);
  private final static int MAX_BUF_LEN = 1 << 18;
  private Map<String, UserInfo> userTable = new Hashtable<String, UserInfo>();

  public ProfilerSocketListenerThread(int port) {
    super(port);
    userTable.put("admin", new UserInfo("admin", null));
  }

  @Override
  protected void reply(SelectionKey key) throws IOException{
    SocketChannel channel = (SocketChannel) key.channel();
    try {
      ByteBuffer cmdBuffer = readCommandData(channel);
      if(cmdBuffer == null) {
        channel.close();
        return;
      }
      byte operand = cmdBuffer.get();
      if(operand <= 0 || operand > 25) {
        sendResponse(channel, "Failure: Invalid command!");
        return;
      }
      if((key.attachment() == null && operand != Command.LOGIN) ||
          (key.attachment() instanceof Integer && ((Integer)key.attachment()) != 0 && operand != Command.LOGIN)) {
        sendResponse(channel, "Please login first.");
        return;
      }
      
      if(!checkSecurity(operand, key)) {
        sendResponse(channel, "Insufficient privilege!");
        return;
      }

      String message = SUCCESS_RESPONSE;
      switch(operand) {
      case Command.LOGIN:
        doLogin(channel, key, cmdBuffer);
        break;
      case Command.ADDUSER:
        message = doAddUser(key, cmdBuffer);
        sendResponse(channel, message);
        break;
      case Command.PASSWD:
        message = doChangePassword(key, cmdBuffer);
        sendResponse(channel, message);
        break;
      case Command.START:
        Profiler.isEnabled = true;
        sendResponse(channel, message);
        break;
      case Command.STOP:
        Profiler.isEnabled = false;
        Manager.instance().flush();
        sendResponse(channel, message);
        break;
      case Command.STATUS:
        sendResponse(channel, getStatusMessage());
        break;
      case Command.FLUSH:
        Manager.instance().flush();
        sendResponse(channel, message);
        break;
      case Command.CHANGETHRESHOLD:
        message = changeMethodProfileThreshold(cmdBuffer);
        sendResponse(channel, message);
        break;
      case Command.OUTPUTBLOCKTIME:
        message = outputBlockTime(cmdBuffer);
        sendResponse(channel, message);
        break;
      case Command.RUNCLASS:
        String result = runClass(cmdBuffer);
        sendResponse(channel, result);
        break;
      case Command.EXCLUDETHREAD:
        message = excludeThread(cmdBuffer);
        sendResponse(channel, message);
        break;
      case Command.SCHEDULE:
        message = schedule(cmdBuffer);
        sendResponse(channel, message);
        break;
      case Command.THREADDUMP:
        runThreadDump(channel);
        break;
      case Command.REDEFINECLASS:
        message = redefineClass(cmdBuffer);
        sendResponse(channel, message);
        break;
      case Command.LOG:
        message = doChangeLogLevel(cmdBuffer);
        sendResponse(channel, message);
        break;
      case Command.PKILL:
        sendResponse(channel, "Profiler destroyed successfully.");
        disableAgent();
        break;
      default:
        sendResponse(channel, "Unknown operation");
        break;
      }
    }catch(IOException ioEx) {
      logger.log(Level.SEVERE, "IOError:", ioEx);
      throw ioEx;
    }catch(Throwable ex) {
      logger.log(Level.SEVERE, "Error:", ex);
      try {
        sendResponse(channel, "Failed: " + ex.getClass().getName() + " - " + ex.getMessage());
      } catch (Exception e) {}
    }
  }

  private String doChangeLogLevel(ByteBuffer cmdBuffer) {
    byte[] level = readParameter(cmdBuffer);
    String levelStr = Utilities.toString(level);
    String result = LogConfiguration.setLogLevel(levelStr);
    if(result != null) {
      return result;
    } else {
      return SUCCESS_RESPONSE;
    }
  }

  private boolean checkSecurity(byte operand, SelectionKey key) {
    if(operand == Command.RUNCLASS || operand == Command.REDEFINECLASS
        || operand == Command.LOG || operand == Command.PKILL) {
      UserInfo userinfo = (UserInfo) key.attachment();
      if(userinfo == null || !userinfo.isAdmin()) {
        return false;
      }
    }
    return true;
  }

  private ByteBuffer readCommandData(SocketChannel channel) throws Exception{
    ByteBuffer buf = ByteBuffer.allocate(4);
    if(readFromChannel(channel, buf, 10)) {
      buf.flip();
      int len = buf.getInt();
      if(len <= 0 || len > MAX_BUF_LEN) {
        logger.info("The data is larger than 256k. Data length: " + len);
        throw new IllegalStateException("Failed: Data is larger than 256k.");
      }
      ByteBuffer cmdBuf = ByteBuffer.allocate(len);
      if(readFromChannel(channel, cmdBuf, 200)) {
        cmdBuf.flip();
        return cmdBuf;
      }
      throw new IOException("Failed to read full command data.");
    }
    return null;
  }

  private void doLogin(SocketChannel channel, SelectionKey key, ByteBuffer cmdBuf) throws Exception{
    byte[] userNameBuf = readParameter(cmdBuf);
    byte[] pass = readParameter(cmdBuf);
    String userName = Utilities.toString(userNameBuf);
    if(pass != null) {
      String plainPw = new String(SecurityUtil.decrypt(pass, userName), "UTF-8");
      UserInfo userinfo = userTable.get(userName);
      if(userinfo == null) {
        sendResponse(channel, "User '" + userName + "' not exist.");
        return;
      }
      if(plainPw.equals(userinfo.getPassword())) {
        key.attach(userinfo);
        sendResponse(channel, "Login success.");
        return;
      } else {
        Object attachment = key.attachment();
        if(attachment instanceof UserInfo) {
          UserInfo userInfo = (UserInfo) attachment;
          userInfo.increaseTryLoginTimes();
          if(userInfo.getTryLoginTimes() >= 3) {
            String message = "Incorrect password for three times, connection to be closed.";
            sendResponse(channel, message);
            Thread.sleep(1000); //wait a second to let client read reply message from this channel before close it
            channel.close();
            return;
          }
          key.attach(userInfo);
        } else {
          UserInfo userInfo = new UserInfo(userName, null);
          userInfo.increaseTryLoginTimes();
          key.attach(userInfo);
        }
        String message = "Login failed: incorrect password!";
        sendResponse(channel, message);
        return;
      }
    } else {
      sendResponse(channel, "Read login password failure.");
    }
  }

  private String doAddUser(SelectionKey key, ByteBuffer cmdBuf) throws Exception {
    byte[] userNameBuf = readParameter(cmdBuf);
    byte[] pwBuf = readParameter(cmdBuf);

    if(userNameBuf == null) {
      return "Invalid user name.";
    }
    if(pwBuf == null) {
      return "Read user password failure.";
    }
    String userName = Utilities.toString(userNameBuf);
    if(userTable.containsKey(userName)) {
      return "User already exists.";
    }

    UserInfo userInfo = (UserInfo)key.attachment();
    if(!userInfo.isAdmin()) {
      return "Insufficient privilege!";
    }

    String password = new String(SecurityUtil.decrypt(pwBuf, userName), "UTF-8");
    userTable.put(userName, new UserInfo(userName, password));
    return SUCCESS_RESPONSE;
  }

  private String doChangePassword(SelectionKey key, ByteBuffer cmdBuf) throws Exception {
    byte[] userNameBuf = readParameter(cmdBuf);
    byte[] pwBuf = readParameter(cmdBuf);
    byte[] firstPw = readParameter(cmdBuf);
    byte[] secondPw = readParameter(cmdBuf);

    if(userNameBuf == null) {
      return "Invalid user name!";
    }
    String username = Utilities.toString(userNameBuf);
    UserInfo user = userTable.get(username);
    if(user == null) {
      return "User '" + username + "' not exist.";
    }
    
    UserInfo userInfo = (UserInfo)key.attachment();
    if(!userInfo.isAdmin() && !username.equalsIgnoreCase(userInfo.getUserName())) {
      return "Insufficient privilege!";
    }

    if(pwBuf == null) {
      return "Original password read failure!";
    }
    String password = new String(SecurityUtil.decrypt(pwBuf, username), "UTF-8");
    String originPasswd = user.getPassword();
    if(!password.equals(originPasswd)) {
      return "Original password incorrect!";
    }

    if(firstPw == null || secondPw == null) {
      return "New password read failure!";
    }
    String newPw1 = new String(SecurityUtil.decrypt(firstPw, username), "UTF-8");
    String newPw2 = new String(SecurityUtil.decrypt(secondPw, username), "UTF-8");
    if(!newPw1.equals(newPw2)) {
      return "New password mismatch!";
    }

    userInfo.setPassword(newPw1);
    user.setPassword(newPw1);
    return SUCCESS_RESPONSE;
  }

  private String schedule(ByteBuffer cmdBuf) throws Exception {
    byte[] bf = readParameter(cmdBuf);
    if(bf == null) {
      return READ_PARAMETER_FAILURE;
    }
    String param1 = Utilities.toString(bf);
    if(Command.CANCEL.equalsIgnoreCase(param1)) {
      ProfConfig.instance.setCronExpression(null, 0);
    } else {
      int duration = Integer.valueOf(param1);
      bf = readParameter(cmdBuf);
      if(bf == null) {
        return READ_PARAMETER_FAILURE;
      }
      String cronExpression = Utilities.toString(bf);
      CronExpression expression = new CronExpression(cronExpression);
      ProfConfig.instance.setCronExpression(expression, duration);
    }
    return SUCCESS_RESPONSE;
  }

  private String excludeThread(ByteBuffer cmdBuf) throws Exception {
    String message = null;
    byte[] bf = readParameter(cmdBuf);
    if(bf == null) return READ_PARAMETER_FAILURE;
    String threadPattern = Utilities.toString(bf);
    if(threadPattern.charAt(0) == '+') {
      message = ProfFilter.instance().addExcludedThreadPattern(threadPattern.substring(1));
    } else if(threadPattern.charAt(0) == '-') {
      message = ProfFilter.instance().removeExcludedThreadPattern(threadPattern.substring(1));
    } else {
      message = "Command format error, no action taken.";
    }
    if(message == null) {
      return SUCCESS_RESPONSE;
    }
    return message;
  }

  private String runClass(ByteBuffer cmdBuf) throws Exception {
    byte[] bf = readParameter(cmdBuf);
    if(bf == null) {
      return READ_PARAMETER_FAILURE;
    }
    return JavaClassExecutor.execute(bf);
  }

  private String outputBlockTime(ByteBuffer cmdBuffer)
      throws IOException {
    String message = SUCCESS_RESPONSE;
    byte[] bf = readParameter(cmdBuffer);
    if(bf == null) return READ_PARAMETER_FAILURE;
    String operation = Utilities.toString(bf);
    if(Command.ENABLE.equalsIgnoreCase(operation)) {
      Profiler.outputBlockTime = true;
    } else if(Command.DISABLE.equalsIgnoreCase(operation)) {
      Profiler.outputBlockTime = false;
    } else {
      message = "Unknown operation, no action taken.";
    }
    return message;
  }

  private String changeMethodProfileThreshold(ByteBuffer cmdBuffer) throws Exception {
    byte[] bf = readParameter(cmdBuffer);
    if(bf == null) {
      return READ_PARAMETER_FAILURE;
    }
    String value = Utilities.toString(bf);
    Profiler.methodThreshold = Integer.valueOf(value);
    return SUCCESS_RESPONSE;
  }

  private byte[] readParameter(ByteBuffer cmdBuf) throws BufferUnderflowException {
    int len = cmdBuf.getInt();
    byte[] paramValue = new byte[len];
    cmdBuf.get(paramValue);
    return paramValue;
  }

  private boolean readFromChannel(SocketChannel channel, ByteBuffer buf, int retryTimes) throws IOException{
    while(buf.hasRemaining() && retryTimes > 0) {
      int count = channel.read(buf);
      retryTimes --;
      if(count == -1) {
        channel.close();
        return false;
      } else if(buf.hasRemaining()){
        if(retryTimes == 0) {
          logger.log(Level.WARNING, "Failed to get enough data from channel:", new Exception());
          return false;
        }
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
    return true;
  }

  private List<String> getStatusMessage() {
    List<String> messages = new ArrayList<String>();
    messages.add("Java process: " + Utilities.PROCESS_NAME);
    messages.add("Listening port: " + ProfConfig.instance.port);
    if (Profiler.isEnabled) {    
      messages.add("Profiler is running.");
    } else {
      messages.add("Profiler has stopped.");
    }
    messages.add("methodProfileThresholdMs = " + Profiler.methodThreshold);
    messages.add("outputMethodBlockTime = " + 
        (Profiler.outputBlockTime ? "yes" : "no"));
    CronExpression expression = ProfConfig.instance.getCronExpression();
    if(expression == null) {
      messages.add("schedulerCronExpression = ");
    } else {
      messages.add("schedulerCronExpression = " + expression.getCronExpression());
    }
    messages.add("samplingDuration = " + ProfConfig.instance.duration);
    messages.add("Log level: " + LogConfiguration.getLogLevel().getName());
    messages.add(ProfilerRuntime.getOSInfo());
    messages.add(ProfilerRuntime.getVmInfo());
    return messages;
  }

  private void sendResponse(SocketChannel channel, String message) throws IOException{
    byte[] msgBytes = Utilities.toBytes(message);
    ByteBuffer buffer = ByteBuffer.wrap(msgBytes);
    writeToChannel(channel, buffer, 60);
  }

  private void sendResponse(SocketChannel channel, List<String> messages) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(2 * 1024);
    fillInBuffer(buffer, messages);
    buffer.flip();
    writeToChannel(channel, buffer, 60);
  }

  private void writeToChannel(SocketChannel channel, ByteBuffer buffer, int retryTimes) throws IOException{
    while(buffer.hasRemaining() && retryTimes > 0) {
      channel.write(buffer);
      retryTimes --;
      if(buffer.hasRemaining()) {
        if(retryTimes == 0) {                  
          throw new IOException("Writer data failure.");
        } else {
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
              return;
          }
        }
      }
    }
  }

  private void runThreadDump(SocketChannel channel) throws Exception {
    StringBuilder sb = new StringBuilder();
    String fn = Utilities.generateStackFileName();
    sb.append(fn + Utilities.LINE_SEPARATOR);
    String date = Utilities.getDateString(Calendar.getInstance().getTime()) + Utilities.LINE_SEPARATOR;
    String dumpHeader = "Full thread dump " + ProfilerRuntime.getVmInfo() + ":" + Utilities.LINE_SEPARATOR + Utilities.LINE_SEPARATOR;
    String deadLock = ProfilerRuntime.deadlocks(true);
    if(deadLock == null) {
      deadLock = "No deadlock found." + Utilities.LINE_SEPARATOR;
    }
    sb.append(date);
    sb.append(dumpHeader);
    sb.append(deadLock + Utilities.LINE_SEPARATOR);

    ThreadMXBean threadMBean = ProfilerRuntime.getThreadMXBean();
    boolean lockedMonitors = threadMBean.isObjectMonitorUsageSupported();
    boolean lockedSynchronizers = threadMBean.isSynchronizerUsageSupported();
    ThreadInfo[] threadInfo = threadMBean.dumpAllThreads(lockedMonitors, lockedSynchronizers);
    int i = 1;
    for(ThreadInfo ti: threadInfo) {
      ProfilerRuntime.getThreadStackTrace(true, ti, sb);
      i ++;
      if(i % 5 == 0) {
        if(!send(channel, sb, false)) {
          logger.log(Level.SEVERE, "Failed to write full message to client.");
          return;
        }
        i = 0;
      }
    }
    send(channel, sb, true);
  }

  private boolean send(SocketChannel channel, StringBuilder sb, boolean isEnd) throws Exception {
    ByteBuffer buffer = null;
    if(sb.length() == 0) {
      buffer = ByteBuffer.allocate(4);
      buffer.putInt(0);
    } else {
      byte[] msgBytes = Utilities.toBytes(sb.toString());
      int len = msgBytes.length + 4;
      if(isEnd) {
        len += 4;
      }
      buffer = ByteBuffer.allocate(len);
      buffer.putInt(msgBytes.length);
      buffer.put(msgBytes);
      if(isEnd) {
        buffer.putInt(0);
      }
    }
    buffer.flip();
    int i = 100;
    while(buffer.hasRemaining() && i > 0) {
      channel.write(buffer);
      i --;
      if(buffer.hasRemaining()) {
        if(i == 0) {                  
          return false;
        } else {
          try {
            Thread.sleep(100);
          } catch (Exception e) {
          }
        }
      }
    }
    sb.setLength(0);
    return true;
  }

  private String redefineClass(ByteBuffer cmdBuffer) throws Exception {
    byte[] nameBytes = readParameter(cmdBuffer);
    if(nameBytes == null) return READ_PARAMETER_FAILURE;
    byte[] clzBytes = readParameter(cmdBuffer);
    if(clzBytes == null) return READ_PARAMETER_FAILURE;
    return Main.redefineClass(Utilities.toString(nameBytes), clzBytes);
  }

  private void disableAgent() throws Exception {
    Profiler.shutdown();
    Thread.sleep(5000);
    Manager.instance().stopThreads();
  }

  private void fillInBuffer(ByteBuffer buffer, List<String> strValues) {
    if(strValues != null && strValues.size() > 0) {
      Iterator<String> iter = strValues.iterator();
      while(iter.hasNext()) {
        String msg = iter.next();
        buffer.put(Utilities.toBytes(msg));
        buffer.put(NEWLINE_BYTES);
      }
    }
  }

  public void exit() {
    userTable.clear();
    close();
  }
}
