package org.filestacker.utils;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

public class HashBiMap<T1, T2> {
	Map<T1, T2> hash;
	Map<T2, T1> cohash;

	private static final Logger logger = Logger.getLogger(HashBiMap.class);

	public HashBiMap() {
		hash = new HashMap<T1, T2>();
		cohash = new HashMap<T2, T1>();
	}

	public void put(T1 key, T2 value) {
		T2 previous = hash.put(key, value);
		cohash.remove(previous);
		if (cohash.containsKey(value)) {
			logger.warn("HashBiMap supposed to satisfy k1 -> v1 & v1 -> k1, but '"+value+"' was already there. It may hurt you.");
		}
		cohash.put(value, key);
	}

	public HashBiMap<T2, T1> inverse() {
		HashBiMap<T2, T1> inverse = new HashBiMap<T2, T1>();
		inverse.hash = this.cohash;
		inverse.cohash = this.hash;
		return inverse;
	}

	public T2 get(T1 key) {
		return hash.get(key);
	}

	public void remove(T1 key) {
		T2 value = hash.remove(key);
		cohash.remove(value);
	}

}
