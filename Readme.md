# 

前言

+ 本文为描述通过`Interceptor`以及`Redis`实现接口访问防刷`Demo`
+ 这里会通过逐步找问题，逐步去完善的形式展示

# 

原理

+ **通过****`ip`地址+`uri`拼接用以作为访问者访问接口区分**
+ **通过在****`Interceptor`中拦截请求，从`Redis`中统计用户访问接口次数从而达到接口防刷目的**
+ 如下图所示

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQGJFfglmTy9b4czEqEen0I7NPgz1YkicbvibtU3pZsofYoiaCOoicxmKdOw/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)

# 

工程

+ 项目地址：interface-brush-protection
+ Apifox地址：Apifox 密码：Lyh3j2Rv
+ **其中，`Interceptor`处代码处理逻辑最为重要**

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQgianmibicb09AtArgyS0jvibA9h8iarD7hBSG0V3qWqqeuosB5cn0QQVHqA/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)


```java

/**
 * @author: Zero
 * @time: 2023/2/14
 * @description: 接口防刷拦截处理
 */
@Slf4j
public class AccessLimintInterceptor  implements HandlerInterceptor {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 多长时间内
     */
    @Value("${interfaceAccess.second}")
    private Long second = 10L;

    /**
     * 访问次数
     */
    @Value("${interfaceAccess.time}")
    private Long time = 3L;

    /**
     * 禁用时长--单位/秒
     */
    @Value("${interfaceAccess.lockTime}")
    private Long lockTime = 60L;

    /**
     * 锁住时的key前缀
     */
    public static final String LOCK_PREFIX = "LOCK";

    /**
     * 统计次数时的key前缀
     */
    public static final String COUNT_PREFIX = "COUNT";


    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String uri = request.getRequestURI();
        String ip = request.getRemoteAddr(); // 这里忽略代理软件方式访问，默认直接访问，也就是获取得到的就是访问者真实ip地址
        String lockKey = LOCK_PREFIX + ip + uri;
        Object isLock = redisTemplate.opsForValue().get(lockKey);
        if(Objects.isNull(isLock)){
            // 还未被禁用
            String countKey = COUNT_PREFIX + ip + uri;
            Object count = redisTemplate.opsForValue().get(countKey);
            if(Objects.isNull(count)){
                // 首次访问
                log.info("首次访问");
                redisTemplate.opsForValue().set(countKey,1,second, TimeUnit.SECONDS);
            }else{
                // 此用户前一点时间就访问过该接口
                if((Integer)count < time){
                    // 放行，访问次数 + 1
                    redisTemplate.opsForValue().increment(countKey);
                }else{
                    log.info("{}禁用访问{}",ip, uri);
                    // 禁用
                    redisTemplate.opsForValue().set(lockKey, 1,lockTime, TimeUnit.SECONDS);
                    // 删除统计
                    redisTemplate.delete(countKey);
                    throw new CommonException(ResultCode.ACCESS_FREQUENT);
                }
            }
        }else{
            // 此用户访问此接口已被禁用
            throw new CommonException(ResultCode.ACCESS_FREQUENT);
        }
        return true;
    }
}
```
  + **在多长时间内访问接口多少次，以及禁用的时长，则是通过与配置文件配合动态设置**

    ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQjUpopTtAnLXKV57xe9jWJ3ibd45R2ziaNOsfiajbTJDnfUMelyke3t1yg/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
  + **当处于禁用时直接抛异常则是通过在****`ControllerAdvice`处统一处理** **（这里代码写的有点丑陋）**

    ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQFl0PH5YhdzqGMhdVD6NrJ3v4khTQA1LO7EGbXrBfGOicYuCqeMrosWw/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
  + 下面是一些测试（可以把项目通过`Git`还原到“【初始化】”状态进行测试）
  + **正常访问时**

    ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQ93ZH9tUPGF7DavvaX2CxQW9kBiadKkZg68I9XN2KRSeWfG35iaykjNxg/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)

    ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQmDkkjKej8qDxcgRNzevMz5uo1fKiagwXXNArYJTh1mgYHM4XXa0FNqQ/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
  + **访问次数过于频繁时**

    ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQoudAWYjyayTgq19WrNBQ3Vm5t00G9g2dlyWwvX3HEQAWAhW8dercsg/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)

    ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQT2c989k36IGUNIJP8BKepibVU4qWCE2QQpWBcqtfFUJ53pyGARlSqgw/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)

# 

自我提问

+ **上述实现就好像就已经达到了我们的接口防刷目的了**
+ **但是，还不够**
+ 为方便后续描述，项目中新增补充`Controller`，如下所示

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQW1lExP17W39Ynyj8iaZzYv5ylBcicCiaH5mGRAQmWiabwacnyvn0KpFpKw/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
+ `PassCotroller`和`RefuseController`
+ 每个`Controller`分别有对应的`get`，`post`，`put`，`delete`类型的方法，其映射路径与方法名称一致
+ 简单来说就是

## **接口自由**

+ 对于上述实现，不知道你们有没有发现一个问题
+ **就是现在我们的接口防刷处理，针对是所有的接口（项目案例中我只是写的接口比较少）**
+ 而在实际开发中，说对于所有的接口都要做防刷处理，感觉上也不太可能（写此文时目前大四，实际工作经验较少，这里不敢肯定）
+ 那么问题有了，该如何解决呢？目前来说想到两个解决方案

### **拦截器映射规则**

+ **项目通过****`Git`还原到"【Interceptor设置映射规则实现接口自由】"版本即可得到此案例实现**

0. 我们都知道**拦截器是可以设置拦截规则的，从而达到拦截处理目的**

   ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQHA6iba8zvC8TxCLHMLcPvE6wmAuNtial0Lkeziaf9soibcwzicIvVpsiarZg/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
1. **这个****`AccessInterfaceInterceptor`是专门用来进行防刷处理的，那么实际上我们可以通过设置它的映射规则去匹配需要进行【接口防刷】的接口即可**
2. 比如说下面的映射配置

   ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQNOlPNklx90sYawkZBgFyfB6j8kowAM4A0NoY3EvZ4Sice5icPeMiaCJaw/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
3. 这样就初步达到了我们的目的，**通过映射规则的配置，只针对那些需要进行【接口防刷】的接口才会进行处理**
4. 至于为啥说是初步呢？下面我就说说目前我想到的使用这种方式进行【接口防刷】的不足点：

   **所有要进行防刷处理的接口统一都是配置成了 x 秒内 y 次访问次数，禁用时长为 z 秒：**

+ 虽然说防刷接口的映射路径基本上定下来后就不会改变
+ 但实际上前后端联调开发项目时，不会有那么严谨的`Api`文档给我们用（这个在实习中倒是碰到过，公司不是很大，开发起来也就不那么严谨，啥都要自己搞，功能能实现就好）
+ 也就是说还是会有那种要修改接口的映射路径需求
+ 当防刷接口数量特别多，后面的接手人员就很痛苦了
+ 就算是项目是自己从0到1实现的，其实有时候项目开发到后面，自己也会忘记自己前面是如何设计的
+ 而使用当前这种方式的话，谁维护谁蛋疼
+ **要知道就是要进行防刷处理的接口，其 x, y, z的值也是并不一定会统一的**
+ **某些防刷接口处理比较消耗性能的，我就把x, y, z设置的紧一点**
+ **而某些防刷接口处理相对来说比较快，我就把x, y, z 设置的松一点**
+ 这没问题吧
+ 但是现在呢？x, y, z值全都一致了，这就不行了
+ 这就是其中一个不足点
+ 当然，其实针对当前这种情况也有解决方案
+ **那就是弄多个拦截器**
+ **每个拦截器的【接口防刷】处理逻辑跟上述一致，并去映射对应要处理的防刷接口**
+ **唯一不同的就是在每个拦截器内部，去修改对应防刷接口需要的x, y, z值**
+ 这样就是感觉会比较麻烦

**防刷接口映射路径修改后维护问题**

+ 虽然说防刷接口的映射路径基本上定下来后就不会改变
+ 但实际上前后端联调开发项目时，不会有那么严谨的`Api`文档给我们用（这个在实习中倒是碰到过，公司不是很大，开发起来也就不那么严谨，啥都要自己搞，功能能实现就好）
+ 也就是说还是会有那种要修改接口的映射路径需求
+ 当防刷接口数量特别多，后面的接手人员就很痛苦了
+ 就算是项目是自己从0到1实现的，其实有时候项目开发到后面，自己也会忘记自己前面是如何设计的
+ 而使用当前这种方式的话，谁维护谁蛋疼

### **自定义注解 + 反射**

+ 咋说呢
+ **就是通过自定义注解中定义 x 秒内 y 次访问次数，禁用时长为 z 秒**
+ **自定义注解 + 在需要进行防刷处理的各个接口方法上**
+ **在拦截器中通过反射获取到各个接口中的x, y, z值即可达到我们想要的接口自由目的**

---

+ 下面做个实现
+ 声明自定义注解

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQHA4qiaglRjRG5qM8GUML9a1lRggxIXV2FkibCTmeHG62kmCpuZPlaRLQ/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
+ `Controlller`中方法中使用

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQuNymWMWJGFMrFEOBlyibCYqAWich2mKXnBuqia5Ivia7NTOSUTV5pbxSfw/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)

  `Interceptor`处逻辑修改（**最重要是通过反射判断此接口是否需要进行防刷处理，以及获取到x, y, z的值**）

```java

/**
 * @author: Zero
 * @time: 2023/2/14
 * @description: 接口防刷拦截处理
 */
@Slf4j
public class AccessLimintInterceptor  implements HandlerInterceptor {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    /**
     * 锁住时的key前缀
     */
    public static final String LOCK_PREFIX = "LOCK";

    /**
     * 统计次数时的key前缀
     */
    public static final String COUNT_PREFIX = "COUNT";


    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        自定义注解 + 反射 实现
        // 判断访问的是否是接口方法
        if(handler instanceof HandlerMethod){
            // 访问的是接口方法，转化为待访问的目标方法对象
            HandlerMethod targetMethod = (HandlerMethod) handler;
            // 取出目标方法中的 AccessLimit 注解
            AccessLimit accessLimit = targetMethod.getMethodAnnotation(AccessLimit.class);
            // 判断此方法接口是否要进行防刷处理（方法上没有对应注解就代表不需要，不需要的话进行放行）
            if(!Objects.isNull(accessLimit)){
                // 需要进行防刷处理，接下来是处理逻辑
                String ip = request.getRemoteAddr();
                String uri = request.getRequestURI();
                String lockKey = LOCK_PREFIX + ip + uri;
                Object isLock = redisTemplate.opsForValue().get(lockKey);
                // 判断此ip用户访问此接口是否已经被禁用
                if (Objects.isNull(isLock)) {
                    // 还未被禁用
                    String countKey = COUNT_PREFIX + ip + uri;
                    Object count = redisTemplate.opsForValue().get(countKey);
                    long second = accessLimit.second();
                    long maxTime = accessLimit.maxTime();

                    if (Objects.isNull(count)) {
                        // 首次访问
                        log.info("首次访问");
                        redisTemplate.opsForValue().set(countKey, 1, second, TimeUnit.SECONDS);
                    } else {
                        // 此用户前一点时间就访问过该接口，且频率没超过设置
                        if ((Integer) count < maxTime) {
                            redisTemplate.opsForValue().increment(countKey);
                        } else {

                            log.info("{}禁用访问{}", ip, uri);
                            long forbiddenTime = accessLimit.forbiddenTime();
                            // 禁用
                            redisTemplate.opsForValue().set(lockKey, 1, forbiddenTime, TimeUnit.SECONDS);
                            // 删除统计--已经禁用了就没必要存在了
                            redisTemplate.delete(countKey);
                            throw new CommonException(ResultCode.ACCESS_FREQUENT);
                        }
                    }
                } else {
                    // 此用户访问此接口已被禁用
                    throw new CommonException(ResultCode.ACCESS_FREQUENT);
                }
            }
        }
        return  true;
    }
}
```
+ 由于不好演示效果，这里就不贴测试结果图片了
+ **项目通过****`Git`还原到"【自定义主键+反射实现接口自由"版本即可得到此案例实现**，后面自己可以针对接口做下测试看看是否如同我所说的那样实现自定义x, y, z 的效果
+ 嗯，**现在看起来，可以针对每个要进行防刷处理的接口进行针对性自定义多长时间内的最大访问次数，以及禁用时长，哪个接口需要，就直接+在那个接口方法出即可**
+ 感觉还不错的样子，现在网上挺多资料也都是这样实现的
+ 但是还是可以有改善的地方
+ 先举一个例子，以我们的`PassController`为例，如下是其实现

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQH5dZead3dREQaamSP4WyxoIVDSsk1gibcbvpw2cskia81gHXj613Tsjg/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
+ 下图是其映射路径关系

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQLey2Ybo0zHO7gFzPIcquNhw0skAbnuljh1ahC2o0Sx9mPqLjBq83fA/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
+ **同一个****`Controller`的所有接口方法映射路径的前缀都包含了`/pass`**
+ **我们在类上通过注解****`@ReqeustMapping`标记映射路径`/pass`，这样所有的接口方法前缀都包含了`/pass`，并且以致于后面要修改映射路径前缀时只需改这一块地方即可**
+ 这也是我们使用`SpringMVC`最常见的用法
+ 那么，我们的自定义注解也可不可以这样做呢？先无中生有个需求
+ **假设****`PassController`中所有接口都是要进行防刷处理的，并且他们的x, y, z值就一样**
+ **如果我们的自定义注解还是只能加载方法上的话，一个一个接口加，那么无疑这是一种很呆的做法**
+ 要改的话，其实也很简单，**首先是修改自定义注解，让其可以作用在类上**

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQutuicJhwU5BicY62Qr9jgJe76hYMiaJFRYYTXbibIOBbRML0gOmMFfzoyQ/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
+ 接着就是修改`AccessLimitInterceptor`的处理逻辑

+ `AccessLimitInterceptor`中代码修改的有点多，主要逻辑如下

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQ6c1vOOqB9qAQ6T8ET1Oysic60RT0xF0VETPYyxjHS6zfD5BOPFTSJXA/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
+ **与之前实现比较，不同点在于x, y, z的值要首先尝试在目标类中获取**
+ **其次，一旦类中标有此注解，即代表此类下所有接口方法都要进行防刷处理**
+ **如果其接口方法同样也标有此注解，根据就近优先原则，以接口方法中的注解标明的值为准**
+ 好了，这样就达到我们想要的效果了

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQTMx8HIwQ8GUVZyreQwSsx2whiaqDrib1ysRQc50T19mFTqFRVofAfX5Q/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
+ **项目通过****`Git`还原到"【自定义注解+反射实现接口自由-版本2.0】"版本即可得到此案例实现**，自己可以测试万一下
+ **这是目前来说比较理想的做法，至于其他做法，暂时没啥了解到**

## **时间逻辑漏洞**

+ 这是我一开始都有留意到的问题
+ 也是一直搞不懂，**就是我们现在的所有做法其实感觉都不是严格意义上的x秒内y次访问次数**
+ **特别注意这个x秒，它是连续，任意的（代表这个x秒时间片段其实是可以发生在任意一个时间轴上）**
+ 我下面尝试表达我的意思，但是我不知道能不能表达清楚
+ 假设我们**固定某个接口5秒内只能访问3次**，以下面例子为例

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQXNdTA2go4GIzC3jMZHSWLyZhuBs70ctGvvMvUTHXiaBiaa3AORic27F7A/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
+ **底下的小圆圈代表此刻请求访问接口**
+ 按照我们之前所有做法的逻辑走

0. 第2秒请求到，为首次访问，`Redis`中统计次数为1（过期时间为5秒）
1. 第7秒，此时有两个动作，一是请求到，二是刚刚第二秒`Redis`存的值现在过期
2. 我们先假设这一刻，请求处理完后，`Redis`存的值才过期
3. 按照这样的逻辑走
4. 第七秒请求到，`Redis`存在对应`key`，且不大于3， 次数+1
5. 接着这个`key`立马过期
6. 再继续往后走，第8秒又当做新的一个起始，就不往下说了，反正就是不会出现禁用的情况

+ 按照上述逻辑走，**实际上也就是说当出现首次访问时，当做这5秒时间片段的起始**
+ 第2秒是，第8秒也是
+ 但是有没有想过，**实际上这个5秒时间片段实际上是可以放置在时间轴上任意区域的**
+ **上述情况我们是根据请求的到来情况人为的把它放在【2-7】，【8-13】上**
+ **而实际上这5秒时间片段是可以放在任意区域的**
+ 那么，这样的话，【7-12】也可以放置
+ **而【7-12】这段时间有4次请求，就达到了我们禁用的条件了**
+ 是不是感觉怪怪的
+ **想过其他做法，但是好像严格意义上真的做不到我所说的那样（至少目前来说想不到）**
+ **之前我们的做法，正常来说也够用，至少说有达到防刷的作用**
+ 后面有机会的话再看看，**不知道我是不是钻牛角尖了**

## **路径参数问题**

+ 假设现在`PassController`中有如下接口方法

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQlyrwwWTDKp7ekPEagNTZbTCJhMRLtyT1URCHV70qRLQgib1waIc79Ag/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
+ 也就是我们**在接口方法中常用的在请求路径中获取参数的套路**
+ 但是使用路径参数的话，就会发生问题
+ **那就是同一个ip地址访问此接口时，我携带的参数值不同**
+ **按照我们之前那种****`前缀`+`ip`+`uri`拼接的形式作为`key`的话，其实是区分不了的**
+ 下图是访问此接口，携带不同参数值时获取的`uri`状况

  ![图片](https://mmbiz.qpic.cn/mmbiz/sXiaukvjR0RDkGy5RrLDBubrl4XJDBbWQRloVaELruj3N0ibsxrOru8BwzpcfsFuee3cicHgBJS6nrjK9RbnxJIJw/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)
+ **这样的话在我们之前拦截器的处理逻辑中，会认为是此****`ip`用户访问的是不同的接口方法,而实际上访问的是同一个接口方法**
+ 也就导致了【接口防刷】失效
+ 接下来就是解决它，目前来说有两种

  **不要使用路径参数**

这算是比较理想的做法，相当于没这个问题

但有一定局限性，有时候接手别的项目，或者自己根本没这个权限说不能使用

****替换****`uri`****

+ **我们获取****`uri`的目的，其实就是为了区别访问接口**
+ **而把****`uri`替换成另一种可以区分访问接口方法的标识即可**
+ **最容易想到的就是通过反射获取到接口方法名称，使用接口方法名称替换成****`uri`即可**
+ **当然，其实不同的****`Controller`中，其接口方法名称也有可能是相同的**
+ **实际上可以再获取接口方法所在类类名，使用类名 + 方法名称替换****`uri`即可**

  **实际解决方案有很多，看个人需求吧**

  **真实ip获取
  **
+ 在之前的代码中，我们获取代码都是通过`request.getRemoteAddr()`获取的
+ 但是后续有了解到，如果说通过代理软件方式访问的话，这样是获取不到来访者的真实`ip`的
+ 至于如何获取，后续我再研究下`http`再说，这里**先提个醒**

# 

总结

+ 说实话，挺有意思的
+ 一开始自己想【接口防刷】的时候，感觉也就是转化成统计下访问次数的问题摆了
+ 后面到网上看别人的写法，又再自己给自己找点问题出来
+ 后面会衍生出来一推东西出来
+ 诸如自定义注解+反射这种实现方式
+ 以前其实对注解 + 反射其实有点不太懂干嘛用的
+ 而从之前的数据报表导出，再到基本权限控制实现，最后到今天的【接口防刷】
+ 一点点来进步去补充自己的知识点
+ 而且，感觉写博客真的是件挺有意义的事情
+ 它会让你去更深入的了解某个点，并且知识是相关联的，探索的过程中会牵扯到其他别的知识点
+ 就像之前的写的【单例模式】实现，一开始就了解到懒汉式，饿汉式
+ 后面深入的话就知道其实会还有序列化/反序列化，反射调用生成实例，对象克隆这几种方式回去破坏单例模式
+ 又是如何解决的，这也是一个进步的点
+ 后续为了保证线程安全问题，牵扯到的`synchronized`，`voliate`关键字
+ 继而又关联到`JVM`，`JUC`，操作系统的东西
+ 哈哈

# 

参考

0. SpringBoot之接口防刷限制
1. SpringBoot如何防止接口恶意刷新和暴力请求
2. 一个注解搞定SpringBoot接口防刷
