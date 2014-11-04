package org.myprofiler4j.java.tools;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.myprofiler4j.java.command.Command;
import org.myprofiler4j.java.command.CommandFuture;
import org.myprofiler4j.java.command.CommandManager;
import org.myprofiler4j.java.command.SimpleCommand;
import org.myprofiler4j.java.config.ProfConfig;
import org.myprofiler4j.java.utils.Utilities;

import com.sun.tools.attach.VirtualMachine;

/**
 * A utility class used for attach JVM at runtime
 * @author nhuang
 *
 */
public class VMAttacher {
  private static boolean isVMDetached = false;
  private static String profileConfFilePath = null;
  private final static String OPEN_CONN_FAILURE = "Open connection failure:";
  
  public static void main(String[] args) {
    ServerSocketChannel serverChannel = null;
    try {
      if(args == null || args.length < 1) {
        System.out.println("Process ID not specified.\nUsage: attachVM [-f <property file path>] [-p <attacher port>] [-s] <pid>");
        System.exit(-1);
      }

      int port = 15598;
      String pid = null;
      boolean isSilentMode = false;
      for(int i = 0; i < args.length; i++) {
        if("-f".equals(args[i])) {
          profileConfFilePath = args[++i];
        } else if("-p".equals(args[i])){
          port = Integer.valueOf(args[++i]);
        } else if("-s".equals(args[i])) {
          isSilentMode = true;
        } else {
          Integer.valueOf(args[i]);
          pid = args[i];
        }
      }
      
      String agentPath = Utilities.getCodeBasePath(VMAttacher.class);
      int index = agentPath.lastIndexOf(Utilities.FILE_SEPARATOR);
      String libPath = agentPath.substring(0, index);
      index = libPath.lastIndexOf(Utilities.FILE_SEPARATOR);
      String myprofile4jHome = agentPath.substring(0, index);
      if(profileConfFilePath == null || !new File(profileConfFilePath).exists()) {
        String propFile = myprofile4jHome + Utilities.FILE_SEPARATOR + ProfConfig.CONFIG_FILE_NAME;
        File f = new File(propFile);
        if(f.exists()) {
          profileConfFilePath = propFile;
        }
      }
      
      if(profileConfFilePath == null) {
        System.out.println("Failed to load configuration file[" + ProfConfig.CONFIG_FILE_NAME + "].");
        System.exit(-1);
      } else {
        System.setProperty("profile.properties", profileConfFilePath);
        System.out.println("Property file path: " + profileConfFilePath);
        ProfConfig.instance.init();
      }

      serverChannel = ServerSocketChannel.open();
      ServerSocket serverSocket = serverChannel.socket();
      serverSocket.bind(new InetSocketAddress(port));

      System.out.println("Try to attach JVM [PID:" + pid + "] on port " + port + ".");
      final VirtualMachine vm = VirtualMachine.attach(pid);
      String arguments = "profile.properties=" + profileConfFilePath;
      if(isSilentMode) {
        arguments = arguments + ";profile.mode=silent";
      }
      vm.loadAgent(agentPath, arguments);
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          try {
            detachVM(vm);
          } catch (Exception e) {
            //ignored
          }
        }
      });
      System.out.println("Successfully attached to JVM [PID:" + pid + "].");
      
      SocketChannel channel = null;
      while(true) {
        try {
          channel = serverChannel.accept();
          channel.socket().setSoTimeout(8000);
          channel.socket().setSendBufferSize(1024);
          
          ByteBuffer bf = ByteBuffer.allocate(4);
          channel.read(bf);
          if(bf.hasRemaining()) {
            System.out.println("Read password length failed.");
            continue;
          }
          bf.flip();
          int len = bf.getInt();
          bf = ByteBuffer.allocate(len);
          channel.read(bf);
          if(bf.hasRemaining()) {
            System.out.println("Read password failed.");
            continue;
          }
          bf.flip();
          String result = sendDetachVMCommand(bf.array());
          boolean isAttacherExit = (result.indexOf(OPEN_CONN_FAILURE) != -1);
          if(isAttacherExit) {
            result = "VM attacher process has terminated.";
          }
          bf = ByteBuffer.wrap(Utilities.toBytes(result));
          int maxTry = 10;
          while(bf.hasRemaining() && maxTry > 0) {
            channel.write(bf);
            maxTry --;
            Thread.sleep(20);
          }
          if(isAttacherExit || result.toLowerCase().indexOf("success") != -1) {
            //Sleep a few seconds for detacher to get response
            Thread.sleep(2000);
          } else {
            continue;
          }
        } catch (IOException e) {
          e.printStackTrace();
          continue;
        } catch (Throwable ex) {
          ex.printStackTrace();
          continue;
        } finally {
          if(channel != null) {
            try {
              channel.close();
            } catch (Exception e) {}
          }
        }
        detachVM(vm);
        break;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(-1);
    } finally {
      if(serverChannel != null) {
        try {
          serverChannel.close();
        } catch (Exception ex) {}
      }
    }
    System.exit(0);
  }

  private synchronized static void detachVM(VirtualMachine vm) {
    try {
      if(vm != null && !isVMDetached) {
        vm.detach();
      }
      isVMDetached = true;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static String sendDetachVMCommand(byte[] password){
      SocketChannel channel = null;
      try {
        ProfConfig.instance.init();
        InetSocketAddress addr = new InetSocketAddress(ProfConfig.instance.port);
        channel = SocketChannel.open();
        channel.connect(addr);
        int i = 0;
        while (!channel.finishConnect() && i < 5) {
          try {
            Thread.sleep(50);
          } catch (Exception e) {
          }
          i++;
        }
      } catch (IOException e) {
        return OPEN_CONN_FAILURE + e.getMessage();
      }
      
      try {
        if(channel != null && channel.isConnected()) {
          //do login
          Command loginCmd = new SimpleCommand(Command.CMD_LOGIN);
          loginCmd.appendArgument("admin"); //user name
          loginCmd.appendArgument(password);//password
          CommandFuture loginFuture = CommandManager.sendCommand(loginCmd, channel);
          List<String> response = loginFuture.get(10, TimeUnit.SECONDS);
          if(response.size() == 0 || response.get(0).indexOf("success") == -1) {
            return "Login failed.";
          }
          
          Command cmd = new SimpleCommand(Command.CMD_PKILL);
          CommandFuture future = CommandManager.sendCommand(cmd, channel);
          List<String> rs = future.get(10, TimeUnit.SECONDS);
          if(rs.size() > 0) {
            return rs.get(0);
          }
        } else {
          return "Failed to establish connection to profiler.";
        }
      } catch(Throwable ex) {
        ProfConfig.instance.reset();
        return "Failed: " + ex.getMessage();
      } finally {
        try {
          if(channel != null) {
            channel.close();
          }
        } catch (Exception e) {}
      }
      return "Failed: Unknown reason";
    }
}
