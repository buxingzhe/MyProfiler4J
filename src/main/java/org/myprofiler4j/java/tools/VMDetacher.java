package org.myprofiler4j.java.tools;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.myprofiler4j.java.utils.SecurityUtil;
import org.myprofiler4j.java.utils.Utilities;

/**
 * A utility class for terminating VM attach process
 * @author nhuang
 *
 */
public class VMDetacher {

    public static void main(String[] args) {
        if(args == null || args.length < 1) {
            System.out.println("VM attacher port not specified.\n Usage: detachVM <port>");
            System.exit(-1);
        }
        String password = null;
        while(password == null || password.isEmpty()) {
          password = new String(System.console().readPassword("Please enter adminstrator's password:"));
        }
        
        SocketChannel channel = null;
        try {
            channel = SocketChannel.open();
            channel.socket().setSoTimeout(10000);
            channel.socket().setReceiveBufferSize(1024);
            
            InetSocketAddress inetAddr = new InetSocketAddress(Integer.valueOf(args[0]));
            channel.connect(inetAddr);
            int i = 0;
            while (!channel.finishConnect() && i < 3) {
                Thread.sleep(10);
                i++;
            }
            if(channel.isConnected()) {
                byte[] passwordBytes = SecurityUtil.encrypt(password, "admin");
                ByteBuffer bf = ByteBuffer.allocate(passwordBytes.length + 4);
                bf.putInt(passwordBytes.length);
                bf.put(passwordBytes);
                bf.flip();
                while(bf.hasRemaining()) {
                  channel.write(bf);
                }
                ByteBuffer buf = ByteBuffer.allocate(1024);
                try {
                  channel.read(buf);
                } catch (Exception e) {
                }
                buf.flip();
                System.out.println(Utilities.toString(buf));
            } else {
                System.out.println("Can't establish connection to VM attacher.");
                System.exit(-1);
            }
        } catch(Exception ex) {
            ex.printStackTrace();
            System.out.println("DetachVM failed: " + ex.getMessage());
        } finally {
            try {
                if(channel != null) {
                    channel.close();
                }
            } catch (Exception e) {}
        }
    }
}
