package spring_multipart_resolver.with.servlet_filter.sample;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import spring_boot.multipart_resolver.encoding.notice.Notice;
import spring_multipart_resolver.with.servlet_filter.SpringMultipartResolverWithServletFilterApplication;
import spring_multipart_resolver.with.servlet_filter.config.SomeServletFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by taesu on 2018-08-27.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SpringMultipartResolverWithServletFilterApplication.class,
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
public class SampleControllerTest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Test
    public void 파일업로드_테스트() throws Exception {
        MockMultipartFile mockMultipartFile = new MockMultipartFile("upload", "testFile.txt",
                "text/plain", "test data".getBytes());

        ByteArrayResource resource = new ByteArrayResource(mockMultipartFile.getBytes()) {
            @Override
            public String getFilename() throws IllegalStateException {
                return "testFile.txt";
            }
        };

        MultiValueMap<String, Object> multiValueMap = new LinkedMultiValueMap<>();
        multiValueMap.add("upload", resource);
        multiValueMap.add("text", "test");

        String result = testRestTemplate.postForObject("/sample", multiValueMap, String.class);
        log.info("CHECK result :" + result);

        assertThat(result).isEqualTo("success");
    }
}