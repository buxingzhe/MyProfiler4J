package org.myprofiler4j.java.hotloader;

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Properties;

public class HackSystem {

    public final static InputStream in = System.in;

    private static ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public static final PrintStream out = new PrintStream(buffer);

    public static final PrintStream err = out;

    public static String getBufferString() {
        return buffer.toString();
    }

    public static void clearBuffer() {
        buffer.reset();
    }

    public static void setSecurityManager(final SecurityManager sm) {
        System.setSecurityManager(sm);
    }

    public static SecurityManager getSecurityManager() {
        return System.getSecurityManager();
    }

    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
        System.arraycopy(src, srcPos, dest, destPos, length);
    }

    public static int identityHashCode(Object x) {
        return System.identityHashCode(x);
    }

    public static String clearProperty(String key) {
        return System.clearProperty(key);
    }

    public static Console console() {
        return System.console();
    }

    public static void exit(int status) {

    }

    public static void gc() {
        System.gc();
    }

    public static java.util.Map<String,String> getenv() {
        return System.getenv();
    }

    public static String getenv(String name) {
        return System.getenv(name);
    }

    public static Properties getProperties() {
        return System.getProperties();
    }

    public static String getProperty(String key) {
        return System.getProperty(key);
    }

    public static String getProperty(String key, String def) {
        return System.getProperty(key, def);
    }

    public static void load(String filename) {
        System.load(filename);
    }

    public static void loadLibrary(String libname) {
        System.loadLibrary(libname);
    }

    public static long nanoTime() {
        return System.nanoTime();
    }

}
