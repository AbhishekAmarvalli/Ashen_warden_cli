package com.ashenwarden.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Loads UTF-8 content files from src/main/resources/content with Jackson
 * (documented parser dependency — no hand-rolled JSON parsing) and turns any
 * failure into a ContentException reported cleanly at startup.
 */
public final class ContentLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ContentLoader() {
    }

    public static <T> List<T> list(String resourcePath, TypeReference<List<T>> type) {
        try (InputStream in = open(resourcePath)) {
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new com.ashenwarden.common.ContentException(
                    "Failed to read content file '" + resourcePath + "': " + e.getMessage(), e);
        }
    }

    public static String text(String resourcePath) {
        try (InputStream in = open(resourcePath)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new com.ashenwarden.common.ContentException(
                    "Failed to read text file '" + resourcePath + "': " + e.getMessage(), e);
        }
    }

    private static InputStream open(String resourcePath) {
        InputStream in = ContentLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new com.ashenwarden.common.ContentException("Missing required content resource: " + resourcePath);
        }
        return in;
    }
}
