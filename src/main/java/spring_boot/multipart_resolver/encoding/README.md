## 1. 사건의 발단

Spring 4.0.4 기반 프로젝트 진행 중 multipart/form-data 형태로 전송할 때 파일 외의 파라미터의 인코딩이 깨지는 현상 발생 
WAS: JBoss 6.2, Spring: 4.0.4, Spring Security ACL 사용 중

한글, 중국어, 일본어가 깨지는 것을 확인 후 테스트 중 아래 코드로 정상 처리 됨을 확인했다. 
<pre><code>String decodedToUTF8 = new String(encodedWithISO88591.getBytes("ISO-8859-1"), "UTF-8");</code></pre>

JSP 시절 request.setcharacterencoding( utf-8 )으로 처리 하지 않았을 때 해결 법으로 기억하여 
Spring Security의 필터가 CharacterEncodingFilter를 방해하는 것인가 판단하였으나 
CharacterEncodingFilter가 SecurityFilter 보다 먼저 선언되어있어 문제는 없었다.

## 2. 원인 파악

원인은 Bean으로 등록한 MultipartResolver에 StandardServletMultipartResolver로 설정한 부분이었는데
StandradServletMultipartReolsver는 Servlet 3.0 이상부터 표준

기존 CommonsMultipartResolver는 CharacterEncodingFilter에서 설정 한 인코딩 값을 기반으로 
CommonsFileUploadSupport#parseFileItems 메소드를 통해 MultipartParsingResult를 반환 받아 사용한다 
이때 Request parameter의 값들이 인코딩이 되어 처리 된다.

반면 StandardServletMultipartResolver에선 오직 파일명에 대해서만(정확히는 MultiPart 타입인 것) 
인코딩 처리하며 나머지 파라미터에 대해서는 인코딩 처리하지 않는다 (즉, ISO-8859-1 인코딩 그대로 Controller에 전달 된다)
따라서 파라미터의 인코딩이 깨지는 문제가 발생한 것을 CommonsMultipartResolver로 변경하여 처리하였다.

## 3. 다른 프로젝트에서 마주친 동일한 문제 그리고 Spring boot...

며칠 뒤 Spring Boot 관련 프로젝트에서도 동일한 문제가 발생했다. 
특이점은 테스트 PC의 로컬에선 잘 동작하는 것이 서버에선 동작하지 앟는 점.
해당 PC는 로컬은 내장 톰캣을 사용하며, 서버에선 JBOSS-6.2를 사용 중

컨테이너의 설정 차이일까 싶었지만 어플리케이션 설정에서 StandardServletMultiprtResolver를 사용하고 있었기에
@Bean으로 등록 한 StandardServletMultiprtResolver를 CommonsMultipartResolver로 변경 해주었다.
하지만 계속해서 인코딩이 깨지는 문제가 발생하였고 컨테이너의 설정 문제라는 잠정 결론을 내렸다.

며칠 뒤 이상하다 싶어 로컬에 프로젝트 셋팅 후 테스트를 해보았는데 전혀 문제가 없었다. 
디버깅을 하던 중 CommonsMultipartResolver로 전혀 실행점이 잡히지 않는 다는 문제 발견. 
즉, MultipartResolver를 CommonsMultipartResolver로 해주었느나 실제는 StandardServletMultipartResolver를 사용 중이었다.

그렇다면 로컬에선 StandardServletMultipartResolver사용해도 왜 인코딩이 깨지지 않을까? 

내장 톰캣은 기본적으로 인코딩을 UTF-8로 처리함, 반면 JBOSS-6.2는 관련 설정을 해주어야 처리 되는 것으로 보인다. 
(내장 톰캣의 버전은 8.5이며 출처는 http://tomcat.10.x6.nabble.com/Is-UTF-8-used-everywhere-for-Tomcat-8-5-6-td5056341.html)

Spring boot에서 왜 StandardServletMultipartResolver 설정이 CommonsMultipartResolver보다 우선순위가 높을까? 
바로 SpringBootApplication -> EnableAutoConfiguration -> MultipartAutoConfiguration 때문

아래 옵션에 따라 클래스패스에 StandardServletMultipartResolver이 있으면 MultipartAutoConfiguration이 활성화 된다 
<pre><code>@ConditionalOnClass({ Servlet.class, StandardServletMultipartResolver.class, MultipartConfigElement.class })</code></pre>

한 가지 의문점은 문제의 재현을 위해 나의 집에서 테스트를 하였을 땐 이상하게도 CommonsMultipartResolver를 잘 사용한다는 점 

더 이상한 것은 CommonsMultipartResolver에 들어왔을 때 fileItem이 모두 0으로 찍혀 파일 업로드조차 안되는 점 (MultipartFile이 null로 찍힘)
위 경우에도 fileItem이 0이기 때문에 루프를 돌지 않고 따라서 인코딩 처리도 하지 않을 것으로 예상 한다.

**이러한 증상을 예방하기 위해선 Spring boot에서 CommonsMultipartResolver를 사용 할 경우 아래 설정을 반드시 @SpringBootApplication 아래에 지정 해주어야 한다.**
<pre><code>@EnableAutoConfiguration(exclude = {MultipartAutoConfiguration.class})</code></pre>

결과적으로 CommonsMultipartResolver를 설정 해주었으나 StandardServletMultipartResolver로 실행 흐름이 탔던 문제 및 
CommonsMultipartResolver로 제대로 실행 흐름이 탔으나 fileItem이 0으로 찍혀 파일 업로드 조차도 진행 되지 않던 문제를 해결 할 수 있다.

개발 환경이라던지 다양한 환경에 따라서 증상은 다르게 나타날 것으로 생각 된다.

StandardServletMultipartResolver를 사용하려 할 경우 톰캣과 같이 인코딩을 자동 처리해주는 경우를 제외하고는 
파일 외의 파라미터 값들이 인코딩 되지 않아 깨지는 현상을 주의.

그냥 최신 WAS를 쓰면 문제도 없지 않을까?


* * *
### 추가 내용

CommonsMultipartResolver를 사용하며 MultipartAutoConfiguration 옵션을 끄지 않았을 때 로직을 확인해보면 
FileUploadBase 클래스에서 findNextItem 메소드를 호출하여 Multipart 들을 찾는데 
multi.skipPreamble() 메소드에서 false를 반환하여 아무런 fileItem을 반환하지 않는다.

<pre><code>
private boolean findNextItem() throws IOException {
  if (eof) {
    return false;
  }

  if (currentItem != null) {
    currentItem.close(); currentItem = null;
  }

  for (;;) {
    boolean nextPart;

    if (skipPreamble) {
    nextPart = multi.skipPreamble();
    ......
</code></pre>
    
따라서 아래의 인코딩 로직을 처리하는 루프가 동작하지 않는다.

<pre><code>
for (FileItem fileItem : fileItems) {
  if (fileItem.isFormField()) {
    String value; 
    String partEncoding = determineEncoding(fileItem.getContentType(), encoding);
</code></pre>
    
skipPreamble 메소드 내부는 아래와 같은데 discardBodyData 메소드 호출 중 **MalformedStreamException**이 발생하여 false를 리턴하기 때문이다.

<pre><code>
public boolean skipPreamble() throws IOException {
  System.arraycopy(boundary, 2, boundary, 0, boundary.length - 2); boundaryLength = boundary.length - 2;
  try {
    discardBodyData();
    return readBoundary();
  } catch (MalformedStreamException e) {
    return false;
  } finally {
    // Restore delimiter. System.arraycopy(boundary, 0, boundary, 2, boundary.length - 2); boundaryLength = boundary.length; boundary[0] = CR; boundary[1] = LF;
  }
}
</code></pre>

discardBodyData 메소드의 JavaDoc을 확인하면 아래와 같은 구절이 있는데 
<pre><code>@throws MalformedStreamException if the stream ends unexpectedly. </code></pre>
Stream이 예기치 못하게 닫혔을 경우 발생하는 예외이다.

예상하기론 아래 설정을 적용하지 않으면 
<pre><code>@EnableAutoConfiguration(exclude = {MultipartAutoConfiguration.class})</code></pre>
Multipart에 대한 requestBody의 input stream이 CommonsMultipartResolver에서 사용하는 형식대로 들어오지 않는 것 같다.