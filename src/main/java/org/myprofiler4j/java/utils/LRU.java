package org.myprofiler4j.java.utils;

import java.util.LinkedHashMap;

@SuppressWarnings("serial")
public class LRU<K,V> extends LinkedHashMap<K, V> {

    private int maxSize = 0;
    
    public LRU(int maxSize) {
        this.maxSize = maxSize;
    }
    
    @Override
    protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}
