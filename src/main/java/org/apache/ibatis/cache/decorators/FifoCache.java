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
package org.apache.ibatis.cache.decorators;

import java.util.Deque;
import java.util.LinkedList;

import org.apache.ibatis.cache.Cache;

/**
 * FIFO (first in, first out) cache decorator.
 * 先进先出缓存 具有缓存淘汰的功能
 * @author Clinton Begin
 */
public class FifoCache implements Cache {

  // 被装饰对象
  private final Cache delegate;
  // 按照写入顺序保存了缓存数据的键
  private final Deque<Object> keyList;
  // 缓存空间的大小
  private int size;

  public FifoCache(Cache delegate) {
    this.delegate = delegate;
    this.keyList = new LinkedList<>();
    this.size = 1024;
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(int size) {
    this.size = size;
  }

  /**
   * 向缓存写入一条数据
   * @param key 数据的键
   * @param value 数据的值
   */
  @Override
  public void putObject(Object key, Object value) {
    cycleKeyList(key);
    delegate.putObject(key, value);
  }

  @Override
  public Object getObject(Object key) {
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyList.clear();
  }

  /**
   * 记录当前放入的数据的键，同时根据空间设置清除超出的数据
   * @param key 当前放入的数据的键
   */
  private void cycleKeyList(Object key) {
    keyList.addLast(key);
    if (keyList.size() > size) {
      // 超过阈值 就进行清理
      // removeFirst方法 为 null时不会报错
      Object oldestKey = keyList.removeFirst();
      delegate.removeObject(oldestKey);
    }
  }

}
