/*
 * Created on Jul 21, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org/ for details.
 */

package nu.rydin.kom.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** @author <a href=mailto:jepson@xyzzy.se>Jepson</a> */
public class CompoundHashMap<K, V> {
  private final HashMap<K, V> keys;
  private final HashMap<V, K> values;

  public CompoundHashMap() {
    keys = new HashMap<K, V>();
    values = new HashMap<V, K>();
  }

  public void put(final K key, final V value) {
      removeByKey(key);
      removeByValue(value);
    keys.put(key, value);
    values.put(value, key);
  }

  public void putAll(final Map<K, V> m) {
    final Iterator<K> it = m.keySet().iterator();
    while (it.hasNext()) {
      final K key = it.next();
        put(key, m.get(key));
    }
  }

  public void removeByKey(final K key) {
    final V value = keys.get(key);
    keys.remove(key);
    values.remove(value);
  }

  public void removeByValue(final V value) {
    final K key = values.get(value);
    values.remove(value);
    keys.remove(key);
  }

  public V getByKey(final K key) {
    return keys.get(key);
  }

  public K getByValue(final V value) {
    return values.get(value);
  }

  public boolean containsKey(final K key) {
    return keys.containsKey(key);
  }

  public boolean containsValue(final Object value) {
    return values.containsKey(value);
  }

  public Set<K> keySet() {
    return keys.keySet();
  }

  public Set<V> valueSet() {
    return values.keySet();
  }

  public void clear() {
    keys.clear();
    values.clear();
  }
}
