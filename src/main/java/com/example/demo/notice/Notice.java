package com.example.demo.notice;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import org.springframework.web.multipart.MultipartFile;

/**
 * Created by taesu on 2018-04-18.
 */
@Data @ToString
public class Notice {
    private String title;
    private String content;
    @JsonIgnore
    private MultipartFile attachedFile;
}
