/**
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 */
package org.myprofiler4j.java.client;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.myprofiler4j.java.command.Command;
import org.myprofiler4j.java.command.CommandFuture;
import org.myprofiler4j.java.command.CommandManager;
import org.myprofiler4j.java.command.RedefineClassCommand;
import org.myprofiler4j.java.command.RunClassCommand;
import org.myprofiler4j.java.command.SimpleCommand;
import org.myprofiler4j.java.command.ThreadDumpCommand;
import org.myprofiler4j.java.utils.CommandUtil;
import org.myprofiler4j.java.utils.SecurityUtil;
import org.myprofiler4j.java.utils.Utilities;

/**
 * Profiler client for remote management.
 * 
 * @author nhuang
 * @since 2013-11-28
 */
public class MyProfilerClient {
  private static String HOST = null;
  private static int PORT_NUMBER = 0;
  private static SocketChannel channel = null;
  private final static String OUTPUT_FORMAT = "%-60s%s\n";
  private final static int DEFAULT_BUFFER_SIZE = 1 << 16;
  private static boolean isLogined = false;
  private static String currentUser = null;
  private static final Console console = System.console();
  private static final BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));
  private static boolean isDebugable = false;

  private static void initSocketChannel() {
    if (channel == null) {
      try {
        InetSocketAddress addr = new InetSocketAddress(HOST, PORT_NUMBER);
        channel = SocketChannel.open();
        channel.connect(addr);
        while (!channel.finishConnect()) {
          Thread.sleep(100);
        }
        channel.configureBlocking(true);
        if (channel.socket().getReceiveBufferSize() < DEFAULT_BUFFER_SIZE) {
          channel.socket().setReceiveBufferSize(DEFAULT_BUFFER_SIZE);
        }
        if (channel.socket().getSendBufferSize() < DEFAULT_BUFFER_SIZE) {
          channel.socket().setSendBufferSize(DEFAULT_BUFFER_SIZE);
        }
        channel.socket().setSoTimeout(60000);
      } catch (Exception e) {
        System.out.println("Failed connect to [" + HOST + ":" + PORT_NUMBER
            + "], reason: " + e.getMessage());
        System.out.println("Exit.");
        HOST = null;
        PORT_NUMBER = 0;
        closeChannel();
        System.exit(-1);
      }
      isLogined = false;
    }
  }

  private static CommandFuture sendCommand(Command cmd) {
    if (cmd == null) {
      return null;
    }
    return CommandManager.sendCommand(cmd, channel);
  }

  private static void quit() {
    try {
      System.out.println("Goodbye!");
      closeChannel();
      inputReader.close();
      System.exit(0);
    } catch (Exception e) {
      // ignored
    }
  }

  private static void closeChannel() {
    if (channel != null) {
      try {
        channel.close();
      } catch (Exception e) {
      } finally {
        channel = null;
      }
    }
  }

  private static void resetChannel() {
    try {
      closeChannel();
      initSocketChannel();
    } catch (Exception e) {
      HOST = null;
      PORT_NUMBER = 0;
    }
  }

  private static String getInput() {
    try {
      String value = inputReader.readLine();
      if(value.isEmpty()) {
        return null;
      } else {
        return value;
      }
    } catch (Exception e) {
    }
    return null;
  }
  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length >= 2) {
      try {
        PORT_NUMBER = Integer.valueOf(args[1]);
        HOST = args[0];
      } catch (Exception e) {
      }
    }
    Utilities.getProfilerHomePath();
    isDebugable = "true".equalsIgnoreCase(System.getProperty("profiler.debug"));
    String line = null;
    do {
      while (HOST == null) {
        System.out.println("Please enter server host name or IP address:");
        if((line = getInput()) == null) {
          continue;
        }
        if ("quit".equalsIgnoreCase(line.trim())) {
          quit();
        }
        HOST = line.trim();
      }
      while (PORT_NUMBER == 0) {
        System.out.println("Please enter profiling port number:");
        if((line = getInput()) == null) {
          continue;
        }
        if (line != null && !"".equals(line)) {
          if ("quit".equalsIgnoreCase(line.trim())) {
            quit();
          }
          try {
            PORT_NUMBER = Integer.valueOf(line.trim());
          } catch (Exception e) {
            continue;
          }
        }
      }

      if (channel == null) {
        initSocketChannel();
        System.out.println("Welcome to MyProfiler4J command console!");
      }

      int maxRetry = 3;
      while (!isLogined && maxRetry > 0) {
        if(currentUser == null) {
          currentUser = console.readLine("Please enter user name:");
          maxRetry = 3;
        }
        char[] pw = console.readPassword("Please enter login password:");
        String password = String.valueOf(pw);
        isLogined = doLogin(currentUser, password);
        maxRetry--;
      }

      if (!isLogined) {
        quit();
      }
      
      System.out.print("profiler> ");
      if((line = getInput()) == null) {
        continue;
      }
      line = line.trim();
      if ("quit".equalsIgnoreCase(line)) {
        quit();
      } else if ("help".equalsIgnoreCase(line)) {
        usage();
      } else {
        executeCommand(line);
      }
    } while (true);
  }

  private static boolean doLogin(String userName, String password) {
    try {
      byte[] passBytes = SecurityUtil.encrypt(password, userName);
      SimpleCommand loginCmd = new SimpleCommand(Command.CMD_LOGIN);
      loginCmd.appendArgument(userName);
      loginCmd.appendArgument(passBytes);

      CommandFuture cf = sendCommand(loginCmd);
      List<String> response = cf.get(20, TimeUnit.SECONDS);
      if (response != null && response.size() > 0) {
        String replyMsg = response.get(0);
        System.out.println(replyMsg);
        if (replyMsg.indexOf("close") != -1) {
          quit();
        } else if (replyMsg.indexOf("success") == -1) {
          return false;
        } else if(replyMsg.indexOf("not exist") != -1) {
          currentUser = null;
          return false;
        }
      } else {
        return false;
      }
    } catch (Exception e) {
      if(isDebugable) {
        e.printStackTrace();
      }
      System.out.println("Login failed:" + e.getClass().getName() + " - " + e.getMessage());
      quit();
    }
    return true;
  }

  private static void executeCommand(String line) {
    CommandFuture cf = null;
    try {
      Command cmd = null;
      byte operand = CommandUtil.getOperand(line);
      if (operand == Command.ADDUSER) {
        char[] pw1 = console.readPassword("Please enter your password:");
        char[] pw2 = console.readPassword("Please reenter your password:");
        String password1 = new String(pw1);
        String password2 = new String(pw2);
        boolean isPasswdMatch = password1.equals(password2);
        if (!isPasswdMatch) {
          System.out.println("Password mismatch.");
          return;
        }
        cmd = new SimpleCommand(line);
        String user = cmd.getArgument(1);
        cmd.appendArgument(SecurityUtil.encrypt(password1, user));
      } else if (operand == Command.PASSWD) {
        char[] pw = console.readPassword("Please enter original password:");
        char[] pw1 = console.readPassword("Please enter new password:");
        char[] pw2 = console.readPassword("Please reenter new password:");
        String password1 = new String(pw1);
        String password2 = new String(pw2);
        boolean isPasswdMatch = password1.equals(password2);
        if (!isPasswdMatch) {
          System.out.println("New password mismatch.");
          return;
        }
        String originalPw = new String(pw);
        cmd = new SimpleCommand(line);
        String user = cmd.getArgument(1);
        if (user == null) {
          user = currentUser;
          cmd.appendArgument(user);
        }
        cmd.appendArgument(SecurityUtil.encrypt(originalPw, user));
        cmd.appendArgument(SecurityUtil.encrypt(password1, user));
        cmd.appendArgument(SecurityUtil.encrypt(password2, user));
      }

      if (cmd == null) {
        switch (operand) {
        case Command.THREADDUMP:
          cmd = new ThreadDumpCommand(line, 32);
          break;
        case Command.REDEFINECLASS:
          cmd = new RedefineClassCommand(line);
          break;
        case Command.RUNCLASS:
          cmd = new RunClassCommand(line, 1 << 12);
          break;
        default:
          cmd = new SimpleCommand(line);
          break;
        }
      }
      cf = sendCommand(cmd);
      List<String> result = cf.get(60, TimeUnit.SECONDS);
      for (Iterator<String> iterator = result.iterator(); iterator.hasNext();) {
        String r = (String) iterator.next();
        System.out.println(r);
      }
      if(operand == Command.PKILL && result.size() > 0 
          && result.get(0).indexOf("success") != -1) {
        quit();
      }
    } catch (UnsupportedOperationException uoex) {
      System.out.println(uoex.getMessage());
    } catch (TimeoutException toex) {
      System.out.println("Response timeout.");
      if (cf.cancel(true)) {
        resetChannel();
      }
    } catch (ExecutionException executionEx) {
      if(isDebugable) {          
        executionEx.printStackTrace();
      }
      if (executionEx.getCause() instanceof IOException) {
        resetChannel();
      }
    } catch (Exception e) {
      if(isDebugable) {        
        e.printStackTrace();
      }
      System.out.println("Failure: " + e.getMessage());
    }
  }

  private static void usage() {
    System.out.printf("%-40s\n", "Command list:");
    System.out.printf(OUTPUT_FORMAT, "start", "- Enable profiler.");
    System.out.printf(OUTPUT_FORMAT, "stop", "- Disable profiler.");
    System.out.printf(OUTPUT_FORMAT, "status", "- Show profiler status.");
    System.out.printf(OUTPUT_FORMAT, "useradd <user name>", "- Add user.");
    System.out.printf(OUTPUT_FORMAT, "passwd <user name>", "- Set user password.");
    System.out.printf(OUTPUT_FORMAT, "jrun <class file path>", "- Run user class remotely.");
    System.out.printf(OUTPUT_FORMAT, "redefine [class file path]", "- Redefine classes.");
    System.out.printf(OUTPUT_FORMAT, "jstack [<none>|<interval(sec.)> <times>]", "- Do thread dump.");
    System.out.printf(OUTPUT_FORMAT, "change-threshold <milliseconds>", "- Change the method profile threshold value.");
    System.out.printf(OUTPUT_FORMAT, "output-block-time <enable|disable>", "- Switch profile method blocking time.");
    System.out.printf(OUTPUT_FORMAT, "schedule [<sampling duration> <cron-expression>|cancel]", "- Schedule profiling.");
    System.out.printf(OUTPUT_FORMAT, "exclude-thread [+|-]<thread name pattern>", "- Add or remove thread name pattern in excluded set.");
    System.out.printf(OUTPUT_FORMAT, "flush", "- Flush method profiling data to log file.");
    System.out.printf(OUTPUT_FORMAT, "log <log level>", "- Set profiler internal log level.");
    System.out.printf(OUTPUT_FORMAT, "quit", "- Exit the client.");
  }
}
