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
package org.apache.ibatis.scripting.xmltags;

import java.util.Map;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 * 在动态 SQL 语句中，我们可以使用 <foreach> 标签对一个集合进行迭代。
 * 在迭代过程中，我们可以通过 index 属性值指定的变量作为元素的下标索引（迭代 Map 集合的话，就是 Key 值），
 * 使用 item 属性值指定的变量作为集合元素（迭代 Map 集合的话，就是 Value 值）。
 * 另外，我们还可以通过 open 和 close 属性在迭代开始前和结束后添加相应的字符串，
 * 也允许使用 separator 属性自定义分隔符。这里要介绍的 ForeachSqlNode 就是 <foreach> 标签的抽象。
 */
public class ForEachSqlNode implements SqlNode {
  public static final String ITEM_PREFIX = "__frch_";

  // 表达式求值器
  private final ExpressionEvaluator evaluator;
  // collection属性的值
  private final String collectionExpression;

  // 节点内的内容
  private final SqlNode contents;


  // open属性的值，即元素左侧插入的字符串
  private final String open;
  // close属性的值，即元素右侧插入的字符串
  private final String close;
  // separator属性的值，即元素分隔符
  private final String separator;
  // item属性的值，即元素
  private final String item;
  // index属性的值，即元素的编号
  private final String index;

  // 配置信息
  private final Configuration configuration;

  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }

  /**
   * 完成该节点自身的解析
   * @param context 上下文环境，节点自身的解析结果将合并到该上下文环境中
   * @return 解析是否成功
   */
  @Override
  public boolean apply(DynamicContext context) {
    // 获取环境上下文信息
    Map<String, Object> bindings = context.getBindings();
    // 交给表达式求值器解析表达式，从而获得迭代器
    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings);
    if (!iterable.iterator().hasNext()) { // 没有可以迭代的元素
      // 不需要拼接信息，直接返回
      return true;
    }
    boolean first = true;
    // 添加open字符串
    applyOpen(context);
    int i = 0;
    for (Object o : iterable) {
      DynamicContext oldContext = context;
      if (first || separator == null) {
        // 添加元素
        // 第一个元素追加空字符串
        context = new PrefixedContext(context, "");
      } else {
        // 添加间隔符
        context = new PrefixedContext(context, separator);
      }
      int uniqueNumber = context.getUniqueNumber();
      // Issue #709
      if (o instanceof Map.Entry) {
        // 被迭代对象是Map.Entry
        // 将被迭代对象放入上下文环境中
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
        applyIndex(context, mapEntry.getKey(), uniqueNumber);
        applyItem(context, mapEntry.getValue(), uniqueNumber);
      } else {
        // 将被迭代对象放入上下文环境中
        applyIndex(context, i, uniqueNumber);
        applyItem(context, o, uniqueNumber);
      }
      // 根据上下文环境等信息构建内容
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));
      if (first) {
        first = !((PrefixedContext) context).isPrefixApplied();
      }
      context = oldContext;
      i++;
    }
    // 添加close字符串
    applyClose(context);
    // 清理此次操作对环境的影响
    context.getBindings().remove(item);
    context.getBindings().remove(index);
    return true;
  }

  private void applyIndex(DynamicContext context, Object o, int i) {
    if (index != null) {
      context.bind(index, o);
      context.bind(itemizeItem(index, i), o);
    }
  }

  private void applyItem(DynamicContext context, Object o, int i) {
    if (item != null) {
      context.bind(item, o);
      context.bind(itemizeItem(item, i), o);
    }
  }

  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open);
    }
  }

  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }

  private static String itemizeItem(String item, int i) {
    return ITEM_PREFIX + item + "_" + i;
  }

  /**
   * ForEachSqlNode$FilteredDynamicContext 这个内部类，它也是 DynamicContext 的装饰器，
   * 核心功能是：根据前面在 PrefixedContext 中绑定的各种变量，处理 SQL 片段中的“#{}”占位符。
   */
  private static class FilteredDynamicContext extends DynamicContext {
    private final DynamicContext delegate;
    private final int index;
    private final String itemIndex;
    private final String item;

    public FilteredDynamicContext(Configuration configuration,DynamicContext delegate, String itemIndex, String item, int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public void appendSql(String sql) {
      // 创建识别"#{}"的GenericTokenParser解析器
      GenericTokenParser parser = new GenericTokenParser("#{", "}", content -> {
        String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
        if (itemIndex != null && newContent.equals(content)) {
          newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
        }
        return "#{" + newContent + "}";
      });
      // 保存解析后的sql片段
      delegate.appendSql(parser.parse(sql));
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }


  /**
   * PrefixedContext 是 DynamicContext 的一个装饰器，其中记录了一个 prefix
   * 前缀信息（其实就是 <foreach> 标签中的 separator 属性值），
   * 在其 apply() 方法中会先追加 prefix 前缀（迭代第一个元素的时候，prefix 为空字符串
   */
  private class PrefixedContext extends DynamicContext {
    private final DynamicContext delegate;
    private final String prefix;
    private boolean prefixApplied;

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public void appendSql(String sql) {
      if (!prefixApplied && sql != null && sql.trim().length() > 0) {
        delegate.appendSql(prefix);
        prefixApplied = true;
      }
      delegate.appendSql(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
