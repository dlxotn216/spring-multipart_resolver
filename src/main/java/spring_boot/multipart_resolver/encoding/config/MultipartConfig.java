package spring_boot.multipart_resolver.encoding.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

/**
 * Created by taesu on 2018-04-18.
 *
 * 아래 설정에 의해 Servlet, StandartServletMultipartResolver, MultipartConfigElement 클래스들이 클래스 패스에 존재하면
 * MultipartAutoConfiguration이 활성화
 * - @ConditionalOnClass({ Servlet.class, StandardServletMultipartResolver.class,MultipartConfigElement.class })
 *
 * 이때 별도로 MultipartResolver클래스 유형의 Bean이 등록도지 않은 경우
 * - @ConditionalOnMissingBean(MultipartResolver.class)
 * 위 설정에 의해 StandardServletMultipartResolver가 multipartResolver로 등록 된다
 *
 * Spring boot에서 CommonsMultipartResolver 등록 시
 * Spring boot 1.5.4 부터 추가 된 MultipartAutoConfiguration 때문에 오동작 일으킬 수 있음
 *
 * 증상1 인코딩 문제
 * CommonsMultipartResolver가 등록 되었으나 실제로 StandartMultipartResolver를 사용하여
 * 파라미터의 인코딩 처리가 되지 않은 경우
 * 인코딩의 경우 Tomcat과 같이 UTF-8로 기본 설정 가능 한 컨테이너에선 문제가 없으나
 * jBoss (6.4 기준)에선 인코딩이 깨지는 문제가 발생 할 수 있음
 *
 * 증상2 파일 업로드가 되지 않는 경우
 * CommonsMultipartResolver를 사용하지만 multipart item이 0개로 처리되며
 * 파일 업로드 자체도 되지 않는 경우
 * 이런 경우 인코딩도 제대로 처리 되지 않을 가능성이 크다
 *
 * 해결을 위해 반드시 아래 설정을 @SpringBootApplication 어노테이션 아래에 추가해줘야 한다
 * - @EnableAutoConfiguration(exclude = {MultipartAutoConfiguration.class})
 *
 * StandardServletMultipartResolver를 사용할 경우엔 CharacterEncodingFilter에 의해 설정 된
 * 인코딩 값이 처리되지 않는 것을 주의해야 한다.
 * multipart/form-data 형태로 전송 할 경우 표준 인코딩 타입인 iso 8859-1로 적용되며
 * 컨테이너에서 별도 설정하지 않는 경우 인코딩이 깨지는 현상이 발생할 수 있다
 *
 */
@Configuration
public class MultipartConfig {
    @Bean
    public CommonsMultipartResolver multipartResolver() {
        CommonsMultipartResolver multipart = new CommonsMultipartResolver();
        multipart.setMaxUploadSize(3 * 1024 * 1024);
        return multipart;
    }

    /*
    Dispatcher servlet 이후에 시작되는 Multipart Request에 대한 parseRequest 작업을
    Filter에서 처리하는 설정.

    Spring Security, Spring Session 등의 프로젝트 등에서 Filter를 이용하여
    request 객체의 body를 먼저 읽어 처리하는 경우 유용하게 사용 가능.
     */
//    @Bean
//    @Order(1)
//    public MultipartFilter multipartFilter() {
//        MultipartFilter multipartFilter = new MultipartFilter();
//        multipartFilter.setMultipartResolverBeanName("multipartResolver");
//        return multipartFilter;
//    }
}
