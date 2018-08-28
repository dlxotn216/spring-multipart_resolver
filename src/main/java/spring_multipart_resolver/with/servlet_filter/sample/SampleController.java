package spring_multipart_resolver.with.servlet_filter.sample;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Created by taesu on 2018-08-27.
 */
@RestController
public class SampleController {

    @PostMapping("/sample")
    public String SampleTest(@RequestParam(value = "upload") MultipartFile multipartFile,
                             @RequestParam(value = "text") String testParam) {
        System.out.println("textParam is :"+ testParam);
        if (multipartFile != null) {
            System.out.println("Multipart file in Request");
            System.out.println(multipartFile);
            System.out.println(multipartFile.getOriginalFilename());
            return "success";
        } else {
            System.out.println("Multipart file is null...");
            return "fail";
        }
    }
}
