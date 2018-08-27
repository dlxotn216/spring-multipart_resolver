package spring_multipart_resolver.with.servlet_filter.config;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.Enumeration;

/**
 * Created by taesu on 2018-08-27.
 */
@Component
public class SomeServletFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        Enumeration<String> parameterNames = httpServletRequest.getParameterNames();
        ServletInputStream inputStream = httpServletRequest.getInputStream();

        //http://18281818.tistory.com/76 참조
        HttpServletRequestWrapper requestWrapper = new HttpServletRequestWrapper(httpServletRequest);
        System.out.println(requestWrapper.getParameter("text"));

        //body 해석됨
//        System.out.println(inputStream.read());

        System.out.println(request.getParameter(""));
        System.out.println(request.getParameter("upload"));
        System.out.println(request.getParameter("text"));

        System.out.println(httpServletRequest.getParameter(""));
        System.out.println(httpServletRequest.getParameter("upload"));
        System.out.println(httpServletRequest.getParameter("text"));

        httpServletRequest.getParts()
                .forEach(part -> System.out.println("partName is :" + part.getName()));
        while (parameterNames.hasMoreElements()) {
            String param = parameterNames.nextElement();
            System.out.println(param + "::" + request.getParameter(param));
        }
        chain.doFilter(request, response);
    }
}
