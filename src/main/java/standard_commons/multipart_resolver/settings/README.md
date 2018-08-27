## ComonsMultipartResolver를 사용할 때 아래설정을 넣을 수 있다

#### - 한 요청 당 최대 업로드 크기
maxUploadSize               : default -1 (no limit)

#### - 파일 당 최대 업로드 크기
maxUploadSizePerFile     : default -1 (no limit)

#### - 디스크에 저장하지 않고 메모리에 유지하도록 허용하는 최대 크기
maxInMemorySize             : default 10240

#### - 기본 인코딩 (request에 인코딩 설정이 없는 경우 동작한다)
defaultEncoding             : default ISO-8859-1

#### - 디스크에 저장 시 위치할 임시 디렉토리
uploadTmpDir                : default org.springframework.web.util.WebUtils#TEMP_DIR_CONTEXT_ATTRIBUTE (javax.servlet.context.tempdir)

이때 uploadTmpDir을 지정할 때 기본 값인 javax.servlet.context.tmpdir은 어디일까?

Jboss 6.2기준
${JBOSS_HOME}\standalone\tmp\work\jboss.web\default-host\_

디렉토리 경로에
upload_0028ab7b_ae65_4e58_b8ef_f37354dfc39f_00000034.tmp 처럼 파일이 저장된다

저장된 파일에 대해서 삭제 로직은 필요 없을까?

한 블로그에서 Servle3.0 MultipartConfig의 문제점이라는 글을 읽었는데
StandarMultipartResolver를 사용할 경우 multipart.delete()가 제대로 동작하지 않는다는 내용이었다.

일단 CommonsMultipartResolver를 사용할 경우 동작을 확인해보았다 
당연히 삭제로직이 존재하며 CommonsMultipartFile에는 파일이 저장된 위치가 기억되어있다
<pre><code>
@Override
public void cleanupMultipart(MultipartHttpServletRequest request) {
    if (!(request instanceof AbstractMultipartHttpServletRequest) ||
            ((AbstractMultipartHttpServletRequest) request).isResolved()) {
        try {
            cleanupFileItems(request.getMultiFileMap());
        }
        catch (Throwable ex) {
            logger.warn("Failed to perform multipart cleanup for servlet request", ex);
        }
    }
}
 
protected void cleanupFileItems(MultiValueMap<String, MultipartFile> multipartFiles) {
    for (List<MultipartFile> files : multipartFiles.values()) {
        for (MultipartFile file : files) {
            if (file instanceof CommonsMultipartFile) {
                CommonsMultipartFile cmf = (CommonsMultipartFile) file;
                cmf.getFileItem().delete();
                
                if (logger.isDebugEnabled()) {
                    logger.debug("Cleaning up multipart file [" + cmf.getName() + "] with original filename [" +
                    cmf.getOriginalFilename() + "], stored " + cmf.getStorageDescription());
                }
            }
        }
    }
}
</code></pre>

### Compare getStorageDescription 

maxInMemorySize 보다 적은 사이즈의 파일일 때
getStorageDescription of CommonsMultipartFile :in memory

maxInMemorySize 보다 큰 사이즈의 파일일 때
getStorageDescription of CommonsMultipartFile :at [C:\Work\installer\jboss-eap-6.4\standalone\tmp\work\jboss.web\default-host\_\upload_0028ab7b_ae65_4e58_b8ef_f37354dfc39f_00000034.tmp]


그렇다면 StandardServletMultipartResolver는?
<pre><code>
@Override
public void cleanupMultipart(MultipartHttpServletRequest request) {
    if (!(request instanceof AbstractMultipartHttpServletRequest) ||
            ((AbstractMultipartHttpServletRequest) request).isResolved()) {
        // To be on the safe side: explicitly delete the parts,
        // but only actual file parts (for Resin compatibility)
        try {
            for (Part part : request.getParts()) {
                if (request.getFile(part.getName()) != null) {
                    part.delete();
                }
            }
        }
        catch (Throwable ex) {
            LogFactory.getLog(getClass()).warn("Failed to perform cleanup of multipart items", ex);
        }
    }
}
</code></pre>

마찬가지다 프레임워크 레벨에서 처리하는 로직은 part.delete()를 그대로 호출하고있고
역시나 업로드 된 파일이 위치한 임시경로를 정확히 가리키고 있었다

**아마 블로그에선 maxInMemorySize 이하의 파일만 업로드 하였기에 
삭제가 제대로 이뤄지지 않는구나 라고 생각 했을지도 모른다...

### Reference
* Tomcat 환경에서 workDir 이라고 불리는 javax.servlet.context.tempdir의 위치는 work 디렉토리이다

Pathname to a scratch directory to be provided by this Context for temporary read-write use by servlets within the associated web application. 
This directory will be made visible to servlets in the web application by a servlet context attribute (of type java.io.File) named javax.servlet.context.tempdir as described in the Servlet Specification. 
If not specified, a suitable directory underneath $CATALINA_HOME/work will be provided.


* StandardServletMultipartResolver의 설정값은 아래와 같다

MultipartConfigElement 클래스에 존재하며 @MultipartConfig 값을 통해 자동으로 읽어들이는 로직이 

<pre><code>
private final String location;// = "";             CommonsMultipartResolver의 tmpDir 
private final long maxFileSize;// = -1;            CommonsMultipartResolver의 maxUploadSizePerFile
private final long maxRequestSize;// = -1;         CommonsMultipartResolver의 maxUploadSize  
private final int fileSizeThreshold;// = 0;        CommonsMultipartResolver의 maxInMemorySize
</code></pre>

특히 fileSizeThresholde 값은 0 미만으로 줄 수 없게 되어있다.
<pre><code>
// Avoid threshold values of less than zero as they cause trigger NPEs
// in the Commons FileUpload port for fields that have no data.
if (fileSizeThreshold > 0) {
    this.fileSizeThreshold = fileSizeThreshold;
} else {
    this.fileSizeThreshold = 0;
}
</code></pre>