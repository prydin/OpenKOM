/*
 * This file contains software that has been made available under
 * The Frameworx Open License 1.0. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 The Frameworx Company
 * All Rights Reserved
 */

package com.frameworx.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * MRU (Most Recently Used) cache with a timed expiration mechanism. <br>
 * Works like <code>Map</code>, but limits the number of items to a preset number, but keeping only
 * the most recently used ones (hence the name). This implementation also has a timed expiration
 * feature that allows a user to specify the time a value is valid. After that time, the value is
 * discarded, regardless of when it was last accessed. An "eviction policy" can be specified to take
 * some action when a value is discarded from the cache, eihter because it expired, or it had to be
 * discarded to make room for a new value.
 *
 * @author Pontus Rydin
 */
public class MRUCache implements Map {
  /** Default eviction policy taking no action */
  static final EvictionPolicy s_voidPolicy =
      new EvictionPolicy() {
        @Override
		public void evict(final Object key, final Object value) {}
      };
  /** The cached data */
  private final HashMap<Object, Object> m_map;
  /** Head of eviction queue */
  private final ListAtom m_head;
  /**
   * Eviction policy, i.e. a class that will be called whenever a value is about to be discarded
   * from the cache.
   */
  private EvictionPolicy m_evictionPolicy;
  /** The specified maximum size */
  private int m_maxSize;

  /**
   * Creates a new <code>MRUCache</code> with a specified maximum size
   *
   * @param maxSize The maximum size.
   */
  public MRUCache(final int maxSize) {
    this(maxSize, MRUCache.s_voidPolicy);
  }

  /**
   * Creates a new <code>MRUCache</code> with a specified maximum size and an eviction policy that
   * will be called whenever a value is about to be discarded from the cache.
   *
   * @param maxSize The maximum size.
   * @param evictionPolicy The eviction policy
   */
  public MRUCache(final int maxSize, final EvictionPolicy evictionPolicy) {
    m_map = new HashMap<Object, Object>(maxSize);
    m_maxSize = maxSize;
    m_head = new ListAtom();
    m_evictionPolicy = evictionPolicy;
  }

  /** Removes all values from the cache. */
  @Override
  public synchronized void clear() {
    if (m_evictionPolicy != MRUCache.s_voidPolicy) {
      final Iterator itor = m_map.entrySet().iterator();
      while (itor.hasNext()) {
        final Map.Entry me = (Map.Entry) itor.next();
        m_evictionPolicy.evict(me.getKey(), me.getValue());
      }
    }
    m_map.clear();
    m_head.yank();
  }

  /**
   * Returns <code>true</code> if the specified key is present in the cache.
   *
   * @param key The key to look for
   */
  @Override
  public synchronized boolean containsKey(final Object key) {
    // TODO: This also updates MRU status. Is that really what we want?
    //
    return getCacheItem(key) != null;
  }

  /**
   * Returns <code>true</code> if the specified value id present in the cache.
   *
   * @param value The value to look for.
   */
  @Override
  public synchronized boolean containsValue(final Object value) {
    for (final Iterator itor = values().iterator(); itor.hasNext(); ) {
      if (value.equals(itor.next())) {
		  return true;
	  }
    }
    return false;
  }

  /** Returns the set of <code>Map.Entry</code> objects connecting keys to their values. */
  @Override
  public synchronized Set entrySet() {
    return m_map.entrySet();
  }

  /**
   * Returns <code>true</code> if the specified object is an <code>MRUCache</code> and its values
   * are equal to the called object.
   *
   * @param o The object to compare to
   */
  @Override
  public synchronized boolean equals(final Object o) {
    if (o == this) {
		return true;
	}
    if (!(o instanceof MRUCache)) {
		return false;
	}
    return ((MRUCache) o).m_map.equals(m_map);
  }

  /**
   * Returns the value associated with the specified key, or null if no value exists for this key.
   *
   * @param key The key
   */
  @Override
  public synchronized Object get(final Object key) {
    final CacheItem ci = getCacheItem(key);
    if (ci == null) {
		return null;
	}
    return ci.m_value;
  }

  /** Returns the hash code. */
  @Override
  public synchronized int hashCode() {
    return m_map.hashCode();
  }

  /** Returns <code>true</code> if the cache is empty. */
  @Override
  public synchronized boolean isEmpty() {
    return m_map.isEmpty();
  }

  /** Returns all the keys as a <code>Set</code> */
  @Override
  public synchronized Set keySet() {
    return m_map.keySet();
  }

  /**
   * Associates a key with a value and stores it in the cache. If the cache has reached its maximum
   * size, the least recently used value is discarded.
   *
   * @param key The key
   * @param value The value
   */
  @Override
  public synchronized Object put(final Object key, final Object value) {
    final CacheItem ci = getCacheItem(key);
    if (ci == null) {
		insertCacheItem(new CacheItem(key, value));
	} else {
		ci.m_value = value;
	}
    return value;
  }

  /**
   * Puts all the items in the specified map into this cache. Note that, if the specified map is
   * larger than the cache, only the <i>n</i> last items will be stored, where <i>n</i> is the
   * specified maximum size of the cache.
   *
   * @param t The map
   */
  @Override
  public void putAll(final Map t) {
    for (final Iterator itor = t.entrySet().iterator(); itor.hasNext(); ) {
      final Map.Entry entry = (Map.Entry) itor.next();
		put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Consciously remove the object corresponding to the <code>key</code> from the cache. The <code>
   * EvictionPolicy</code> will not be enforced on the removed object.
   *
   * @param key The key.
   */
  @Override
  public synchronized Object remove(final Object key) {
    final CacheItem ci = (CacheItem) m_map.remove(key);
    if (ci == null) {
		return null;
	}
    ci.remove();
    return ci.m_value;
  }

  /** Returns the current number of values in the cache. */
  @Override
  public int size() {
    return m_map.size();
  }

  /** Returns the values a <code>Collection</code> */
  @Override
  public synchronized Collection values() {
    final Collection v = m_map.values();
    final ArrayList<Object> answer = new ArrayList<Object>(v.size());
    for (final Iterator itor = v.iterator(); itor.hasNext(); ) {
      final CacheItem each = (CacheItem) itor.next();
      answer.add(each.m_value);
    }
    return answer;
  }

  /** Returns the eviction policy. */
  public EvictionPolicy getEvictionPolicy() {
    return m_evictionPolicy;
  }

  /**
   * Sets the eviction policy to be called when values are discarded from the cache.
   *
   * @param evictionPolicy The eviction policy.
   */
  public void setEvictionPolicy(final EvictionPolicy evictionPolicy) {
    m_evictionPolicy = evictionPolicy;
  }

  /** Retruns the specified maximum size. */
  public int getMaxSize() {
    return m_maxSize;
  }

  /**
   * Sets the maximum size. If the maximum size is smaller than the current size, some values may be
   * discarded from the cache.
   *
   * @param maxSize The new maximum size.
   */
  public synchronized void setMaxSize(final int maxSize) {
    final int oldSize = m_maxSize;
    m_maxSize = maxSize;
    if (oldSize > maxSize)
      //
      // Perhaps we need to kick something out?
      //
	{
		assertMaxSize(0);
	}
  }

  /**
   * Returns the <code>CacheItem</code> associated with a key, or null if no <code>CacheItem</code>
   * existed for the specified key.
   *
   * @param key The key
   */
  CacheItem getCacheItem(final Object key) {
    final CacheItem ci = (CacheItem) m_map.get(key);
    if (ci == null) {
		return null;
	}
    ci.succeed(m_head);
    return ci;
  }

  /** Add the <code>CacheItem</code> to the linked list and to the internal map. */
  void insertCacheItem(final CacheItem ci) {
    // Add to map and MRU list
    //
	  assertMaxSize(1);
    m_map.put(ci.m_key, ci);
    ci.succeed(m_head);
  }

  /**
   * Makes sure there is room for the specified number of additional items.
   *
   * @param toMakeRoomFor The number of additional items.
   */
  private void assertMaxSize(final int toMakeRoomFor) {
    final int maxMapSize = m_maxSize - toMakeRoomFor;
    while (m_map.size() > maxMapSize) {
      // We have to kick something out. Find the victim and remove it
      //
      final CacheItem victim = (CacheItem) m_head.previous();

      // Now we have determined the victim. Yank it from the MRU list
      //
      victim.remove();
      m_map.remove(victim.m_key);
      m_evictionPolicy.evict(victim.m_key, victim.m_value);
    }
  }

  /** Interface to objects that want to be informed when a value was discarded from the cache. */
  public interface EvictionPolicy {
    /**
     * Called when a value is about to be discarded from the cache.
     *
     * @param key The key
     * @param value The value.
     */
    void evict(Object key, Object value);
  }

  /**
   * Internal class wrapping cached values. Implements a doubly-linked list atom, which allows the
   * cache to keep an "eviction queue", i.e. a queue of values eligible to be discarded from the
   * cache. When the cache is full and it has to make room for a new item, the item at the tail end
   * of the eviction queue is discarded. Whenever a cache item is accessed, it is moved to the head
   * end of the queue.
   */
  class CacheItem extends ListAtom {
    static final long serialVersionUID = 2005;

    /** The key */
    protected final Object m_key;

    /** The value */
    protected Object m_value;

    /**
     * Constructs a new <code>CacheItem</code>
     *
     * @param key The key
     * @param value The value
     */
    CacheItem(final Object key, final Object value) {
      m_key = key;
      m_value = value;
    }

    Object getKey() {
      return m_key;
    }

    Object getValue() {
      return m_value;
    }

    void setValue(final Object value) {
      m_value = value;
    }

    /** Removes this <code>CacheItem</code> from the eviction queue */
    synchronized void remove() {
		yank();
    }
  }
}
