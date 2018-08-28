package spring_multipart_resolver.with.servlet_filter.config;

import com.navercorp.lucy.security.xss.servletfilter.XssEscapeServletFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.support.MultipartFilter;
import spring_multipart_resolver.with.servlet_filter.filter.SomeFilterToWrapRequest;
import spring_multipart_resolver.with.servlet_filter.filter.SomeServletFilter;

/**
 * Created by taesu on 2018-08-28.
 */
@Configuration
public class WebConfig {

    /*
    Request failed 사례
    "{"timestamp":"2018-08-28T12:15:37.141+0000","status":400,"error":"Bad Request","message":"Required request part 'upload' is not present","path":"/sample"}"

    @Bean
    public FilterRegistrationBean<MultipartFilter> multipartFilterFilterRegistrationBean() {
        FilterRegistrationBean<MultipartFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(multipartFilter());
        registrationBean.setOrder(2);       //순서 주의

        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<SomeServletFilter> servletFilterFilterRegistrationBean() {
        FilterRegistrationBean<SomeServletFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(someServletFilter());
        registrationBean.setOrder(1);       //순서 주의

        return registrationBean;
    }
     */

    @Bean
    public FilterRegistrationBean<MultipartFilter> multipartFilterFilterRegistrationBean() {
        FilterRegistrationBean<MultipartFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(multipartFilter());
        registrationBean.setOrder(1);

        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<SomeServletFilter> servletFilterFilterRegistrationBean() {
        FilterRegistrationBean<SomeServletFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(someServletFilter());
        registrationBean.setOrder(2);

        return registrationBean;
    }

    /*@Bean
    public FilterRegistrationBean<SomeFilterToWrapRequest> someFilterToWrapRequestFilterRegistrationBean() {
        FilterRegistrationBean<SomeFilterToWrapRequest> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(someFilterToWrapRequest());
        registrationBean.setOrder(0);

        return registrationBean;
    }*/

    /*@Bean
    public FilterRegistrationBean<XssEscapeServletFilter> xssEscapeServletFilterFilterRegistrationBean() {
        FilterRegistrationBean<XssEscapeServletFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(xssEscapeServletFilter());
        registrationBean.setOrder(0);

        return registrationBean;
    }*/

    /**
     * XSS 공격을 방지하는 Filter
     *
     * @return XssEscapeServletFilter
     */
    private XssEscapeServletFilter xssEscapeServletFilter() {
        return new XssEscapeServletFilter();
    }

    /**
     * Request를 Wrapping 하는 filter
     *
     * @return SomeFilterToWrapRequest
     */
    private SomeFilterToWrapRequest someFilterToWrapRequest() {
        return new SomeFilterToWrapRequest();
    }

    /**
     * Request를 소비하는 Filter
     *
     * @return SomeServletFilter
     */
    private SomeServletFilter someServletFilter() {
        return new SomeServletFilter();
    }

    /**
     * Multipart Request에 대해
     * resolve를 Filter에서 처리하도록 설정
     * <p>
     * request의 body를 읽어 처리하는 SomeFilter보다 앞서 설정하도록 처리하는 것 주의
     *
     * @return MultipartFilter
     */
    private MultipartFilter multipartFilter() {
        MultipartFilter multipartFilter = new MultipartFilter();
        multipartFilter.setMultipartResolverBeanName("multipartResolver");
        return multipartFilter;
    }
}
