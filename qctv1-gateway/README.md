# qctv1 网关模块说明

`qctv1-gateway` 是 `qctv1` 项目的统一 API 网关模块，负责把前端的 AI 相关请求收口到一个入口，再按照路径规则转发到 `qctv1-ai-chat` 和 `qctv1-ai-rag`。

## 1. 文档入口

- 路由转发详细说明：[`ROUTING.md`](./ROUTING.md)

## 2. 模块定位

这个模块不承载具体业务逻辑，它主要解决三件事：

- 统一入口：把对外访问路径统一收口到 `/api/ai/**`
- 统一治理：把请求 ID、访问日志、鉴权骨架、CORS、超时、重试、熔断集中放在网关层
- 解耦上下游：前端不需要知道下游服务端口和控制器真实路径，下游服务也不需要为了前端重新改控制器结构

## 3. 为什么使用 Spring Cloud Gateway

本项目选择 Spring Cloud Gateway，主要是因为它天然适合下面这类需求：

- 对外统一入口：所有 AI 接口统一挂在 `/api/ai/**`
- 支持响应式流式接口：适合 `/chat/stream`、`/agent/chat` 这类 SSE 场景
- 治理能力完备：日志、鉴权、CORS、限流、超时、重试、熔断都可以在网关层统一做
- 与 Spring 生态一致：便于和现有 Boot / Cloud 项目保持同一技术栈
- 支持服务发现：可以从本地直连平滑切到 Nacos 注册发现模式

## 4. 当前对外路由

| 对外路径 | 目标服务 | 下游路径 |
| --- | --- | --- |
| `/api/ai/chat/stream` | `qctv1-ai-chat` | `/chat/stream` |
| `/api/ai/chat/**` | `qctv1-ai-chat` | `/chat/**` |
| `/api/ai/image/**` | `qctv1-ai-chat` | `/image/**` |
| `/api/ai/hello/**` | `qctv1-ai-chat` | `/hello/**` |
| `/api/ai/agent/chat` | `qctv1-ai-rag` | `/ai/agent/chat` |
| `/api/ai/agent/**` | `qctv1-ai-rag` | `/ai/agent/**` |
| `/api/ai/code/**` | `qctv1-ai-rag` | `/ai/code/**` |

其中：

- `/api/ai/chat/stream`
- `/api/ai/agent/chat`

这两条流式接口会走专门的 SSE 路由，不启用普通接口的重试策略。

## 5. Spring Cloud Gateway 能力与类映射

下面这张表，既回答“Spring Cloud Gateway 有哪些能力”，也回答“这些能力在当前项目里对应哪些类、该怎么用”。

| 能力 | 核心类 / 工厂 | 在框架里的作用 | 在本项目中的落点与用法 |
| --- | --- | --- | --- |
| 路由定义 | `RouteDefinition` | 路由定义的通用抽象 | 当前项目没有走纯配置式路由，而是用 Java DSL 显式构建，对应概念仍然是路由定义 |
| 路由集合 | `RouteLocator` | 运行时最终可用的路由集合 | `src/main/java/com/qctv1/gateway/config/GatewayRouteConfig.java` |
| 路由来源 | `RouteDefinitionLocator` | 提供路由定义来源 | 当前主要使用 Java DSL，不依赖配置文件自动装配 |
| 服务发现生成路由 | `DiscoveryClientRouteDefinitionLocator` | 根据注册中心自动生成路由 | 当前项目刻意关闭，避免自动路由覆盖掉显式设计的路径改写规则 |
| 请求匹配 | `RoutePredicateHandlerMapping` | 根据谓词挑选要执行的路由 | 运行时按 `/api/ai/**` 路径前缀完成匹配 |
| 路由过滤器 | `GatewayFilter` | 只作用于单条路由的过滤器 | 当前路由上主要用了 `RewritePath`、`Retry`、`CircuitBreaker` |
| 全局过滤器 | `GlobalFilter` | 所有请求共享的过滤器 | 请求 ID、访问日志、鉴权骨架都在全局层完成 |
| 请求上下文 | `ServerWebExchange` | 当前请求与响应的上下文对象 | 各过滤器都通过它读写请求头、响应头、路径、状态码 |
| 路径谓词 | `PathRoutePredicateFactory` | 通过 URL 路径匹配路由 | 当前网关全部以路径前缀为主进行分流 |
| 方法谓词 | `MethodRoutePredicateFactory` | 根据 HTTP 方法匹配 | 当前没有单独启用，但后续可以对上传、删除等接口单独限制方法 |
| 请求头谓词 | `HeaderRoutePredicateFactory` | 根据请求头匹配 | 当前没有使用，后续可按租户、渠道头区分路由 |
| 查询参数谓词 | `QueryRoutePredicateFactory` | 根据查询参数匹配 | 当前没有使用，后续可以按灰度标记做路由试验 |
| 去前缀过滤器 | `StripPrefixGatewayFilterFactory` | 按段移除路径前缀 | 当前没有使用，因为本项目更适合显式 `RewritePath`，路径映射更可读 |
| 路径改写过滤器 | `RewritePathGatewayFilterFactory` | 用正则把外部路径改写成下游路径 | 本项目核心能力，负责把 `/api/ai/...` 翻译成下游控制器实际路径 |
| 限流过滤器 | `RequestRateLimiterGatewayFilterFactory` | 在网关层做限流 | 当前未启用，后续可对聊天和 Agent 接口做用户维度限流 |
| 重试过滤器 | `RetryGatewayFilterFactory` | 针对可重试异常场景自动重试 | 当前普通 GET 接口启用，SSE 路由禁用 |
| 熔断过滤器 | `CircuitBreakerGatewayFilterFactory` | 下游故障时快速失败并保护系统 | 当前普通接口启用，按路由维度配置 |
| Token 透传 | `TokenRelayGatewayFilterFactory` | OAuth2 场景下把上游 token 透传给下游 | 当前未启用，因为鉴权只搭了骨架，没有接入 OAuth2 |
| 负载均衡转发 | `ReactiveLoadBalancerClientFilter` | 处理 `lb://` 目标地址 | nacos 模式下会把 `lb://qctv1-ai-chat` 解析为真实实例 |
| 自定义路由过滤器 | `AbstractGatewayFilterFactory` | 封装可复用的自定义路由过滤器 | 当前还没有单独实现，后续如需租户头透传、签名校验可以扩展 |
| 自定义全局过滤器 | 自定义 `GlobalFilter` | 实现跨全部请求的统一治理 | `RequestIdGlobalFilter`、`AccessLogGlobalFilter`、`AuthenticationGlobalFilter` |

## 6. 当前项目如何使用这些能力

### 6.1 路由装配

路由统一在 `src/main/java/com/qctv1/gateway/config/GatewayRouteConfig.java` 中定义。

这里做了三件关键事情：

- 按路径前缀区分请求应该去 `chat` 还是 `rag`
- 使用 `RewritePath` 把外部路径翻译成下游控制器真实路径
- 把流式接口和普通接口拆成两套过滤链

### 6.2 全局治理

全局过滤器位于：

- `src/main/java/com/qctv1/gateway/filter/RequestIdGlobalFilter.java`
- `src/main/java/com/qctv1/gateway/filter/AccessLogGlobalFilter.java`
- `src/main/java/com/qctv1/gateway/filter/AuthenticationGlobalFilter.java`
- `src/main/java/com/qctv1/gateway/filter/CorsPreflightWebFilter.java`

职责分别是：

- 请求 ID 贯穿请求与响应
- 统一记录访问日志
- 预留鉴权开关、白名单与统一错误结构
- 浏览器 CORS 预检提前处理

### 6.3 配置绑定

网关业务配置绑定类位于：

- `src/main/java/com/qctv1/gateway/config/Qctv1GatewayProperties.java`

这里把下面几类配置集中管理：

- 路由目标地址
- 重试和熔断开关
- 鉴权开关、白名单、模拟 token
- CORS 允许来源、方法和暴露头

## 7. 运行模式

### 7.1 local 模式

默认就是 `local` 模式。

特点：

- 不依赖 Nacos
- 网关直接转发到本机固定端口
- 更适合本地开发和快速联调

当前默认转发到：

- `http://localhost:10082`
- `http://localhost:10081`

### 7.2 nacos 模式

启用 `nacos` profile 后：

- 网关从 Nacos 读取 `qctv1-gateway.yaml`
- 可选读取 `qctv1-common.yaml`
- 下游目标从固定地址切换为 `lb://服务名`

对应服务名：

- `qctv1-gateway`
- `qctv1-ai-chat`
- `qctv1-ai-rag`

## 8. 配置文件说明

| 文件 | 作用 |
| --- | --- |
| `src/main/resources/application.yml` | 公共基础配置，定义默认 profile、端口、监控、路由基础参数 |
| `src/main/resources/application-local.yml` | 本地直连模式，关闭 Nacos，指定固定下游地址 |
| `src/main/resources/application-nacos.yml` | Nacos 模式，开启配置中心和注册发现，使用 `spring.config.import=nacos:` |

## 9. 鉴权说明

当前鉴权只完成了骨架，没有接入真实 JWT / OAuth2。

当前行为：

- `qctv1.gateway.auth.enabled=false`
- 支持白名单
- 缺少 token 返回 `401`
- token 校验失败返回 `403`
- `TokenValidator` 是后续接入真实鉴权逻辑的扩展点

## 10. 常用启动命令

### 10.1 本地模式

```bash
mvn -pl qctv1-gateway spring-boot:run
```

### 10.2 Nacos 模式

```bash
mvn -pl qctv1-gateway spring-boot:run -Dspring-boot.run.profiles=nacos
```

如需显式指定 Nacos 连接参数，可在启动前设置：

```bash
NACOS_SERVER_ADDR=127.0.0.1:8848
NACOS_USERNAME=nacos
NACOS_PASSWORD=nacos
NACOS_NAMESPACE=
NACOS_GROUP=DEFAULT_GROUP
```

## 11. 常用观测接口

网关启动后可以直接查看：

- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/gateway/routes`

这几个接口分别用于：

- 看服务是否启动成功
- 看监控指标是否暴露
- 看当前生效的路由是否符合预期

## 12. 推荐阅读顺序

如果你要快速读懂当前实现，建议按这个顺序看：

1. `README.md`
2. `ROUTING.md`
3. `src/main/java/com/qctv1/gateway/config/GatewayRouteConfig.java`
4. `src/main/java/com/qctv1/gateway/filter/*`
5. `src/main/resources/application*.yml`

## 13. 官方参考

- Spring Cloud Gateway 入门说明：<https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/starter.html>
- Spring Cloud Gateway 工作原理：<https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/how-it-works.html>
- 谓词工厂说明：<https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/request-predicates-factories.html>
- 路由过滤器工厂说明：<https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/gatewayfilter-factories.html>
- 全局过滤器说明：<https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/global-filters.html>
- 服务发现生成路由说明：<https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/the-discoveryclient-route-definition-locator.html>
- Spring Cloud 版本支持矩阵：<https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions>
- Spring Cloud Alibaba 2025.x 版本说明：<https://sca.aliyun.com/en/docs/2025.x/overview/version-explain/>
- Spring Cloud Alibaba Nacos 快速开始：<https://sca.aliyun.com/en/docs/2025.x/user-guide/nacos/quick-start/>
