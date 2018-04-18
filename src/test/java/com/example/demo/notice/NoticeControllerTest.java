package com.example.demo.notice;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.not;

/**
 * Created by taesu on 2018-04-18.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
public class NoticeControllerTest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Test
    public void fileUploadTest() throws IOException {
        MockMultipartFile mockMultipartFile = new MockMultipartFile("user-file", "testFile.txt",
                "text/plain", "test data".getBytes());

        ByteArrayResource resource = new ByteArrayResource(mockMultipartFile.getBytes()) {
            @Override
            public String getFilename() throws IllegalStateException {
                return "testFile.txt";
            }
        };

        String title = "공지사항 테스트 제목";
        String content = "한글 인코딩이 깨지나요?";

        MultiValueMap<String, Object> multiValueMap = new LinkedMultiValueMap<>();
        multiValueMap.add("title", title);
        multiValueMap.add("content", content);
        multiValueMap.add("attachedFile", resource);

        Notice notice = testRestTemplate.postForObject("/notices", multiValueMap, Notice.class);
        log.info("CHECK result :" + notice);

        assertThat(notice.getTitle()).isEqualTo(title);

    }

}