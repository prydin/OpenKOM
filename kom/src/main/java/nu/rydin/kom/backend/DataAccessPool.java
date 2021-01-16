/*
 * Created on Nov 4, 2003
 *
 * Distributed under the GPL license.
 * See http://www.gnu.org for details
 */
package nu.rydin.kom.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedList;
import nu.rydin.kom.exceptions.UnexpectedException;

/** @author Pontus Rydin */
public class DataAccessPool {
  private static final DataAccessPool s_instance;

  static {
    try {
      s_instance = new DataAccessPool();
    } catch (final UnexpectedException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private final LinkedList<DataAccess> m_pool = new LinkedList<>();

  public DataAccessPool() throws UnexpectedException {
    final int top = ServerSettings.getNumDataAccess();
    for (int idx = 0; idx < top; ++idx) {
      m_pool.add(createDataAccess());
    }
  }

  public static DataAccessPool instance() {
    return s_instance;
  }

  public DataAccess getDataAccess() throws UnexpectedException {
    // Have we already requested a da for this thread?
    //
    DataAccess da = null;
    synchronized (this) {
      if (!m_pool.isEmpty()) {
        da = m_pool.removeFirst();
      }
    }

    // Did we get anything? Is it working?
    //
    if (da == null || !da.isValid()) {
      da = createDataAccess();
    }

    // Associate with thread
    //
    return da;
  }

  private DataAccess createDataAccess() throws UnexpectedException {
    final Connection conn;
    try {
      Class.forName(ServerSettings.getJDBCDriverClass()).newInstance();
      conn = DriverManager.getConnection(ServerSettings.getJDBCConnectString());
      conn.setAutoCommit(false);
      //			Statement stmt = conn.createStatement();
      //			stmt.execute("SET AUTOCOMMIT=0");
      return new DataAccess(conn);
    } catch (final IllegalAccessException e) {
      throw new UnexpectedException(-1, "PANIC: Can't access driver", e);
    } catch (final ClassNotFoundException e) {
      throw new UnexpectedException(-1, "PANIC: Can't find driver", e);
    } catch (final InstantiationException e) {
      throw new UnexpectedException(-1, "PANIC: Can't instantiate driver", e);
    } catch (final SQLException e) {
      throw new UnexpectedException(-1, "PANIC: Error while creating connection", e);
    }
  }

  public synchronized void returnDataAccess(final DataAccess da) {
    // Whatever we do, let's not leave uncomitted transactions around
    //
    try {
      da.rollback();
    } catch (final UnexpectedException e) {
      // This DataAccess seems broken. Don't return to pool.
      //
      return;
    }

    // Return to pool
    //
    m_pool.addLast(da);
  }
}
