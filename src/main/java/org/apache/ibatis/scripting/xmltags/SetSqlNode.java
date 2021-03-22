/**
 *    Copyright 2009-2018 the original author or authors.
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

import java.util.Collections;
import java.util.List;

import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 * TrimSqlNode的子类
 */
public class SetSqlNode extends TrimSqlNode {

  private static final List<String> COMMA = Collections.singletonList(",");

  /**在 SetSqlNode 中将 prefix 设置为“SET”关键字，prefixesToOverride
   * 集合和 suffixesToOverride 集合只包含“，”（逗号）字符串，
   * 这样就实现了删除 SQL 片段开头和结尾多余的逗号，并添加“SET”关键字的效果。
   *
   * @param configuration
   * @param contents
   */
  public SetSqlNode(Configuration configuration,SqlNode contents) {
    super(configuration, contents, "SET", COMMA, null, COMMA);
  }

}
