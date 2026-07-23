---
name: alibaba-java-review
description: 基于《阿里巴巴Java开发手册》对 Java 代码进行规范审查。检查命名规约、OOP规约、集合处理、并发处理、控制语句、注释规约、异常日志、MySQL数据库等，输出分级审查报告并给出修复建议。当用户要求代码审查、检查代码规范、阿里规范检查、code review 时触发。
---

# 阿里巴巴 Java 开发手册 — 代码审查

基于《阿里巴巴Java开发手册》（黄山版）对 Java 代码进行规范审查。兼容 Java 17+ 新特性。

## Java 17+ 适配说明

本规范已针对 Java 17 做如下适配：
- **Record 类型**（Java 16+）：不可变数据载体，字段可用基本类型，不遵循传统 POJO 包装类型规则
- **var 类型推断**（Java 10+）：局部变量类型明显时推荐使用
- **不可变集合**（Java 9+）：优先使用 `List.of()/Set.of()/Map.of()` 替代 `Arrays.asList()`
- **switch 表达式**（Java 14+）：使用 `->` 无需 break，编译器保证穷举
- **pattern matching instanceof**（Java 16+）：减少嵌套类型判断
- **文本块**（Java 15+）：多行字符串使用 `"""..."""`

## 审查流程

1. 读取待审查的代码文件（或 git diff）
2. 按以下 8 个维度逐一检查
3. 输出分级审查报告
4. 给出具体修复建议（含代码示例）

## 审查维度与核心规则

### 1. 命名规约

| 规则 | 级别 |
|---|---|
| 类名 UpperCamelCase（DO/BO/DTO/VO/PO 除外） | 强制 |
| 方法名/变量名 lowerCamelCase | 强制 |
| 常量名 UPPER_SNAKE_CASE | 强制 |
| 抽象类以 Abstract/Base 开头，异常类以 Exception 结尾 | 强制 |
| boolean 变量不加 is 前缀（POJO 类中） | 强制 |
| 包名全小写，点分隔符间有且仅有一个自然语义英语单词 | 强制 |
| 避免拼音与英文混合命名，更不允许纯拼音命名 | 强制 |
| 枚举类名带 Enum 后缀，成员名全大写，分号结尾 | 强制 |

### 2. OOP 规约

| 规则 | 级别 |
|---|---|
| 避免通过对象引用访问静态方法/变量 | 强制 |
| 所有覆写方法必须加 @Override | 强制 |
| 相同参数类型和个数，仅返回值不同时，不能方法重载 | 强制 |
| Object.equals 判断对象相等，或用 Objects.equals | 强制 |
| 所有整型包装类对象比较必须用 equals | 强制 |
| BigDecimal 等值比较用 compareTo，不用 equals | 强制 |
| POJO 类属性必须使用包装数据类型（Record 类型除外，因其不可变） | 强制 |
| 构造方法禁止加业务逻辑，初始化放 init 方法 | 推荐 |
| 局部变量类型明显时推荐使用 var（Java 10+） | 推荐 |
| Record 字段可用基本类型（不可变数据载体，Java 16+） | 推荐 |
| 类成员与方法访问顺序：先本类→接口→父类→静态导入 | 参考 |

### 3. 集合处理

| 规则 | 级别 |
|---|---|
| ArrayList.subList 返回原列表视图，不可强转 | 强制 |
| 使用集合转数组必须用 toArray(T[] array) | 强制 |
| 创建不可变集合优先使用 List.of()/Set.of()/Map.of()（Java 9+） | 强制 |
| Arrays.asList 返回不可变列表，增删抛 UnsupportedOperationException（Java 9+ 推荐用 List.of） | 强制 |
| 不要在 foreach 循环里增删元素，用 Iterator 或 removeIf | 强制 |
| JDK7+ Comparator 实现必须满足自反/反对称/传递性，否则 Arrays.sort 抛 IllegalArgumentException | 强制 |
| 集合初始化时指定大小，避免不必要的扩容 | 推荐 |
| Map 遍历不用 keySet 再 get，用 entrySet 或 forEach | 推荐 |
| 利用 Set 元素唯一性做去重时，注意 equals 和 hashCode | 推荐 |

### 4. 并发处理

| 规则 | 级别 |
|---|---|
| 禁止使用 Executors 创建线程池，用 ThreadPoolExecutor | 强制 |
| SimpleDateFormat 线程不安全，禁止定义为 static，或用 DateTimeFormatter | 强制 |
| 必须回收自定义 ThreadLocal 变量，用 try-finally 或 afterExecute | 强制 |
| 高并发时 synchronized 粒度要小，能用 Lock 就不用 synchronized | 推荐 |
| 对多个资源/锁加锁时，保证顺序一致，防止死锁 | 强制 |
| volatile 解决可见性，不解决原子性；i++ 用 AtomicInteger | 强制 |
| 线程池核心参数：corePoolSize / maximumPoolSize / keepAliveTime / workQueue / handler | 强制 |
| 并发修改记录时先加锁或使用 ConcurrentHashMap | 强制 |

### 5. 控制语句

| 规则 | 级别 |
|---|---|
| switch 必须包含 default 分支（switch 表达式用 default ->） | 强制 |
| switch 表达式使用 -> 箭头语法，无需 break（Java 14+） | 推荐 |
| instanceof 模式匹配减少嵌套类型判断（Java 16+） | 推荐 |
| if/else/for/while/do 必须使用大括号 | 强制 |
| 三目运算符注意 NPE：自动拆箱可能空指针 | 强制 |
| if/else 嵌套不超过 3 层，超过用卫语句/策略模式/状态模式重构 | 推荐 |
| 避免取反逻辑运算符（!），改为正向判断 | 推荐 |
| 表达式边界条件：循环边界、数组下标、if 条件等要仔细验证 | 强制 |

### 6. 注释规约

| 规则 | 级别 |
|---|---|
| 类、类属性、类方法必须使用 Javadoc 注释 | 强制 |
| 所有抽象方法（含接口方法）必须有 Javadoc | 强制 |
| 类中已废弃的方法和属性要加 @Deprecated 并说明替代方案 | 强制 |
| 特殊注释标记：TODO(标记人,标记时间,预计处理时间) | 推荐 |
| 注释双斜杠后加空格：`// 这是注释` | 推荐 |
| 方法内部单行注释在被注释语句上方另起一行 | 推荐 |

### 7. 异常与日志

| 规则 | 级别 |
|---|---|
| 禁止 catch(Exception e) 后忽略不处理 | 强制 |
| 异常捕获不能用来做流程控制 | 强制 |
| finally 块必须对资源进行释放，try-with-resources 优先 | 强制 |
| 事务中抛异常需确保事务正确回滚 | 强制 |
| 日志输出用占位符 {}，不用字符串拼接 | 强制 |
| 日志级别：ERROR（系统异常）、WARN（可恢复）、INFO（关键流程）、DEBUG（调试） | 强制 |
| 避免重复打印日志，浪费 IO，使用 additivity=false | 推荐 |
| 生产环境禁止使用 System.out 或 e.printStackTrace | 强制 |
| 多行字符串使用文本块 text block（Java 15+） | 推荐 |

### 8. MySQL 数据库

| 规则 | 级别 |
|---|---|
| 表名、字段名小写，下划线分隔 | 强制 |
| 禁止使用 MySQL 保留字（desc, range, match 等） | 强制 |
| 表必备三字段：id（主键）、create_time、update_time | 推荐 |
| 小数类型用 decimal，禁止 float/double | 强制 |
| 表名不使用复数名词 | 推荐 |
| 索引命名：主键 pk_、唯一索引 uk_、普通索引 idx_ | 推荐 |
| 超过三个表禁止 join，需要 join 时注意驱动表选择 | 强制 |
| where 条件中字段类型与索引一致，避免隐式类型转换 | 强制 |
| 禁止使用 SELECT *，必须指定具体字段 | 强制 |
| in 操作能避免就避免，实在避免不了需评估 in 后面集合元素数量（控制在 1000 以内） | 强制 |

## 审查报告格式

```markdown
# 代码审查报告

## 概要
- 审查文件：{file}
- 问题总数：{total}（强制 {mandatory} / 推荐 {recommended} / 参考 {reference}）

## 问题列表

### [强制] 问题标题
- **位置**：{file}:{line}
- **规则**：{规则描述}
- **问题代码**：
  ```java
  // 原始代码
  ```
- **修复建议**：
  ```java
  // 修复后代码
  ```

### [推荐] 问题标题
...

## 亮点
- 列出代码中做得好的地方（如果有的话）
```

## 审查级别说明

| 级别 | 含义 | 处理要求 |
|---|---|---|
| 【强制】 | 必须遵守，违反可能导致 Bug 或严重问题 | 必须修复 |
| 【推荐】 | 强烈建议遵守，有助于代码质量提升 | 尽量修复 |
| 【参考】 | 可选建议，视具体情况而定 | 酌情处理 |

## 详细规则速查

完整规则列表和更多示例请参考 [rules-reference.md](rules-reference.md)

## 使用示例

**示例 1：审查单个文件**
```
帮我审查 UserService.java 是否符合阿里规范
```

**示例 2：审查 git diff**
```
检查我这次提交的代码有没有违反阿里规范
```

**示例 3：只检查某个维度**
```
检查这段代码的并发处理是否符合阿里规范
```
