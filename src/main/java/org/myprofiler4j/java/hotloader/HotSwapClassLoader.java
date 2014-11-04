package org.myprofiler4j.java.hotloader;

/**
 * Hot swap class loader
 * @author nhuang
 * @since 2013-12-8
 */
public class HotSwapClassLoader extends ClassLoader {
	
	public HotSwapClassLoader() {
		super(HotSwapClassLoader.class.getClassLoader());
	}
	
	public HotSwapClassLoader(ClassLoader parentClassloader) {
	  super(parentClassloader);
	}
	
	public Class<?> loadBytes(byte[] clzByteCodes) {
		return defineClass(null, clzByteCodes, 0, clzByteCodes.length);
	}
}
