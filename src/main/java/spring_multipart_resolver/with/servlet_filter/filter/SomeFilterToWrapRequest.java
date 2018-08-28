package spring_multipart_resolver.with.servlet_filter.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;

/**
 * Created by taesu on 2018-08-28.
 *
 * Request Wrapper
 *
 * Multpart Filter 앞단에 존재하여도 아무런 상관 없음
 */
public class SomeFilterToWrapRequest implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // 요청 래퍼 객체 생성
        HttpServletRequestWrapper requestWrapper = new RequestWrapper((HttpServletRequest) request);

        // 체인의 다음 필터에 요청 래퍼 객체 전달
        chain.doFilter(requestWrapper, response);
    }

    class RequestWrapper extends HttpServletRequestWrapper {
        /**
         * Constructs a request object wrapping the given request.
         *
         * @param request The request to wrap
         * @throws IllegalArgumentException if the request is null
         */
        public RequestWrapper(HttpServletRequest request) {
            super(request);
        }

        public String getParameter(String name) {
            if (name.equals("upload")) {
                return null;
            } else {
                return super.getParameter(name);
            }
        }
    }
}
