# Spring Filter와 Multipart Resolver

## 1. 문제 발생
최근 팀에서 진행하는 프로젝트 중 Cloud 환경에 배포해야 할 일이 생겼다.  
배포 대상인 기존 솔루션들은 Sticky Session을 기반으로 IDC에서 운용되고 있어 클라우드 환경에서 Auto scaling을 위해서는  
Session clustering이 필요했고 Redis를 이용한 Spring Session을 통해 처리하고 있었다.

IDC와 Cloud에 대한 처리는 Spring의 profile을 통해 Configurable하게 설정되어있었다.

대부분 테스트가 끝나고 Service를 위한 서버에 배포를 하였으나 파일 업로드가 되지 않는 문제가 발생하였는데  
특이하게 IDC에서는 정상 동작하였으나 Cloud에 올라간 솔루션 중 하나에서 문제가 발생하고 있었다.  
원인 확인을 이해 아래와 같은 사항을 확인해 보았다
> Check List
> * 모든 솔루션에 Spring Session Redis가 적용 되었는가?              (Yes)
> * Request 자체가 차단이 되었는가?   (No)
> * MultipartFile과 함게 보낸 Parameter는 정상적으로 전송 되었는가? (Yes)

CommonsMultipartResolver를 상속한 Bean을 통해 Log를 찍어본 결과 아래 코드에서처럼  
parseRequest를 통해 가져온 fileItems의 Size가 0으로 나타났다

```java
class CommonsMultipartResolver{    
    protected MultipartParsingResult parseRequest(HttpServletRequest request) throws MultipartException {
        String encoding = determineEncoding(request);
        FileUpload fileUpload = prepareFileUpload(encoding);
        try {
            List<FileItem> fileItems = ((ServletFileUpload) fileUpload).parseRequest(request);
            //fileItems의 size가 항상 0임.
            return parseFileItems(fileItems, encoding);
        }
        catch (FileUploadBase.SizeLimitExceededException ex) {
            throw new MaxUploadSizeExceededException(fileUpload.getSizeMax(), ex);
        }
        catch (FileUploadBase.FileSizeLimitExceededException ex) {
            throw new MaxUploadSizeExceededException(fileUpload.getFileSizeMax(), ex);
        }
        catch (FileUploadException ex) {
            throw new MultipartException("Failed to parse multipart servlet request", ex);
        }
    }
}
```

문제가 발생하지 않은 솔루션에선 모든 Request를 Form의 Submit을 통해 전송하였고  
문제가 발생한 솔루션에선 모든 Request를 jQuery를 통해 전송하고있었기에  
혹시 Client쪽의 문제인가 싶어 Form submit을 통해 전송하였으나 마찬가지였다.  

서버측 환경을 Local에 구성하여 Debugging을 해보고 싶었지만 다른 업무가 밀려있는 상태였고  
이미 프로젝트를 주도하던 팀에서는 Infra적 문제인 것으로 가정하여  
담당자분에게 확인을 요청한 상태였기에 흐지부지 끝나는 듯 했다.

## 2. 원인 추측 및 해결
업무를 하면서도 틈틈히 아래와 같은 키워드들로 검색을 해보았지만 별 소득이 없었다.  
> (XXX) Cloud file upload not working.    
> Multipart File null

단서가 될 만한 것은 아래 페이지에서였다.  
<a href="https://commons.apache.org/proper/commons-fileupload/faq.html">Apache Commons File Upload</a>  

CommonsMultipartResolver에서 fileItems가 비어있는 것을 문제삼아 검색하여 위 페이지에서 아래와 같은 문구를 확인했다.  
> Why is parseRequest() returning no items?  
> 
> This most commonly happens when the request has already been parsed, or processed in some other way.   
> Since the input stream has aleady been consumed by that earlier process,   
> it is no longer available for parsing by Commons FileUpload.

즉, CommonsMulitpartResolver가 parseRequet를 통해 MulitpartFile을 파싱하기 전에  
어디선가 request를 파싱하였다면 fileItems만 비어있을 수 있다는 것이다.  

DispatcherServlet의 doDispatch 메소드 상단부에서 MultipartResolver의 구현체에 의한 파싱이 진행되기 때문에  
Filter쪽에 문제가 있지 않을까 싶었다.
  
특히나 Sprig Security 외에도 Spring Session 설정으로 인해 많은 Filter가 추가되었기에 더욱 의심이 갔다.  
(문제가 없던 솔루션에도 동일한 Filter가 설정 되어있기는 했다.)

```java
class DispatcherServlet {
    protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
        HttpServletRequest processedRequest = request;
        HandlerExecutionChain mappedHandler = null;
        boolean multipartRequestParsed = false;

        WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
        ModelAndView mv = null;
        Exception dispatchException = null;

        processedRequest = checkMultipart(request);             
        multipartRequestParsed = (processedRequest != request);
        //...
    }
    
    protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {  
        if (this.multipartResolver != null && this.multipartResolver.isMultipart(request)) {
            if (WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class) != null) {
                if (request.getDispatcherType().equals(DispatcherType.REQUEST)) {
                    logger.trace("Request already resolved to MultipartHttpServletRequest, e.g. by MultipartFilter");
                }
            }
            else if (hasMultipartException(request) ) {
                logger.debug("Multipart resolution previously failed for current request - " +
                "skipping re-resolution for undisturbed error rendering");
            }
            else {
                try {
                    return this.multipartResolver.resolveMultipart(request);
                }
                catch (MultipartException ex) {
                    if (request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) != null) {
                        logger.debug("Multipart resolution failed for error dispatch", ex);
                        // Keep processing error dispatch with regular request handle below
                    }
                    else {
                        throw ex;
                    }
                }
            }
        }
        // If not returned before: return original request.
        return request;
    }
}
```
그래서 당장의 해결책으로 생각해 낸 것은 MultipartFilter이었다.  
예전에 얼핏 이름만 들었고 어떤 것을 처리하는 것인지 몰랐는데 혹시나 해서  
JavaDoc을 확인해보았다.  
> Servlet Filter that resolves multipart requests via a {@link MultipartResolver}.
> in the root web application context.
>
> Looks up the MultipartResolver in Spring's root web application context.    
> Supports a "multipartResolverBeanName" filter init-param in {@code web.xml};    
> the default bean name is "filterMultipartResolver".  
> 
> If no MultipartResolver bean is found, this filter falls back to a default  
> MultipartResolver: {@link StandardServletMultipartResolver} for Servlet 3.0,  
> based on a multipart-config section in {@code web.xml}.  
> Note however that at present the Servlet specification only defines how to  
> enable multipart configuration on a Servlet and as a result multipart request  
> processing is likely not possible in a Filter unless the Servlet container  
> provides a workaround such as Tomcat's "allowCasualMultipartParsing" property.  
>
> MultipartResolver lookup is customizable: Override this filter's  
> {@code lookupMultipartResolver} method to use a custom MultipartResolver  
> instance, for example if not using a Spring web application context.  
> Note that the lookup method should not create a new MultipartResolver instance  
> for each call but rather return a reference to a pre-built instance.  
>
> Note: This filter is an <b>alternative</b> to using DispatcherServlet's  
> MultipartResolver support, for example for web applications with custom web views  
> which do not use Spring's web MVC, or for custom filters applied before a Spring MVC  
> DispatcherServlet (e.g. {@link org.springframework.web.filter.HiddenHttpMethodFilter}).  
> In any case, this filter should not be combined with servlet-specific multipart resolution.  

요약하면 Servlet Filter에서 MultipartResolver를 통해 MulitpartRequest를 처리하는 역할을 하며  
기본적으로 look-up 하는 MultipartResolver bean의 이름은 filterMultipartResolver이다.  

또한 DispatcherServlet에서의 MultipartResolver support에 대한 대체라는 것.  
즉, MultipartFilter는  HiddenhttpMethodfilter와 같이 Spring MVC의 DispatcherServlet 이전에 적용되는 Custom Filter  
사용시 DispatcherServlet에서 처리하는 MultipartResolver를 통한 작업을 대신하는 역할이라는 것이다.  

JavaDoc을 보니 확신이 생겼다.  
다음날 출근하여 바로 Local에 Redis를 설치하고 테스트를 해보았다.  
먼저 Cloud config로 띄운 후 파일 업로드는 아래와 같이 실패했다.   
이로써 Infra적 문제가 아니라는 가설을 입증하였다.  

다음으로 MultipartFilter를 web.xm에 적용하였다.  
Filter의 위치를 고려했을 때 Encoding이슈가 없도록 EncodingFilter 다음에 위치해야했고  
어떤 Filter가 request에 대한 parsing을 사전처리하는 지 확인이 불가능했으므로 다른 필터보다 앞서서 위치시켰다.
```xml
<filter>
    <filter-name>characterSetEncodingFilter</filter-name>
    <filter-class>org.springframework.web.filter.CharacterEncodingFilter</filter-class>
    <init-param>
      <param-name>encoding</param-name>
      <param-value>UTF-8</param-value>
    </init-param>
    <init-param>
      <param-name>forceEncoding</param-name>
      <param-value>true</param-value>
    </init-param>
</filter>
<filter-mapping>
    <filter-name>characterSetEncodingFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>

<filter>
    <filter-name>multipartFilter</filter-name>
    <filter-class>org.springframework.web.multipart.support.MultipartFilter</filter-class>
</filter>
<filter-mapping>
    <filter-name>multipartFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

그후 기존 Servlet Context에 설정 된 multipartResolver의 이름을 filterMultipartResolver로 바꾸어주었다.
```xml
<!-- xxx-servlet.xml -->
<bean id="filterMultipartResolver" class="org.springframework.web.multipart.commons.CommonsMultipartResolver"/>
```

Controller에서 Debugging을 건 후 확인해보니 정상적으로 파일업로드가 되엇다.  
그런데 한 가지 이상한 것이 업로드 된 MultipartFile이 StandardMultipartfile이었다.  
즉 filterMultipartResolver로 설정한 CommonsMultipartResolver에 의해 parsing 된 것이 아닌  
StandardMultipartResolver에 의해 parsing 된 것이었다.  

<table border="2" style="width: fit-content"><tr><td>
<img src="https://raw.githubusercontent.com/dlxotn216/image/master/spring-multipartresolver/StandardMultipartfileInController.png" style="border: solid 5px black;" />
</td></tr></table>

확인을 위해 MultipartFilter의 아래 코드에 디버깅을 해보앗다.  
```java
class MultipartFilter {
    protected MultipartResolver lookupMultipartResolver() {
        WebApplicationContext wac 
                = WebApplicationContextUtils.getWebApplicationContext(getServletContext());
        String beanName = getMultipartResolverBeanName();
        if (wac != null && wac.containsBean(beanName)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Using MultipartResolver '" + beanName + "' for MultipartFilter");
        }
        return wac.getBean(beanName, MultipartResolver.class);
        }
        else {
            return this.defaultMultipartResolver;
        }
    }
}
```
<table border="2" style="width: fit-content"><tr><td>
<img src="https://raw.githubusercontent.com/dlxotn216/image/master/spring-multipartresolver/root-applicationcontext.png" style="border: solid 5px black;" />
</td></tr></table>
확인해보면 WebApplicationContextUtils를 통해 얻은 WebApplicationContext는 RootContext이었다.  
따라서 Root application context에는 filterMultipartResolver이름의 Bean이 없기 때문에  
defaultMultipartResolver를 리턴하였고 이것은 StandardMultipartResolver이었다.  

업로드 기능 자체에는 문제가 없었지만 지난 MultipartResolver 이슈에서처럼 Encoding 관련 이슈가 있기 때문에  
CommonsMultipartResolver의 사용이 필요했다.  
따라서 아래와 같이 filterMultipartResolver Bean의 설정을 Root application context로 이동하였다.
```xml
<!-- application-context.xml -->
<bean id="filterMultipartResolver" class="org.springframework.web.multipart.commons.CommonsMultipartResolver"/>
```

그후 테스트 결과를 보면 정상적으로 파일 업로드가 되었고 업로드 된 파일은  CommonsMultipartFile 타입의 객체이었다.  

## 3. 원인 파악

계속 검색을 하던 중 아래 Spring-session 프로젝트 Github의 이슈를 찾았다.  
<a href="https://github.com/spring-projects/spring-session/issues/649">request.getInputStream is empty#649</a>
<table border="2" style="width: fit-content"><tr><td>
<img src="https://raw.githubusercontent.com/dlxotn216/image/master/spring-multipartresolver/spring-session-request-param-null-github-issue.png" style="border: solid 5px black;"  />
</td></tr></table>
CookieHttpSessionStrategy를 사용할 경우 내부에서 getParameter를 호출 할 때 Requets가 먼저 parsing 된다는 것이다.  
 
혹시 프로젝트에서 CookieHttpSessionStrategy를 사용하는지 혹은 다른 Filter에서 getParamter를 호출하는 지 확인하였지만  
모두 허사였다. 한 가지 authenticationStrategy에서는 getParamter를 사용하였는데 이 구현체는 Login을 할 경우  
호출되는 것이기때문에 별 상관 없었다.  

프로젝트에는 CookieHttpSessionStrategy가 아닌 HeaderHttpSessionStrategy가 설정되어있었다.  
하지만 문제가 발생한 솔루션엔 HeaderHttpSessionStrategy가 존재했지만  
문제가 발생하지 않은 솔루션엔 HeaderHttpSessionStrategy 설정이 없었기에 이부분이 의심스러웠다..  
```xml
<!-- 문제가 발생한 솔루션의 Configuration -->
<bean class="org.springframework.session.data.redis.RedisOperationsSessionRepository" name="sessionRepository" >
    <constructor-arg name="redisConnectionFactory" ref="jedisConnFactory" />
</bean>

<bean class="org.springframework.session.web.http.HeaderHttpSessionStrategy" name="sessionStrategy" />

<bean name="springSessionRepositoryFilter" class="org.springframework.session.web.http.SessionRepositoryFilter">
    <constructor-arg name="sessionRepository" ref="sessionRepository" />
</bean>

<bean class="org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration" >
    <property name="httpSessionStrategy" ref="sessionStrategy"/>
</bean>
```

JavaDoc을 확인하면 아래와 같은데  
인증이 성공하면 Response 중 x-auth-token Header에 Session ID를 담아 전송할 것이며  
Client에서는 이후 모든 요청에 x-auth-token Header에 Session ID 값을 담아 전송해야 한다는 것이다.  

> A HttpSessionStrategy that uses a header to obtain the session from.  
> Specifically, this implementation will allow specifying a header name using setHeaderName(String).   
> The default is "x-auth-token". When a session is created, 
> the HTTP response will have a response header of the specified name and the value of the session id.   
> For example:   
> HTTP/1.1 200 OK  
> x-auth-token: f81d4fae-7dec-11d0-a765-00a0c91e6bf6  
>    
> The client should now include the session in each request by specifying the same header in their request.   
> For example:  
> GET /messages/ HTTP/1.1  
> Host: example.com  
> x-auth-token: f81d4fae-7dec-11d0-a765-00a0c91e6bf6  
>    
> When the session is invalidated, the server will send an HTTP response   
> that has the header name and a blank value. For example:  
> HTTP/1.1 200 OK  
> x-auth-token:  

하지만 프로젝트 어디서도 Login 후 x-auth-token 헤더에 대한 처리도 없을 뿐 더러  
실제 Login 인증 완료 후 API의 Response를 봐도 x-auth-token 헤더 자체가 없었다.  
<table border="2" style="width: fit-content"><tr><td>
<img src="https://raw.githubusercontent.com/dlxotn216/image/master/spring-multipartresolver/LoginSuccessRequest_COOKIE.png" style="border: solid 5px black;"  />
</td></tr></table>

이 부분을 굉장히 이상하게 생각하였고 혹시 x-auth-token을 request에 담아 보내지 않았기에  
매번 인증 과정이 거쳐졌고 그래서 request.getparameter를 호출하는 authenticationStrategy 로직을 타는 것이  
문제의 원인이 아닐까 하여 디버깅을 해보았지만 역시 아니었다.  

문제 발견을 위해 가장 원초적인 방법으로 Encoding Filter로 부터 모든 디버깅 과정을 따라갔다.  
그 중 우연히발견 한 것인데 아래의 코드에서 보면 HttpSessionStrategy로 주입 된 구현체가   
CookieHttpSessionStrategy인 것을 볼 수 있다.  
<table border="2" style="width: fit-content"><tr><td>
<img src="https://raw.githubusercontent.com/dlxotn216/image/master/spring-multipartresolver/CookieHttpSessionStrategy.png" style="border: solid 5px black;" />
</td></tr></table>

분명 아래에서 RedisHttpSessionConfiguration 내에 httpSessionStrategy를 주입하고 있는데 왜 CookieHttpSessionStrategy가
주입되어있을까?
```xml
<bean class="org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration" >
 <property name="httpSessionStrategy" ref="sessionStrategy"/>
</bean>
```

먼저 SessionRepositoryFilter의 configuration은 아래와 같이 생성자 주입으로 sessionRepository를 주입한다.
```xml
<bean name="springSessionRepositoryFilter" class="org.springframework.session.web.http.SessionRepositoryFilter">
    <constructor-arg name="sessionRepository" ref="sessionRepository" />
</bean>
```
아래의 코드에서 보면 생성자 주입 외에 httpSessionStrategy에 대한 Setter가 존재하며  
Default httpSessionStrategy는 CookieHttpSessionStrategy인 것을 확인할 수 있다.
```java
@Order(SessionRepositoryFilter.DEFAULT_ORDER)
public class SessionRepositoryFilter<S extends ExpiringSession> extends OncePerRequestFilter {
    public static final String SESSION_REPOSITORY_ATTR = SessionRepository.class.getName();

    public static final int DEFAULT_ORDER = Integer.MIN_VALUE + 50;
    
    private final SessionRepository<S> sessionRepository;

    private ServletContext servletContext;

    //Default httpSessionStrategy -> CookieHttpSessionStrategy
    private MultiHttpSessionStrategy httpSessionStrategy = new CookieHttpSessionStrategy();
    
    public SessionRepositoryFilter(SessionRepository<S> sessionRepository) {
        if(sessionRepository == null) {
            throw new IllegalArgumentException("SessionRepository cannot be null");
        }
        this.sessionRepository = sessionRepository;
    }
    
    public void setHttpSessionStrategy(HttpSessionStrategy httpSessionStrategy) {
        if(sessionRepository == null) {
            throw new IllegalArgumentException("httpSessionIdStrategy cannot be null");
    }
	this.httpSessionStrategy = new MultiHttpSessionStrategyAdapter(httpSessionStrategy);
    }
}
```

생성자 주입과 Setter를 이용한 주입은 동시에 처리될 수 없기 때문에 난감한 상황이다.  
설정을 더 둘러보던 중 RedisHttpSessionConfiguration 내부를 확인하여 해결법을 찾았다.   
먼저 xml configuration을 보면 아래와 같다. 
```xml
<bean class="org.springframework.session.web.http.HeaderHttpSessionStrategy" name="sessionStrategy" />

<bean class="org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration" >
   <property name="httpSessionStrategy" ref="sessionStrategy"/>
</bean>
```

내부 코드를 확인해보면 아래와 같이 SessionRepositoryFilter에 대한 Bean 정의가 있다.  
RedisHttpSessionConfiguration Bean에 httpSessionStrategy를 Setter로 주입하고 있으므로  
아래에서 생성 된 Bean은 HttpSessionStrategy를 주입받은 SessionRepositoryFilter를 만들어 낼 것이다. 

또한 내부에서 RedisOperationSessionRepository bean도 생성하고 있다.
```java
@org.springframework.context.annotation.Configuration
public class RedisHttpSessionConfiguration {
    //.....
    //다른 Bean Configuration
    @Bean
    public RedisOperationsSessionRepository sessionRepository(RedisTemplate<String, ExpiringSession> sessionRedisTemplate) {
        RedisOperationsSessionRepository sessionRepository = new RedisOperationsSessionRepository(sessionRedisTemplate);
        sessionRepository.setDefaultMaxInactiveInterval(maxInactiveIntervalInSeconds);
        return sessionRepository;
    }
    	
    @Bean
    public <S extends ExpiringSession> SessionRepositoryFilter<? extends ExpiringSession> springSessionRepositoryFilter(SessionRepository<S> sessionRepository, ServletContext servletContext) {
        SessionRepositoryFilter<S> sessionRepositoryFilter = new SessionRepositoryFilter<S>(sessionRepository);
        sessionRepositoryFilter.setServletContext(servletContext);
        if(httpSessionStrategy != null) {
            sessionRepositoryFilter.setHttpSessionStrategy(httpSessionStrategy);
        }
        return sessionRepositoryFilter;
    }
    //.....
} 
```

따라서 아래와 같이 xml configuration으로 생성된 SessionRepositoryFilter(Default로 CookieHttpSessionStrategy를 사용하는)  
설정 부분을 주석으로 처리하고 SessionRepository Bean도 주석으로 처리한 후 다시 실행해보았다.  
(그 외에도 필요한 RedisConnectionFactory, RedisTemplate 등은 RedisHttpSessionConfiguration에서 주입받고 있기 때문에  
 기존 xml configuration에서 선언한 내용으로 의도한대로 설정이 완료될 수 있었다.)
```xml
<!--<bean class="org.springframework.session.data.redis.RedisOperationsSessionRepository" name="sessionRepository" >
    <constructor-arg name="redisConnectionFactory" ref="jedisConnFactory" />
</bean>-->

<bean class="org.springframework.session.web.http.HeaderHttpSessionStrategy" name="sessionStrategy" />

<!--<bean name="springSessionRepositoryFilter" class="org.springframework.session.web.http.SessionRepositoryFilter">-->
<!--<constructor-arg name="sessionRepository" ref="sessionRepository" />-->
<!--</bean>-->

<bean class="org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration" >
    <property name="httpSessionStrategy" ref="sessionStrategy"/>
</bean>
```

로그인 시도 후 Response를 보면 아래와 같이 x-auth-token이 header에 담긴 것을 확인 할 수 있었으며  
기존 Code에서는 header에 담긴 Session ID를 다른 Request에 실어 보내지 않았기 때문에  
로그인 후 다른 시나리오는 정상적으로 처리될 수 없었다.  
<table border="2" style="width: fit-content"><tr><td>
<img src="https://raw.githubusercontent.com/dlxotn216/image/master/spring-multipartresolver/LoginSuccessRequest_HEADER.png" style="border: solid 5px black;" />
</td></tr></table>

이 부분은 추가적으로 개발이 필요한 부분이었지만 내가 맡은 솔루션에 다른 이슈들이 정체되어있어  
원인 파악을 완료한 것에 대해 만족하기로 했다.  

아마 발급 받은 Session ID를 모든 Request의 x-auth-token header에 실어 보내도록 설정 한 후에는  
MultipartFilter가 등록되지 않아도 정상적으로 파일 업로드가 가능 했을 것이라고 예측 된다.

## 4. 마치며
//TODO 