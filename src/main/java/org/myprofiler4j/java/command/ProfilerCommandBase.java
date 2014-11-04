package org.myprofiler4j.java.command;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.ArrayList;
import java.util.List;

import org.myprofiler4j.java.utils.CommandUtil;
import org.myprofiler4j.java.utils.Utilities;

@SuppressWarnings("serial")
public abstract class ProfilerCommandBase implements Command {
  private final static int DEFAULT_SEND_BUFFER_SIZE = 2048;
  private final static String BINARY_ARG = "BINARY";
  private String commandLine;
  private ByteChannel channel;
  private ByteBuffer commandBuffer;
  protected FileWriter writer;
  protected byte operand;
  protected List<String> commandArgs;
  protected int sendBufferSize = 1 << 8;
  protected int receiveBufferSize = DEFAULT_SEND_BUFFER_SIZE;

  public ProfilerCommandBase(String commandline) throws Exception {
    this.commandLine = commandline;
    prepare();
  }

  public ProfilerCommandBase(String commandline, int sendBufferSize) throws Exception {
    this.sendBufferSize = sendBufferSize;
    this.commandLine = commandline;
    prepare();
  }

  @Override
  public List<String> call() throws Exception {
    List<String> result = new ArrayList<String>();
    result.add(execute());
    return result;
  }

  protected String execute() throws Exception {
    return send();
  }

  public String send() throws Exception {
    if (commandBuffer == null)
      return "Invalid command.";
    if (channel == null) {
      return "No ByteChannel specified.";
    }
    commandBuffer.flip();
    while (commandBuffer.hasRemaining()) {
      channel.write(commandBuffer);
    }

    String filePath = null;
    if (receiveBufferSize > 0) {
      ByteBuffer readBuffer = ByteBuffer.allocate(receiveBufferSize);
      channel.read(readBuffer);
      readBuffer.flip();
      return Utilities.toString(readBuffer);
    } else {
      ByteBuffer rBuffer = ByteBuffer.allocate(4);
      while (rBuffer.hasRemaining()) {
        channel.read(rBuffer);
      }
      rBuffer.flip();
      int len = rBuffer.getInt();
      ByteBuffer readBuf = null;
      while (len > 0) {
        readBuf = ByteBuffer.allocate(len);
        while (readBuf.hasRemaining()) {
          channel.read(readBuf);
        }
        readBuf.flip();
        String fp = writeFile(readBuf);
        if (filePath == null) {
          filePath = fp;
        }
        readBuf.clear();
        rBuffer.clear();
        while (rBuffer.hasRemaining()) {
          channel.read(rBuffer);
        }
        rBuffer.flip();
        len = rBuffer.getInt();
      }

      return "File location: " + filePath;
    }
  }

  protected void validateCommand() throws Exception {
    CommandUtil.checkCommand(operand,
        commandArgs.toArray(new String[commandArgs.size()]));
  }

  protected void initArgument() throws Exception {
    initCommandBuffer();
    if (commandArgs != null && commandArgs.size() > 1) {
      for (int i = 1; i < commandArgs.size(); i++) {
        byte[] param = Utilities.toBytes(commandArgs.get(i));
        if (param == null) {
          continue;
        }
        if (commandBuffer.remaining() < (param.length + 4)) {
          expandCommandBuffer(param.length + 4);
        }
        commandBuffer.putInt(param.length);
        commandBuffer.put(param);
        commandBuffer.mark();
        commandBuffer.putInt(0, commandBuffer.getInt(0) + param.length + 4);
        commandBuffer.reset();
      }
    }
  }

  private void prepare() throws Exception {
    commandArgs = CommandUtil.parseCommandLine(commandLine);
    operand = CommandUtil.getOperand(commandArgs.get(0));

    validateCommand();

    initArgument();
  }

  public void appendArgument(Object arg) throws Exception {
    if (arg != null) {
      byte[] val = null;
      if (arg instanceof String) {
        val = Utilities.toBytes((String) arg);
        commandArgs.add((String) arg);
      } else if (arg instanceof File) {
        File file = (File) arg;
        val = Utilities.readFile(file, 0, -1);
        commandArgs.add(file.getAbsolutePath());
      } else if (arg instanceof byte[]) {
        val = (byte[]) arg;
        commandArgs.add(BINARY_ARG);
      } else {
        throw new IllegalArgumentException("Unsupported argument type - "
            + arg.getClass().getName());
      }
      if (commandBuffer == null) {
        initCommandBuffer();
      }
      if (commandBuffer.remaining() < (val.length + 4)) {
        expandCommandBuffer(val.length + 4);
      }
      commandBuffer.putInt(val.length);
      commandBuffer.put(val);
      commandBuffer.mark();
      commandBuffer.putInt(0, commandBuffer.getInt(0) + val.length + 4);
      commandBuffer.reset();
    }
  }

  @Override
  public byte getOperand() {
    if (this.operand > 0) {
      return this.operand;
    }
    return CommandUtil.getOperand(commandLine);
  }

  protected String writeFile(ByteBuffer bf) throws IOException {
    return null;
  }

  @Override
  public void setChannel(ByteChannel channel) {
    this.channel = channel;
  }

  @Override
  public ByteChannel getChannel() {
    return this.channel;
  }

  @Override
  public void setReceiveBufferSize(int bufSize) {
    this.receiveBufferSize = bufSize;
  }

  @Override
  public String getArgument(int index) {
    if (commandArgs != null && commandArgs.size() > index) {
      return commandArgs.get(index);
    }
    return null;
  }

  @Override
  public void addSubCommand(Command subCmd) {
  }

  protected void initCommandBuffer() {
    if (this.commandBuffer == null) {
      this.commandBuffer = ByteBuffer.allocate(sendBufferSize);
      this.commandBuffer.putInt(1);
      this.commandBuffer.put(operand);
    }
  }

  private void expandCommandBuffer(int expandSize) {
    ByteBuffer cmdBuf = ByteBuffer.allocate(commandBuffer.position()
        + expandSize);
    commandBuffer.flip();
    cmdBuf.put(commandBuffer.array());
    commandBuffer = cmdBuf;
  }

  @Override
  public void exit() {
    if (writer != null) {
      try {
        writer.close();
      } catch (Exception e) {
      } finally {
        writer = null;
      }
    }
  }
}
