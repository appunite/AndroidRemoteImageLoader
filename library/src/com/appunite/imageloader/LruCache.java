/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appunite.imageloader;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Static library version of {@link android.util.LruCache}. Used to write apps
 * that run on API levels prior to 12. When running on API level 12 or above,
 * this implementation is still used; it does not try to switch to the
 * framework's implementation. See the framework SDK documentation for a class
 * overview.
 */
@SuppressWarnings("UnusedDeclaration")
public class LruCache<K, V> {
	private final LinkedHashMap<K, V> map;

	/** Size of this cache in units. Not necessarily the number of elements. */
	private int size;
	private final int maxSize;

	private int putCount;
	private int createCount;
	private int evictionCount;
	private int hitCount;
	private int missCount;

	/**
	 * @param maxSize
	 *            for caches that do not override {@link #sizeOf}, this is the
	 *            maximum number of entries in the cache. For all other caches,
	 *            this is the maximum sum of the sizes of the entries in this
	 *            cache.
	 */
	public LruCache(int maxSize) {
		if (maxSize <= 0) {
			throw new IllegalArgumentException("maxSize <= 0");
		}
		this.maxSize = maxSize;
		this.map = new LinkedHashMap<K, V>(0, 0.75f, true);
	}

	/**
	 * Called after a cache miss to compute a value for the corresponding key.
	 * Returns the computed value or null if no value can be computed. The
	 * default implementation returns null.
	 * 
	 * <p>
	 * The method is called without synchronization: other threads may access
	 * the cache while this method is executing.
	 * 
	 * <p>
	 * If a value for {@code key} exists in the cache when this method returns,
	 * the created value will be released with {@link #entryRemoved} and
	 * discarded. This can occur when multiple threads request the same key at
	 * the same time (causing multiple values to be created), or when one thread
	 * calls {@link #put} while another is creating a value for the same key.
	 */
	protected V create(K key) {
		return null;
	}

	/**
	 * Returns the number of times {@link #create(Object)} returned a value.
	 */
	public synchronized final int createCount() {
		return this.createCount;
	}

	/**
	 * Called for entries that have been evicted or removed. This method is
	 * invoked when a value is evicted to make space, removed by a call to
	 * {@link #remove}, or replaced by a call to {@link #put}. The default
	 * implementation does nothing.
	 * 
	 * <p>
	 * The method is called without synchronization: other threads may access
	 * the cache while this method is executing.
	 * 
	 * @param evicted
	 *            true if the entry is being removed to make space, false if the
	 *            removal was caused by a {@link #put} or {@link #remove}.
	 * @param newValue
	 *            the new value for {@code key}, if it exists. If non-null, this
	 *            removal was caused by a {@link #put}. Otherwise it was caused
	 *            by an eviction or a {@link #remove}.
	 */
	protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {
	}

	/**
	 * Clear the cache, calling {@link #entryRemoved} on each removed entry.
	 */
	public final void evictAll() {
		this.trimToSize(-1); // -1 will evict 0-sized elements
	}

	/**
	 * Returns the number of values that have been evicted.
	 */
	public synchronized final int evictionCount() {
		return this.evictionCount;
	}

	/**
	 * Returns the value for {@code key} if it exists in the cache or can be
	 * created by {@code #create}. If a value was returned, it is moved to the
	 * head of the queue. This returns null if a value is not cached and cannot
	 * be created.
	 */
	public final V get(K key) {
		if (key == null) {
			throw new NullPointerException("key == null");
		}

		V mapValue;
		synchronized (this) {
			mapValue = this.map.get(key);
			if (mapValue != null) {
				this.hitCount++;
				return mapValue;
			}
			this.missCount++;
		}

		/*
		 * Attempt to create a value. This may take a long time, and the map may
		 * be different when create() returns. If a conflicting value was added
		 * to the map while create() was working, we leave that value in the map
		 * and release the created value.
		 */

		V createdValue = this.create(key);
		if (createdValue == null) {
			return null;
		}

		synchronized (this) {
			this.createCount++;
			mapValue = this.map.put(key, createdValue);

			if (mapValue != null) {
				// There was a conflict so undo that last put
				this.map.put(key, mapValue);
			} else {
				this.size += this.safeSizeOf(key, createdValue);
			}
		}

		if (mapValue != null) {
			this.entryRemoved(false, key, createdValue, mapValue);
			return mapValue;
		} else {
			this.trimToSize(this.maxSize);
			return createdValue;
		}
	}

	/**
	 * Returns the number of times {@link #get} returned a value.
	 */
	public synchronized final int hitCount() {
		return this.hitCount;
	}

	/**
	 * For caches that do not override {@link #sizeOf}, this returns the maximum
	 * number of entries in the cache. For all other caches, this returns the
	 * maximum sum of the sizes of the entries in this cache.
	 */
	public synchronized final int maxSize() {
		return this.maxSize;
	}

	/**
	 * Returns the number of times {@link #get} returned null or required a new
	 * value to be created.
	 */
	public synchronized final int missCount() {
		return this.missCount;
	}

	/**
	 * Caches {@code value} for {@code key}. The value is moved to the head of
	 * the queue.
	 * 
	 * @return the previous value mapped by {@code key}.
	 */
	public final V put(K key, V value) {
		if (key == null || value == null) {
			throw new NullPointerException("key == null || value == null");
		}

		V previous;
		synchronized (this) {
			this.putCount++;
			this.size += this.safeSizeOf(key, value);
			previous = this.map.put(key, value);
			if (previous != null) {
				this.size -= this.safeSizeOf(key, previous);
			}
		}

		if (previous != null) {
			this.entryRemoved(false, key, previous, value);
		}

		this.trimToSize(this.maxSize);
		return previous;
	}

	/**
	 * Returns the number of times {@link #put} was called.
	 */
	public synchronized final int putCount() {
		return this.putCount;
	}

	/**
	 * Removes the entry for {@code key} if it exists.
	 * 
	 * @return the previous value mapped by {@code key}.
	 */
	public final V remove(K key) {
		if (key == null) {
			throw new NullPointerException("key == null");
		}

		V previous;
		synchronized (this) {
			previous = this.map.remove(key);
			if (previous != null) {
				this.size -= this.safeSizeOf(key, previous);
			}
		}

		if (previous != null) {
			this.entryRemoved(false, key, previous, null);
		}

		return previous;
	}

	private int safeSizeOf(K key, V value) {
		int result = this.sizeOf(key, value);
		if (result < 0) {
			throw new IllegalStateException("Negative size: " + key + "="
					+ value);
		}
		return result;
	}

	/**
	 * For caches that do not override {@link #sizeOf}, this returns the number
	 * of entries in the cache. For all other caches, this returns the sum of
	 * the sizes of the entries in this cache.
	 */
	public synchronized final int size() {
		return this.size;
	}

	/**
	 * Returns the size of the entry for {@code key} and {@code value} in
	 * user-defined units. The default implementation returns 1 so that size is
	 * the number of entries and max size is the maximum number of entries.
	 * 
	 * <p>
	 * An entry's size must not change while it is in the cache.
	 */
	protected int sizeOf(K key, V value) {
		return 1;
	}

	/**
	 * Returns a copy of the current contents of the cache, ordered from least
	 * recently accessed to most recently accessed.
	 */
	public synchronized final Map<K, V> snapshot() {
		return new LinkedHashMap<K, V>(this.map);
	}

	@Override
	public synchronized final String toString() {
		int accesses = this.hitCount + this.missCount;
		int hitPercent = accesses != 0 ? (100 * this.hitCount / accesses) : 0;
		return String.format(Locale.US,
				"LruCache[maxSize=%d,hits=%d,misses=%d,hitRate=%d%%]",
				this.maxSize, this.hitCount, this.missCount, hitPercent);
	}

	/**
	 * @param maxSize
	 *            the maximum size of the cache before returning. May be -1 to
	 *            evict even 0-sized elements.
	 */
	private void trimToSize(int maxSize) {
		while (true) {
			K key;
			V value;
			synchronized (this) {
				if (this.size < 0 || (this.map.isEmpty() && this.size != 0)) {
					throw new IllegalStateException(this.getClass().getName()
							+ ".sizeOf() is reporting inconsistent results!");
				}

				if (this.size <= maxSize || this.map.isEmpty()) {
					break;
				}

				Map.Entry<K, V> toEvict = this.map.entrySet().iterator().next();
				key = toEvict.getKey();
				value = toEvict.getValue();
				this.map.remove(key);
				this.size -= this.safeSizeOf(key, value);
				this.evictionCount++;
			}

			this.entryRemoved(true, key, value, null);
		}
	}
}
