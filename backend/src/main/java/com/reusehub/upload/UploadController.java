package com.reusehub.upload;

import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final Path uploadDir = Paths.get("uploads");

    public UploadController() throws IOException {
        Files.createDirectories(uploadDir);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFiles(@RequestParam("files") MultipartFile[] files, jakarta.servlet.http.HttpServletRequest request) throws IOException {
        List<String> urls = new ArrayList<>();
        log.info("Upload requested: {} files", files == null ? 0 : files.length);
        System.out.println("[UPLOAD] uploadFiles called, files=" + (files == null ? 0 : files.length));
        try {
            for (MultipartFile file : files) {
                log.info("Processing file: name='{}', original='{}', size={}", file.getName(), file.getOriginalFilename(), file.getSize());
                if (file.isEmpty()) continue;
                String contentType = file.getContentType();
                if (contentType == null || !contentType.matches("image/(png|jpeg|jpg|gif|webp)")) {
                    continue;
                }
                if (file.getSize() > 10L * 1024 * 1024) continue;

                String ext = Optional.ofNullable(file.getOriginalFilename())
                        .filter(n -> n.contains("."))
                        .map(n -> n.substring(n.lastIndexOf('.')))
                        .orElse("");
                String filename = UUID.randomUUID().toString() + ext;
                Path dest = uploadDir.resolve(filename);
                log.info("Saving file to {}", dest.toAbsolutePath());
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                String base = request.getScheme() + "://" + request.getServerName();
                int port = request.getServerPort();
                if (!(port == 80 || port == 443)) {
                    base = base + ":" + port;
                }
                String url = base + "/uploads/" + filename;
                urls.add(url);
            }
            return ResponseEntity.ok(urls);
        } catch (Exception ex) {
            log.error("Upload failed", ex);
            return ResponseEntity.status(500).body(java.util.Map.of("message", "Upload failed", "error", ex.getMessage()));
        }
    }
}
