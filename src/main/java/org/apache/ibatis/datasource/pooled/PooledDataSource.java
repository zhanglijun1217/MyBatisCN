/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.datasource.pooled;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * This is a simple, synchronous, thread-safe database connection pool.
 *
 * @author Clinton Begin
 */
public class PooledDataSource implements DataSource {

  private static final Log log = LogFactory.getLog(PooledDataSource.class);

  // 利用PoolState管理 数据库连接池
  // 内部存储了连接池的活跃连接数量、空闲连接数量等~
  // 获取连接时 会为state同步加锁
  private final PoolState state = new PoolState(this);

  // 持有一个UnPooledDataSource对象
  private final UnpooledDataSource dataSource;

  // 和连接池设置有关的配置项
  protected int poolMaximumActiveConnections = 10; // 池最大活跃连接数
  protected int poolMaximumIdleConnections = 5;// 池最大空闲连接数99
  protected int poolMaximumCheckoutTime = 20000; // 连接池连接借出的最大时长
  protected int poolTimeToWait = 20000; // 无空闲链接且最老连接借出时间不超过poolMaximumCheckoutTime 轮询的休息间隔


  protected int poolMaximumLocalBadConnectionTolerance = 3;// 获取不到的容忍

  protected String poolPingQuery = "NO PING QUERY SET"; // ping请求的测试sql
  protected boolean poolPingEnabled;// flag
  protected int poolPingConnectionsNotUsedFor; // 发送ping请求的时间flag

  // 存储池子中的连接的编码，编码用("" + url + username + password).hashCode()算出来
  // 因此，整个池子中的所有连接的编码必须是一致的，里面的连接是等价的
  private int expectedConnectionTypeCode;

  public PooledDataSource() {
    // dataSource 引用 其实还是 UnPooledDataSource
    dataSource = new UnpooledDataSource();
  }

  public PooledDataSource(UnpooledDataSource dataSource) {
    this.dataSource = dataSource;
  }

  public PooledDataSource(String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driver, url, username, password);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driver, url, driverProperties);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, username, password);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, driverProperties);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  @Override
  public Connection getConnection() throws SQLException {
    // 这里拿到的是PooledConnection的动态代理对象 Proxy.newInstance。。。。
    return popConnection(dataSource.getUsername(), dataSource.getPassword()).getProxyConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return popConnection(username, password).getProxyConnection();
  }

  @Override
  public void setLoginTimeout(int loginTimeout) {
    DriverManager.setLoginTimeout(loginTimeout);
  }

  @Override
  public int getLoginTimeout() {
    return DriverManager.getLoginTimeout();
  }

  @Override
  public void setLogWriter(PrintWriter logWriter) {
    DriverManager.setLogWriter(logWriter);
  }

  @Override
  public PrintWriter getLogWriter() {
    return DriverManager.getLogWriter();
  }

  /**
   * 设置连接池的驱动
   * @param driver 连接池驱动
   */
  public void setDriver(String driver) {
    dataSource.setDriver(driver);
    forceCloseAll();
  }

  public void setUrl(String url) {
    dataSource.setUrl(url);
    forceCloseAll();
  }

  public void setUsername(String username) {
    dataSource.setUsername(username);
    forceCloseAll();
  }

  public void setPassword(String password) {
    dataSource.setPassword(password);
    forceCloseAll();
  }

  public void setDefaultAutoCommit(boolean defaultAutoCommit) {
    dataSource.setAutoCommit(defaultAutoCommit);
    forceCloseAll();
  }

  public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
    dataSource.setDefaultTransactionIsolationLevel(defaultTransactionIsolationLevel);
    forceCloseAll();
  }

  public void setDriverProperties(Properties driverProps) {
    dataSource.setDriverProperties(driverProps);
    forceCloseAll();
  }

  /**
   * Sets the default network timeout value to wait for the database operation to complete. See {@link Connection#setNetworkTimeout(java.util.concurrent.Executor, int)}
   * 
   * @param milliseconds
   *          The time in milliseconds to wait for the database operation to complete.
   * @since 3.5.2
   */
  public void setDefaultNetworkTimeout(Integer milliseconds) {
    dataSource.setDefaultNetworkTimeout(milliseconds);
    forceCloseAll();
  }

  /**
   * The maximum number of active connections.
   *
   * @param poolMaximumActiveConnections The maximum number of active connections
   */
  public void setPoolMaximumActiveConnections(int poolMaximumActiveConnections) {
    this.poolMaximumActiveConnections = poolMaximumActiveConnections;
    forceCloseAll();
  }

  /**
   * The maximum number of idle connections.
   *
   * @param poolMaximumIdleConnections The maximum number of idle connections
   */
  public void setPoolMaximumIdleConnections(int poolMaximumIdleConnections) {
    this.poolMaximumIdleConnections = poolMaximumIdleConnections;
    forceCloseAll();
  }

  /**
   * The maximum number of tolerance for bad connection happens in one thread
   * which are applying for new {@link PooledConnection}.
   *
   * @param poolMaximumLocalBadConnectionTolerance
   * max tolerance for bad connection happens in one thread
   *
   * @since 3.4.5
   */
  public void setPoolMaximumLocalBadConnectionTolerance(
      int poolMaximumLocalBadConnectionTolerance) {
    this.poolMaximumLocalBadConnectionTolerance = poolMaximumLocalBadConnectionTolerance;
  }

  /**
   * The maximum time a connection can be used before it *may* be
   * given away again.
   *
   * @param poolMaximumCheckoutTime The maximum time
   */
  public void setPoolMaximumCheckoutTime(int poolMaximumCheckoutTime) {
    this.poolMaximumCheckoutTime = poolMaximumCheckoutTime;
    forceCloseAll();
  }

  /**
   * The time to wait before retrying to get a connection.
   *
   * @param poolTimeToWait The time to wait
   */
  public void setPoolTimeToWait(int poolTimeToWait) {
    this.poolTimeToWait = poolTimeToWait;
    forceCloseAll();
  }

  /**
   * The query to be used to check a connection.
   *
   * @param poolPingQuery The query
   */
  public void setPoolPingQuery(String poolPingQuery) {
    this.poolPingQuery = poolPingQuery;
    forceCloseAll();
  }

  /**
   * Determines if the ping query should be used.
   *
   * @param poolPingEnabled True if we need to check a connection before using it
   */
  public void setPoolPingEnabled(boolean poolPingEnabled) {
    this.poolPingEnabled = poolPingEnabled;
    forceCloseAll();
  }

  /**
   * If a connection has not been used in this many milliseconds, ping the
   * database to make sure the connection is still good.
   *
   * @param milliseconds the number of milliseconds of inactivity that will trigger a ping
   */
  public void setPoolPingConnectionsNotUsedFor(int milliseconds) {
    this.poolPingConnectionsNotUsedFor = milliseconds;
    forceCloseAll();
  }

  public String getDriver() {
    return dataSource.getDriver();
  }

  public String getUrl() {
    return dataSource.getUrl();
  }

  public String getUsername() {
    return dataSource.getUsername();
  }

  public String getPassword() {
    return dataSource.getPassword();
  }

  public boolean isAutoCommit() {
    return dataSource.isAutoCommit();
  }

  public Integer getDefaultTransactionIsolationLevel() {
    return dataSource.getDefaultTransactionIsolationLevel();
  }

  public Properties getDriverProperties() {
    return dataSource.getDriverProperties();
  }

  /**
   * @since 3.5.2
   */
  public Integer getDefaultNetworkTimeout() {
    return dataSource.getDefaultNetworkTimeout();
  }

  public int getPoolMaximumActiveConnections() {
    return poolMaximumActiveConnections;
  }

  public int getPoolMaximumIdleConnections() {
    return poolMaximumIdleConnections;
  }

  public int getPoolMaximumLocalBadConnectionTolerance() {
    return poolMaximumLocalBadConnectionTolerance;
  }

  public int getPoolMaximumCheckoutTime() {
    return poolMaximumCheckoutTime;
  }

  public int getPoolTimeToWait() {
    return poolTimeToWait;
  }

  public String getPoolPingQuery() {
    return poolPingQuery;
  }

  public boolean isPoolPingEnabled() {
    return poolPingEnabled;
  }

  public int getPoolPingConnectionsNotUsedFor() {
    return poolPingConnectionsNotUsedFor;
  }

  /**
   * 将活动和空闲的连接全部关闭
   */
  public void forceCloseAll() {
    synchronized (state) { // 增加同步锁
      // 重新计算和更新连接类型编码
      expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
      // 依次关闭所有的活动连接
      for (int i = state.activeConnections.size(); i > 0; i--) {
        try {
          PooledConnection conn = state.activeConnections.remove(i - 1);
          conn.invalidate();

          Connection realConn = conn.getRealConnection();
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
      // 依次关闭所有的空闲连接
      for (int i = state.idleConnections.size(); i > 0; i--) {
        try {
          PooledConnection conn = state.idleConnections.remove(i - 1);
          conn.invalidate();

          Connection realConn = conn.getRealConnection();
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("PooledDataSource forcefully closed/removed all connections.");
    }
  }

  public PoolState getPoolState() {
    return state;
  }

  /**
   * 计算该连接池中连接的类型编码
   * @param url 连接地址
   * @param username 用户名
   * @param password 密码
   * @return 类型编码
   */
  private int assembleConnectionTypeCode(String url, String username, String password) {
    // 所有连接等价
    // 同一连接池中的连接 PooledConnection hash值都相等
    return ("" + url + username + password).hashCode();
  }

  /**
   * 收回一个连接
   * getCollection中返回都是PooledConnection的代理对象
   * 在pooledConnection的代理invoke逻辑中 拦截了close调用，不是去close连接，而是调用PooledDataSource的pushConnection回收连接
   * @param conn 连接
   * @throws SQLException
   */
  protected void pushConnection(PooledConnection conn) throws SQLException {
    synchronized (state) {
      // 将该连接从活跃连接中删除
      state.activeConnections.remove(conn);
      if (conn.isValid()) { // 当前连接是可用的
        // 判断空闲连接池未满 + 该连接确实属于该连接池
        if (state.idleConnections.size() < poolMaximumIdleConnections && conn.getConnectionTypeCode() == expectedConnectionTypeCode) {
          state.accumulatedCheckoutTime += conn.getCheckoutTime(); // 增加从连接池中总共借出时间
          if (!conn.getRealConnection().getAutoCommit()) { // 如果连接没有设置自动提交
            // 将未完成的操作回滚
            conn.getRealConnection().rollback();
          }
          // 重新整理连接
          PooledConnection newConn = new PooledConnection(conn.getRealConnection(), this);
          // 将连接放入空闲连接池
          state.idleConnections.add(newConn);
          newConn.setCreatedTimestamp(conn.getCreatedTimestamp());
          newConn.setLastUsedTimestamp(conn.getLastUsedTimestamp());
          // 设置连接为未校验，以便取出时重新校验
          conn.invalidate();
          if (log.isDebugEnabled()) {
            log.debug("Returned connection " + newConn.getRealHashCode() + " to pool.");
          }
          state.notifyAll(); // 唤醒所有阻塞等待空闲连接的线程
        } else {
          // 连接池已满或者该连接不属于该连接池
          state.accumulatedCheckoutTime += conn.getCheckoutTime(); //增加从连接池中总共借出时间
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }
          // 直接关闭连接，而不是将其放入连接池中
          conn.getRealConnection().close(); // 当空闲连接池已满 才真正关闭连接
          if (log.isDebugEnabled()) {
            log.debug("Closed connection " + conn.getRealHashCode() + ".");
          }
          conn.invalidate(); // 置为失效的
        }
      } else { // 当前连接不可用
        if (log.isDebugEnabled()) {
          log.debug("A bad connection (" + conn.getRealHashCode() + ") attempted to return to the pool, discarding connection.");
        }
        state.badConnectionCount++; // 坏连接++
      }
    }
  }

  /**
   * 从池化数据源中给出一个连接
   * 1.检测当前连接池中是否有空闲的有效连接，如果有，则直接返回连接；如果没有，则继续执行下一步。
   * 2.检查连接池当前的活跃连接数是否已经达到上限值，如果未达到，则尝试创建一个新的数据库连接，并在创建成功之后，返回新建的连接；如果已达到最大上限，则往下执行。
   * 3.检查活跃连接中是否有连接超时，如果有，则将超时的连接从活跃连接集合中移除，并重复步骤 2；如果没有，则执行下一步。
   * 4.当前请求数据库连接的线程阻塞等待，并定期执行前面三步检测相应的分支是否可能获取连接。
   *
   * @param username 用户名
   * @param password 密码
   * @return 池化的数据库连接
   * @throws SQLException
   */
  private PooledConnection popConnection(String username, String password) throws SQLException {
    // 某次请求等待多轮也只能算作发生了一次等待 一个标志
    boolean countedWait = false;
    PooledConnection conn = null;
    // 用于统计取出连接花费的时长的时间起点
    long t = System.currentTimeMillis();
    int localBadConnectionCount = 0;

    while (conn == null) {
      // 获取连接轮询
      // 给state加同步锁
      synchronized (state) {
        if (!state.idleConnections.isEmpty()) { // 池中存在空闲连接
          // 左移操作，从空闲的连接数组中取出第一个连接
          conn = state.idleConnections.remove(0);
          if (log.isDebugEnabled()) {
            log.debug("Checked out connection " + conn.getRealHashCode() + " from pool.");
          }
        } else { // 池中没有空余连接
          if (state.activeConnections.size() < poolMaximumActiveConnections) { // 活跃连接数组中还有空余位置 判断max值
            // 可以创建新连接，也是通过DriverManager.getConnection拿到的连接
            conn = new PooledConnection(dataSource.getConnection(), this);
            if (log.isDebugEnabled()) {
              log.debug("Created connection " + conn.getRealHashCode() + ".");
            }
          } else { // 活跃连接数组已满，不能创建新连接
            // 找到借出去最久的连接
            PooledConnection oldestActiveConnection = state.activeConnections.get(0);
            // 查看借出去最久的连接已经被借了多久
            long longestCheckoutTime = oldestActiveConnection.getCheckoutTime();
            if (longestCheckoutTime > poolMaximumCheckoutTime) { // 借出时间超过设定的借出时长
              // 声明该连接超期不还
              state.claimedOverdueConnectionCount++; // 超时的连接次数+1
              state.accumulatedCheckoutTimeOfOverdueConnections += longestCheckoutTime; //过期的连接数总checkout时间+longestCheckoutTime
              state.accumulatedCheckoutTime += longestCheckoutTime; // 使用方从连接池中取出连接到归还连接的总时长+longestCheckoutTime 因为下面要从活跃连接数组中剔除
              // 因超期不还而从池中除名
              state.activeConnections.remove(oldestActiveConnection);
              if (!oldestActiveConnection.getRealConnection().getAutoCommit()) { // 如果超期不还的连接没有设置自动提交事务
                // 尝试替它提交回滚事务
                try {
                  oldestActiveConnection.getRealConnection().rollback();
                } catch (SQLException e) {
                  // 即使替它回滚事务的操作失败，也不抛出异常，仅仅做一下记录
                  log.debug("Bad connection. Could not roll back");
                }
              }
              // 新建一个PooledConnection连接 替代 最早超期不还的池连接
              // 但是realConnection还是复用之前最早超期不还连接的realConnection和参数
              conn = new PooledConnection(oldestActiveConnection.getRealConnection(), this);
              conn.setCreatedTimestamp(oldestActiveConnection.getCreatedTimestamp());
              conn.setLastUsedTimestamp(oldestActiveConnection.getLastUsedTimestamp());
              oldestActiveConnection.invalidate(); // 最早的超时活跃链接失效
              if (log.isDebugEnabled()) {
                log.debug("Claimed overdue connection " + conn.getRealHashCode() + ".");
              }
            } else { // 借出去最久的连接，并未超期
              // 继续等待，等待有连接归还到连接池
              try {
                if (!countedWait) {
                  // 记录发生等待的次数。某次请求等待多轮也只能算作发生了一次等待
                  // countedWait只是做这个falg用
                  state.hadToWaitCount++;
                  countedWait = true;
                }
                if (log.isDebugEnabled()) {
                  log.debug("Waiting as long as " + poolTimeToWait + " milliseconds for connection.");
                }
                long wt = System.currentTimeMillis();
                // 沉睡一段时间再试，防止一直占有计算资源
                state.wait(poolTimeToWait); // 给个一定时间缓冲 在回收连接的时候会notifyAll唤醒
                state.accumulatedWaitTime += System.currentTimeMillis() - wt; // 记录阻塞等待时间
              } catch (InterruptedException e) {
                break;
              }
            }
          }
        }
        if (conn != null) { // 取到了连接
          // 判断连接是否可用
          if (conn.isValid()) { // 如果连接可用
            if (!conn.getRealConnection().getAutoCommit()) { // 该连接没有设置自动提交
              // 回滚未提交的操作
              conn.getRealConnection().rollback();
            }
            // 每个借出去的连接都到打上数据源的连接类型编码，以便在归还时确保正确
            conn.setConnectionTypeCode(assembleConnectionTypeCode(dataSource.getUrl(), username, password));
            // 数据记录操作
            conn.setCheckoutTimestamp(System.currentTimeMillis());
            conn.setLastUsedTimestamp(System.currentTimeMillis());
            state.activeConnections.add(conn); // 活跃连接数增加
            state.requestCount++; //请求次数++
            state.accumulatedRequestTime += System.currentTimeMillis() - t; // 获取连接时间+
          } else { // 连接不可用
            if (log.isDebugEnabled()) {
              log.debug("A bad connection (" + conn.getRealHashCode() + ") was returned from the pool, getting another connection.");
            }
            state.badConnectionCount++; // 坏连接+1
            localBadConnectionCount++; // 一次获取本地检测到的坏连接数量
            // 直接删除连接
            conn = null;
            // 如果没有一个连接能用，说明连不上数据库
            // 这里判断标准是localBadConnectionCount 比 最大空闲连接数 + 一个最大容忍度
            if (localBadConnectionCount > (poolMaximumIdleConnections + poolMaximumLocalBadConnectionTolerance)) {
              if (log.isDebugEnabled()) {
                log.debug("PooledDataSource: Could not get a good connection to the database.");
              }
              throw new SQLException("PooledDataSource: Could not get a good connection to the database.");
            }
          }
        }
      }
      // 如果到这里还没拿到连接，则会循环此过程，继续尝试取连接
    }

    if (conn == null) {
      if (log.isDebugEnabled()) {
        log.debug("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
      }
      throw new SQLException("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
    }

    return conn;
  }

  /**
   * Method to check to see if a connection is still usable
   * 心跳检查连接是否还活着
   *
   * @param conn - the connection to check
   * @return True if the connection is still usable
   */
  protected boolean pingConnection(PooledConnection conn) {
    boolean result = true;

    try {
      // 检测底层数据库连接是否已经关闭
      result = !conn.getRealConnection().isClosed();
    } catch (SQLException e) {
      if (log.isDebugEnabled()) {
        log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
      }
      result = false;
    }
  // 如果底层与数据库的网络连接没断开，则需要检测poolPingEnabled字段的配置，决定
  // 是否能执行ping操作。另外，ping操作不能频繁执行，只有超过一定时长
  // (超过poolPingConnectionsNotUsedFor指定的时长)未使用的连接，才需要ping
  // 操作来检测数据库连接是否正常
    if (result) {
      if (poolPingEnabled) {
        if (poolPingConnectionsNotUsedFor >= 0 && conn.getTimeElapsedSinceLastUse() > poolPingConnectionsNotUsedFor) {
          try {
            if (log.isDebugEnabled()) {
              log.debug("Testing connection " + conn.getRealHashCode() + " ...");
            }
            // 执行poolPingQuery字段中记录的测试SQL语句
            Connection realConn = conn.getRealConnection();
            try (Statement statement = realConn.createStatement()) {
              statement.executeQuery(poolPingQuery).close();
            }
            if (!realConn.getAutoCommit()) {
              realConn.rollback();
            }
            result = true; // 不抛异常，即为成功
            if (log.isDebugEnabled()) {
              log.debug("Connection " + conn.getRealHashCode() + " is GOOD!");
            }
          } catch (Exception e) {
            log.warn("Execution of ping query '" + poolPingQuery + "' failed: " + e.getMessage());
            try {
              conn.getRealConnection().close();
            } catch (Exception e2) {
              //ignore
            }
            result = false; // 有异常则为失败
            if (log.isDebugEnabled()) {
              log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
            }
          }
        }
      }
    }
    return result;
  }

  /**
   * Unwraps a pooled connection to get to the 'real' connection
   *
   * @param conn - the pooled connection to unwrap
   * @return The 'real' connection
   */
  public static Connection unwrapConnection(Connection conn) {
    if (Proxy.isProxyClass(conn.getClass())) {
      InvocationHandler handler = Proxy.getInvocationHandler(conn);
      if (handler instanceof PooledConnection) {
        return ((PooledConnection) handler).getRealConnection();
      }
    }
    return conn;
  }

  @Override
  protected void finalize() throws Throwable {
    forceCloseAll();
    super.finalize();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException(getClass().getName() + " is not a wrapper.");
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) {
    return false;
  }

  @Override
  public Logger getParentLogger() {
    return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  }

}
