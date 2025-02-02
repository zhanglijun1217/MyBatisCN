/**
 *    Copyright 2009-2015 the original author or authors.
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

/**
 * @author Clinton Begin
 * 在我们写动态的SQL语句时，<if></if>  <where></where> 这些就是sqlNode
 * SqlNode的实现使用了组合模式
 */
public interface SqlNode {

  /**
   * 完成该节点自身的解析
   * apply方法会根据用户传入的实参，解析sqlNode所表示的动态内容，并追加到DynamicContext.sqlBuilder中
   * 当全部解析完毕，可以从上下文中得到完整、可用的SQL语句了。
   * @param context 上下文环境，节点自身的解析结果将合并到该上下文环境中
   * @return 解析是否成功
   */
  boolean apply(DynamicContext context);
}
