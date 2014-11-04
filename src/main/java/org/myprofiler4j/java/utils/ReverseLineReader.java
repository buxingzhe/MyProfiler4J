package org.myprofiler4j.java.utils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * A utility class for reading a file line by line from tail to head
 * 
 * @author nhuang
 *
 */
public class ReverseLineReader {
    private RandomAccessFile raf = null;
    private final FileChannel channel;
    private final String encoding;
    private long filePos;
    private ByteBuffer buf;
    private int bufPos;
    private ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private static final int BUFFER_SIZE = 8192;

    public ReverseLineReader(File file, String encoding) throws IOException {
        raf = new RandomAccessFile(file, "r");
        channel = raf.getChannel();
        filePos = raf.length();
        this.encoding = encoding;
    }

    public String readLine() throws IOException {
        while (true) {
            if (bufPos <= 0) {
                if (filePos == 0) {
                    if (baos == null) {
                        if(raf != null) {
                          try {
                            raf.close();
                            raf = null;
                          } catch (Exception e) {
                          }
                        }
                        return null;
                    }
                    String line = bufToString();
                    baos = null;
                    return line;
                }

                long start = Math.max(filePos - BUFFER_SIZE, 0);
                long end = filePos;
                long len = end - start;

                buf = channel.map(FileChannel.MapMode.READ_ONLY, start, len);
                bufPos = (int) len;
                filePos = start;
            }
            
            while (bufPos-- > 0) {
                byte c = buf.get(bufPos);
                if (c == '\n') {
                    return bufToString();
                } else if(c == '\r') {
                  continue;
                }
                baos.write(c);
            }
        }
    }
    
    public void reset() {
      bufPos = 0;
      baos = new ByteArrayOutputStream();
      try {
        filePos = raf.length();
      } catch (Exception e) {}
    }
    
    public void close() {
        try {
            if(raf != null) {
                raf.close();
            }           
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String bufToString() throws UnsupportedEncodingException {
        if (baos.size() == 0) {
            return "";
        }

        byte[] bytes = baos.toByteArray();
        for (int i = 0; i < bytes.length / 2; i++) {
            byte t = bytes[i];
            bytes[i] = bytes[bytes.length - i - 1];
            bytes[bytes.length - i - 1] = t;
        }

        baos.reset();

        return new String(bytes, encoding);
    }

    public static void main(String[] args) throws IOException {
        File file = new File("methodProfile_01092014_115148.txt");
        ReverseLineReader reader = new ReverseLineReader(file, "UTF-8");
        String line;
        while ((line = reader.readLine()) != null) {
            if(line.startsWith("19:"))
            System.out.println(line);
        }
        reader.close();
    }
}
