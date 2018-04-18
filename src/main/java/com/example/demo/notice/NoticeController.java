package com.example.demo.notice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by taesu on 2018-04-18.
 */
@RestController
@Slf4j
public class NoticeController {

    @PostMapping("/notices")
    public Notice addNotice(Notice notice) {
        log.info("CHECK Title:" + notice.getTitle());
        log.info("CHECK Content:" + notice.getContent());
        log.info("CHECK File:" + notice.getAttachedFile());

        return notice;
    }
}
