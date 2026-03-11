package com.communitysport.upload.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.upload.dto.FileUploadResponse;

@Service
public class UploadService {

    private static final Set<String> ALLOWED_PHOTO_CATEGORIES = Set.of(
        "avatar",
        "venue",
        "course",
        "coach",
        "equipment",
        "notice",
        "complaint",
        "inspection",
        "banner"
    );

    private static final Set<String> ALLOWED_PHOTO_EXT = Set.of(
        ".jpg",
        ".jpeg",
        ".png",
        ".gif",
        ".webp",
        ".bmp"
    );

    public FileUploadResponse uploadPhoto(String category, MultipartFile file) {
        if (!StringUtils.hasText(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category required");
        }
        String cat = category.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_PHOTO_CATEGORIES.contains(cat)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category invalid");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
        }

        String original = file.getOriginalFilename();
        String ext = "";
        if (StringUtils.hasText(original)) {
            int idx = original.lastIndexOf('.');
            if (idx >= 0 && idx < original.length() - 1) {
                ext = original.substring(idx).toLowerCase(Locale.ROOT);
            }
        }
        if (!ALLOWED_PHOTO_EXT.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file type not allowed");
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path dir = Paths.get("src", "main", "resources", "static", "upload", "photo", cat).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create upload dir");
        }

        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(dir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid filename");
        }

        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save file");
        }

        FileUploadResponse resp = new FileUploadResponse();
        resp.setFileName(fileName);
        resp.setUrl("/upload/photo/" + cat + "/" + fileName);
        return resp;
    }
}
