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
package org.apache.ibatis.mapping;

/**
 * Represents the content of a mapped statement read from an XML file or an annotation.
 * It creates the SQL that will be passed to the database out of the input parameter received from the user.
 *
 * @author Clinton Begin
 */

/**
 * 一共有四个实现
 * mybatis用SqlSource接口表达解析之后的sql语句（解析过sqlNode标签信息的）
 * 其中的 SQL 语句只是一个中间态，可能包含动态 SQL 标签或占位符等信息，无法直接使用
 *
 * 为了创建MappedStatement
 */
public interface SqlSource {

  /**
   * 获取一个BoundSql对象
   * // 根据Mapper文件或注解描述的SQL语句，以及传入的实参，返回可执行的SQL
   * @param parameterObject 参数对象
   * @return BoundSql对象
   */
  BoundSql getBoundSql(Object parameterObject);

}
