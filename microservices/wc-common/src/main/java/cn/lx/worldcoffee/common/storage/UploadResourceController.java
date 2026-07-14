package cn.lx.worldcoffee.common.storage;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class UploadResourceController {

    private final FileStorageService fileStorageService;

    @GetMapping("/uploads/**")
    public ResponseEntity<Void> redirectToMinio(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int index = uri.indexOf("/uploads/");
        String objectName = index >= 0 ? uri.substring(index + 1) : uri.replaceFirst("^/+", "");
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(fileStorageService.publicUrl(objectName)).toString())
                .build();
    }
}
