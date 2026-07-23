# 阿里巴巴 Java 开发手册 — 详细规则速查

> 本规则已适配 Java 17+，新增特性用 `[Java N+]` 标注

## 一、编程规约

### 命名规约详细规则

1. **类名**：UpperCamelCase 风格，但 DO/BO/DTO/VO/AO/PO/UID 等除外
   ```java
   // 正例
   public class UserService {}
   public class UserDTO {}
   
   // 反例
   public class userService {}  // 首字母未大写
   public class user_dto {}     // 使用了下划线
   ```

2. **方法名/参数名/成员变量/局部变量**：lowerCamelCase
   ```java
   // 正例
   private String userName;
   public void getUserById() {}
   
   // 反例
   private String UserName;     // 首字母大写
   public void Getuserbyid() {} // 全大写
   ```

3. **常量名**：UPPER_SNAKE_CASE
   ```java
   // 正例
   public static final int MAX_STOCK_SIZE = 1000;
   
   // 反例
   public static final int maxStockSize = 1000; // 未全大写
   ```

4. **抽象类/异常类命名**
   ```java
   // 正例
   public abstract class AbstractHandler {}
   public class BusinessException extends RuntimeException {}
   ```

5. **boolean 变量不加 is 前缀**（POJO 类中，因为部分框架解析会引起序列化错误）
   ```java
   // 正例
   private Boolean deleted;
   
   // 反例
   private Boolean isDeleted; // 加了 is 前缀
   ```

6. **包名**：全小写，点分隔符间有且仅有一个自然语义的英语单词
   ```java
   // 正例
   package org.hongxi.cloud.sample;
   
   // 反例
   package org.hongxi.cloudSample; // 驼峰命名
   ```

7. **枚举类**：类名带 Enum 后缀，成员名全大写，分号结尾
   ```java
   public enum OrderStatusEnum {
       CREATED, PAID, SHIPPED, COMPLETED;
   }
   ```

### OOP 规约详细规则

1. **equals 使用**
   ```java
   // 正例
   "test".equals(object);
   Objects.equals(a, b);
   
   // 反例
   object.equals("test"); // 可能 NPE
   ```

2. **整型包装类比较**
   ```java
   // 正例
   Integer a = 128;
   Integer b = 128;
   a.equals(b); // true
   
   // 反例
   a == b; // false（超出 -128~127 缓存范围）
   ```

3. **BigDecimal 等值比较**
   ```java
   // 正例
   new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0
   
   // 反例
   new BigDecimal("1.0").equals(new BigDecimal("1.00")) // false（精度不同）
   ```

4. **POJO 类属性使用包装类型**
   ```java
   // 正例
   private Integer age;
   private Long orderId;
   
   // 反例
   private int age;      // 基本类型，默认值 0 无法区分"未赋值"
   private long orderId;  // 默认值 0L
   ```

5. **方法覆写必须加 @Override**
   ```java
   // 正例
   @Override
   public String toString() { ... }
   
   // 反例
   public String toString() { ... } // 缺少 @Override
   ```

6. **Record 类型** `[Java 16+]` — 不可变数据载体，字段可用基本类型
   ```java
   // 正例：Record 字段用基本类型（不可变，不存在 setter 问题）
   public record Point(int x, int y) {}
   public record UserDTO(Long id, String name, boolean active) {}
   
   // 注意：Record 自动实现 equals/hashCode/toString，无需手写
   // 注意：Record 字段默认为 private final，不可修改
   ```

7. **var 类型推断** `[Java 10+]` — 局部变量类型明显时推荐
   ```java
   // 正例：类型明显时用 var
   var list = new ArrayList<String>();
   var stream = list.stream();
   
   // 反例：类型不明显时不要用 var
   var result = process(data); // 看不出返回类型
   ```

### 集合处理详细规则

1. **subList 陷阱**
   ```java
   List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
   List<String> sub = list.subList(0, 2);
   // sub 是原 list 的视图，原 list 增删会导致 sub 遍历/增删抛 ConcurrentModificationException
   ```

2. **集合转数组**
   ```java
   // 正例
   String[] arr = list.toArray(new String[0]);
   
   // 反例
   String[] arr = (String[]) list.toArray(); // ClassCastException
   ```

3. **不可变集合** `[Java 9+]` — 优先使用 List.of()/Set.of()/Map.of()
   ```java
   // 正例（Java 9+）
   List<String> list = List.of("a", "b", "c");
   Set<Integer> set = Set.of(1, 2, 3);
   Map<String, Integer> map = Map.of("a", 1, "b", 2);
   
   // 正例（Java 10+）
   var list = new ArrayList<>(List.of("a", "b", "c")); // 可变副本
   
   // 反例
   List<String> list = Arrays.asList("a", "b");
   list.add("c"); // UnsupportedOperationException
   ```

3b. **Stream.toList()** `[Java 16+]` — 返回不可变 List
   ```java
   // 正例
   List<String> result = list.stream()
       .filter(s -> s.length() > 3)
       .toList(); // 返回不可变 List
   
   // 如果需要可变 List，仍用 collect
   List<String> mutable = list.stream()
       .filter(s -> s.length() > 3)
       .collect(Collectors.toCollection(ArrayList::new));
   ```

4. **foreach 中增删元素**
   ```java
   // 正例
   list.removeIf(item -> "delete".equals(item));
   
   // 或使用 Iterator
   Iterator<String> it = list.iterator();
   while (it.hasNext()) {
       String item = it.next();
       if ("delete".equals(item)) {
           it.remove();
       }
   }
   
   // 反例
   for (String item : list) {
       if ("delete".equals(item)) {
           list.remove(item); // ConcurrentModificationException
       }
   }
   ```

5. **Comparator 必须满足三个性质**
   ```java
   // 反例（不满足传递性，JDK7+ 会抛 IllegalArgumentException）
   list.sort((a, b) -> {
       if (a > b) return 1;
       return -1; // 缺少 a == b 返回 0 的情况
   });
   
   // 正例
   list.sort(Comparator.comparingInt(Integer::intValue));
   ```

### 并发处理详细规则

1. **禁止 Executors 创建线程池**
   ```java
   // 正例
   new ThreadPoolExecutor(
       4, 8, 60L, TimeUnit.SECONDS,
       new LinkedBlockingQueue<>(1000),
       new ThreadFactoryBuilder().setNameFormat("biz-pool-%d").build(),
       new ThreadPoolExecutor.CallerRunsPolicy()
   );
   
   // 反例
   Executors.newFixedThreadPool(10);      // 无界队列，可能 OOM
   Executors.newCachedThreadPool();       // 允许创建无限线程，可能 OOM
   Executors.newSingleThreadExecutor();   // 无界队列
   ```

2. **SimpleDateFormat 线程不安全**
   ```java
   // 正例
   private static final DateTimeFormatter FMT =
       DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
   
   // 反例
   private static final SimpleDateFormat SDF =
       new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // 多线程共用会出错
   ```

3. **ThreadLocal 必须回收**
   ```java
   // 正例
   ThreadLocal<User> userHolder = new ThreadLocal<>();
   try {
       userHolder.set(currentUser);
       // 业务逻辑
   } finally {
       userHolder.remove(); // 必须回收
   }
   ```

4. **volatile 与原子性**
   ```java
   // 反例
   private volatile int count;
   count++; // 非原子操作，多线程不安全
   
   // 正例
   private AtomicInteger count = new AtomicInteger(0);
   count.incrementAndGet(); // 原子操作
   ```

### 控制语句详细规则

1. **switch 必须有 default**（传统 switch 语句）
   ```java
   // 正例
   switch (status) {
       case 1: handle1(); break;
       case 2: handle2(); break;
       default: throw new IllegalArgumentException("Unknown status: " + status);
   }
   ```

1b. **switch 表达式** `[Java 14+]` — 使用箭头语法，无需 break
   ```java
   // 正例：switch 表达式，编译器保证穷举（不需要 default，但推荐加）
   String result = switch (status) {
       case 1 -> "created";
       case 2 -> "paid";
       case 3 -> "shipped";
       default -> "unknown";
   };
   
   // 正例：switch 表达式 + yield（多行逻辑）
   int len = switch (s) {
       case null, "" -> 0;
       case String v when v.length() > 100 -> 100;
       case String v -> v.length();
   };
   ```

1c. **pattern matching instanceof** `[Java 16+]` — 减少嵌套类型判断
   ```java
   // 正例（Java 16+）
   if (obj instanceof String s) {
       System.out.println(s.length());
   }
   
   // 反例（传统写法）
   if (obj instanceof String) {
       String s = (String) obj;
       System.out.println(s.length());
   }
   ```

2. **三目运算符 NPE**
   ```java
   // 反例
   Integer a = null;
   Integer b = (a > 0) ? a : -1; // a 为 null 时自动拆箱抛 NPE
   
   // 正例
   Integer b = (a != null && a > 0) ? a : -1;
   ```

3. **if/else 嵌套不超过 3 层**
   ```java
   // 反例
   if (condition1) {
       if (condition2) {
           if (condition3) {
               // 业务逻辑
           }
       }
   }
   
   // 正例（卫语句）
   if (!condition1) return;
   if (!condition2) return;
   if (!condition3) return;
   // 业务逻辑
   ```

### 注释规约详细规则

1. **类/方法 Javadoc**
   ```java
   /**
    * 用户服务类
    *
    * @author hongxi
    * @date 2026-07-04
    */
   public class UserService {
   
       /**
        * 根据 ID 获取用户
        *
        * @param id 用户 ID
        * @return 用户对象，不存在返回 null
        */
       public User getUserById(Long id) { ... }
   }
   ```

2. **废弃方法标记**
   ```java
   /**
    * @deprecated 请使用 {@link #getUsersV2()} 替代
    */
   @Deprecated
   public List<User> getUsers() { ... }
   ```

### 异常与日志详细规则

1. **禁止吞掉异常**
   ```java
   // 反例
   try { ... } catch (Exception e) { }  // 什么都不做
   try { ... } catch (Exception e) { e.printStackTrace(); } // 仅打印
   
   // 正例
   try { ... } catch (Exception e) {
       log.error("操作失败, param={}", param, e);
       throw new BusinessException("操作失败", e);
   }
   ```

2. **try-with-resources**
   ```java
   // 正例
   try (InputStream is = new FileInputStream(file)) {
       // 使用资源
   }
   
   // 反例
   InputStream is = null;
   try {
       is = new FileInputStream(file);
   } finally {
       if (is != null) {
           try { is.close(); } catch (IOException e) { } // 冗长且易错
       }
   }
   ```

3. **日志占位符**
   ```java
   // 正例
   log.info("User {} login from {}", userId, ip);
   
   // 反例
   log.info("User " + userId + " login from " + ip); // 字符串拼接，影响性能
   // 注意：Java 9+ 的 invokedynamic 已优化字符串拼接，但日志仍应用占位符以避免无效拼接
   ```

3b. **文本块** `[Java 15+]` — 多行字符串使用 text block
   ```java
   // 正例（Java 15+）
   String json = """
           {
               "name": "test",
               "age": 25
           }
           """;
   
   String sql = """
           SELECT id, name, age
           FROM user
           WHERE status = ?
           """;
   
   // 反例
   String json = "{\n" +
       "    \"name\": \"test\",\n" +
       "    \"age\": 25\n" +
       "}"; // 可读性差，易出错
   ```

4. **日志级别使用**
   ```java
   // ERROR：系统级异常，需要人工介入
   log.error("数据库连接失败", e);
   
   // WARN：可恢复异常，不影响核心功能
   log.warn("缓存未命中，降级查库, key={}", key);
   
   // INFO：关键业务节点
   log.info("订单创建成功, orderId={}", orderId);
   
   // DEBUG：调试信息
   log.debug("查询参数: {}", params);
   ```

### MySQL 数据库详细规则

1. **表设计规范**
   ```sql
   -- 正例
   CREATE TABLE `user_order` (
       `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
       `user_id` bigint(20) NOT NULL COMMENT '用户ID',
       `amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '金额',
       `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
       `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
       PRIMARY KEY (`id`),
       KEY `idx_user_id` (`user_id`)
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订单表';
   ```

2. **禁止 SELECT ***
   ```sql
   -- 正例
   SELECT id, user_id, amount, create_time FROM user_order WHERE user_id = ?;
   
   -- 反例
   SELECT * FROM user_order WHERE user_id = ?;
   ```

3. **小数类型用 decimal**
   ```sql
   -- 正例
   `price` decimal(10,2) NOT NULL
   
   -- 反例
   `price` float NOT NULL  -- 精度丢失
   `price` double NOT NULL -- 精度丢失
   ```

4. **索引命名规范**
   ```sql
   -- 主键：pk_
   PRIMARY KEY (`id`)
   
   -- 唯一索引：uk_
   UNIQUE KEY `uk_user_name` (`user_name`)
   
   -- 普通索引：idx_
   KEY `idx_create_time` (`create_time`)
   ```

5. **避免隐式类型转换**
   ```sql
   -- 反例（phone 是 varchar，传入数字导致索引失效）
   SELECT * FROM user WHERE phone = 13800138000;
   
   -- 正例
   SELECT * FROM user WHERE phone = '13800138000';
   ```

## 二、安全规约

1. 隶属于用户请求的参数必须做合法性校验（空值、长度、范围）
2. 用户输入的 SQL 参数必须使用预编译（PreparedStatement），禁止拼接 SQL
3. 用户敏感数据禁止直接展示，必须脱敏（手机号、身份证、银行卡等）
4. 表单、AJAX 提交必须执行 CSRF 安全验证
5. 禁止向 HTML 页面输出未经安全过滤的用户输入（防 XSS）

## 三、工程结构

1. **推荐包结构**
   ```
   com.company.project
   ├── controller/     # 入口层
   ├── service/        # 业务逻辑层
   │   └── impl/       # 实现类
   ├── manager/        # 通用业务处理层（可选）
   ├── dao/            # 数据访问层
   ├── model/
   │   ├── entity/     # DO（与数据库表对应）
   │   ├── dto/        # DTO（传输对象）
   │   ├── vo/         # VO（视图对象）
   │   └── query/      # 查询参数对象
   ├── config/         # 配置类
   ├── common/         # 通用工具/常量
   │   ├── constant/   # 常量
   │   ├── enums/      # 枚举
   │   ├── exception/  # 自定义异常
   │   └── utils/      # 工具类
   └── interceptor/    # 拦截器
   ```

2. **分层领域模型规约**
   - **DO**（Data Object）：与数据库表结构一一对应
   - **DTO**（Data Transfer Object）：服务间传输的数据对象
   - **VO**（View Object）：返回给前端的数据对象
   - **Query**：接收前端查询参数
