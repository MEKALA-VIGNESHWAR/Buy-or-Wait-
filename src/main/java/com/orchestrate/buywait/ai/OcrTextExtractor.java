package com.orchestrate.buywait.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to extract raw text lines from evidence PNG images using local OCR.
 * Reads from dataset/media/images/ocr_cache.json when available, or executes
 * local OCR process dynamically.
 */
@Component
public class OcrTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(OcrTextExtractor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<String>> ocrTextCache = new ConcurrentHashMap<>();

    public OcrTextExtractor() {
        loadOcrCache();
    }

    private void loadOcrCache() {
        Path cachePath = Paths.get("dataset", "media", "images", "ocr_cache.json");
        if (Files.exists(cachePath)) {
            try {
                Map<String, List<String>> loaded = objectMapper.readValue(
                        cachePath.toFile(),
                        new TypeReference<Map<String, List<String>>>() {}
                );
                if (loaded != null) {
                    ocrTextCache.putAll(loaded);
                    log.info("Loaded OCR text cache with {} images from {}", loaded.size(), cachePath);
                }
            } catch (IOException e) {
                log.warn("Could not load ocr_cache.json: {}", e.getMessage());
            }
        }
    }

    /**
     * Retrieves OCR text lines for a given image ID.
     */
    public List<String> extractText(String imageId, Path imagePath) {
        if (imageId == null || imageId.isBlank()) {
            return Collections.emptyList();
        }

        String filename = imageId.endsWith(".png") ? imageId : imageId + ".png";
        if (ocrTextCache.containsKey(filename)) {
            return ocrTextCache.get(filename);
        }

        // If not in cache and imagePath exists, attempt to run local OCR script
        if (imagePath != null && Files.exists(imagePath)) {
            List<String> dynamicLines = runLocalOcrProcess(imagePath);
            if (!dynamicLines.isEmpty()) {
                ocrTextCache.put(filename, dynamicLines);
                return dynamicLines;
            }
        }

        return Collections.emptyList();
    }

    private List<String> runLocalOcrProcess(Path imagePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "python", "-c",
                    "import easyocr, sys, json; " +
                    "reader = easyocr.Reader(['en'], gpu=False); " +
                    "lines = reader.readtext(sys.argv[1], detail=0); " +
                    "print(json.dumps(lines))",
                    imagePath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor();

            if (process.exitValue() == 0 && output.startsWith("[")) {
                return objectMapper.readValue(output, new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            log.debug("Dynamic local OCR execution failed for {}: {}", imagePath, e.getMessage());
        }
        return Collections.emptyList();
    }
}
