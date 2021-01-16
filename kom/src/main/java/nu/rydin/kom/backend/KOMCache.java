/*
 * Created on Jun 6, 2004
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend;

import com.frameworx.util.MRUCache;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import nu.rydin.kom.structs.CacheInformation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** @author Pontus Rydin */
public class KOMCache extends MRUCache {
  private static final Logger LOG = LogManager.getLogger(MRUCache.class);

  private final ThreadLocal<Transaction> transaction = ThreadLocal.withInitial(Transaction::new);
  private long era = 0;
  private long numAccesses = 0;
  private long numHits = 0;
  private boolean committing = false;

  public KOMCache(final int maxSize) {
    super(maxSize);
  }

  public KOMCache(final int maxSize, final EvictionPolicy evictionPolicy) {
    super(maxSize, evictionPolicy);
  }

  @Override
  public synchronized Object put(final Object key, final Object value) {
    if (committing) {
      super.put(key, value);
    } else {
      LOG.warn("You should use deferredPut instead!");
      deferredPut(key, value);
    }
    return value;
  }

  public synchronized Object rawGet(final Object key) {
    return super.get(key);
  }

  @Override
  public synchronized Object get(final Object key) {
    final Object answer = innerGet(key);
    ++numAccesses;
    if (answer != null) {
      ++numHits;
    }
    return answer;
  }

  private synchronized Object innerGet(final Object key) {
    final Transaction tx = transaction.get();

    // Pending deletion in this tx? No hit!
    //
    if (tx.pendingDeletion(key)) {
      return null;
    }
    final Entry entry = (Entry) super.get(key);
    return entry != null ? entry.data : null;
  }

  public long getNumAccesses() {
    return numAccesses;
  }

  public long getNumHits() {
    return numHits;
  }

  public CacheInformation getStatistics() {
    return new CacheInformation(numAccesses, numHits);
  }

  public synchronized void registerInvalidation(final Object key) {
    transaction.get().delete(key);
  }

  public synchronized void deferredPut(final Object key, final Object value) {
    transaction.get().put(key, new Entry(value, ++era));
  }

  public synchronized void commit() {
    committing = true;
    transaction.get().commit(this);
    committing = false;
  }

  public synchronized void rollback() {
    transaction.get().rollback();
  }

  private static class Entry {
    private final Object data;

    private final long era;

    public Entry(final Object data, final long era) {
      super();
      this.data = data;
      this.era = era;
    }
  }

  private static class Transaction {
    private final Set<Object> deletions = new HashSet<>();

    private final Map<Object, Entry> dirtyData = new HashMap<>();

    private Transaction() {}

    public void put(final Object key, final Entry value) {
      dirtyData.put(key, value);
    }

    public void delete(final Object key) {
      dirtyData.remove(key);
      deletions.add(key);
    }

    public Entry get(final Object key) {
      return dirtyData.get(key);
    }

    public boolean pendingDeletion(final Object key) {
      return deletions.contains(key);
    }

    public void commit(final KOMCache cache) {
      for (final Map.Entry<Object, Entry> each : dirtyData.entrySet()) {
        final Object key = each.getKey();
        final Entry dirty = each.getValue();
        final Entry clean = (Entry) cache.rawGet(key);

        // If dirty data isn't stale...
        //
        if (clean == null || dirty.era > clean.era) {
          cache.put(key, dirty);
        }
      }
      for (final Iterator itor = deletions.iterator(); itor.hasNext(); ) {
        cache.remove(itor.next());
      }
      rollback();
    }

    public void rollback() {
      dirtyData.clear();
      deletions.clear();
    }
  }
}
