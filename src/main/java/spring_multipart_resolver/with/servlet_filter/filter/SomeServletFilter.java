package spring_multipart_resolver.with.servlet_filter.filter;

import org.apache.commons.io.IOUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by taesu on 2018-08-27.
 */
public class SomeServletFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        //body 해석 됨
        HttpServletRequestWrapper requestWrapper = new HttpServletRequestWrapper(httpServletRequest);
        InputStream in = requestWrapper.getInputStream();
        String requestBody = new String(IOUtils.toByteArray(in));

        System.out.println(requestBody);
        chain.doFilter(request, response);
    }
}
