package org.myprofiler4j.java.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Profiler inner socket handler which is used to listen to
 * the server socket (to accept new connections) and response to all the active socket
 * channels.
 * 
 * @author nhuang
 * 
 */
public class NioSocketHandler implements Runnable{
  private final static Logger logger = Logger.getLogger(NioSocketHandler.class.getName());

  private int port = 15599;

  private boolean keepRunning = true;

  private ServerSocketChannel serverChannel = null;

  private Selector selector = null;

  private final static int DEFAULT_BUFFER_SIZE = 1 << 16;

  public static void main(String[] argv) throws Exception {
    new NioSocketHandler(15599).run();
  }

  public NioSocketHandler(int port) {
    this.port = port;
  }

  public void run() {
    try {
      //System.out.println("Listening on port " + port);
      // Allocate an unbound server socket channel
      serverChannel = ServerSocketChannel.open();
      // Get the associated ServerSocket to bind it with
      ServerSocket serverSocket = serverChannel.socket();
      // Create a new Selector for use below
      synchronized (Selector.class) {
        selector = Selector.open();
      }
      // Set the port the server channel will listen to
      boolean isBind = false;
      int maxRetry = 3;
      while(!isBind && maxRetry > 0) {
        try {
          serverSocket.bind(new InetSocketAddress(port));
          isBind = true;
        } catch (Exception e) {
          port++;
        }
        maxRetry --;
      }
      logger.info("Profiler listening on port: " + port);
      
      // Set nonblocking mode for the listening socket
      serverChannel.configureBlocking(false);
      // Register the ServerSocketChannel with the Selector
      serverChannel.register(selector, SelectionKey.OP_ACCEPT);
      while (keepRunning) {
        try {
          // This may block for a long time. Upon returning, the
          // selected set contains keys of the ready channels.
          int n = selector.select();
          if (n == 0) {
            continue; // nothing to do
          }
          // Get an iterator over the set of selected keys
          Iterator<SelectionKey> it = selector.selectedKeys().iterator();
          // Look at each key in the selected set
          while (it.hasNext()) {
            try {
              SelectionKey key = it.next();
              if(!key.isValid()) {
                continue;
              }
              // Is a new connection coming in?
              if (key.isAcceptable()) {
                ServerSocketChannel server = (ServerSocketChannel) key.channel();
                SocketChannel channel = server.accept();
                registerChannel(selector, channel, SelectionKey.OP_READ);
              } else if (key.isReadable()) {// Is there data to read on this channel?
                readDataFromSocket(key);
              }
            }catch (Exception e) {
              continue;
            } finally {
              // Remove key from selected set; it's been handled
              it.remove();
            }
          }
        }catch (Exception ex) {
          continue;
        }
      }
    }catch (Exception e) {
      e.printStackTrace();
    }
  }

  // ----------------------------------------------------------
  /**
   * Register the given channel with the given selector for the given
   * operations of interest
   */
  protected void registerChannel(Selector selector,
      SocketChannel channel, int ops) throws Exception {
    if (channel == null) {
      return; // could happen
    }
    // Set the new channel nonblocking
    channel.configureBlocking(false);
    channel.socket().setKeepAlive(true);
    if(channel.socket().getReceiveBufferSize() < DEFAULT_BUFFER_SIZE) {
      channel.socket().setReceiveBufferSize(DEFAULT_BUFFER_SIZE);
    }
    if(channel.socket().getSendBufferSize() < DEFAULT_BUFFER_SIZE) {
      channel.socket().setSendBufferSize(DEFAULT_BUFFER_SIZE);
    }
    channel.socket().setSoTimeout(60000);
    // Register it with the selector
    channel.register(selector, ops);

    if(logger.isLoggable(Level.INFO)) {
      logger.info("User from " + channel.socket().getRemoteSocketAddress() + " has connected.");
    }
  }

  /**
   * Sample data handler method for a channel with data ready to read.
   * 
   * @param key
   *            A SelectionKey object associated with a channel determined by
   *            the selector to be ready for reading. If the channel returns
   *            142 an EOF condition, it is closed here, which automatically
   *            invalidates the associated key. The selector will then
   *            de-register the channel on the next select call.
   */
  protected void readDataFromSocket(SelectionKey key) throws Exception {

  }

  public void setPort(int port) {
    this.port = port;
  }

  protected synchronized void close() {
    this.keepRunning = false;

    if(serverChannel != null) {
      try {
        serverChannel.close();
      } catch (IOException e) {
      } finally {                
        serverChannel = null;
      }
    }

    if(selector != null) {
      Set<SelectionKey> keySet = selector.selectedKeys();
      Iterator<SelectionKey> keyIter = keySet.iterator();
      while(keyIter.hasNext()) {
        try {            
          keyIter.next().channel().close();
        } catch (Exception e) {
        }
      }

      try {
        selector.close();
      } catch (IOException e) {
      } finally {
        selector = null;
      }
    }
  }
}
