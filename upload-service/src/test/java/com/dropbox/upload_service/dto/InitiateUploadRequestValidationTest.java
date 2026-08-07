package com.dropbox.upload_service.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InitiateUploadRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void rejectsBlankFileName() {
        InitiateUploadRequest request = new InitiateUploadRequest("", null, 1024L, "text/plain");

        Set<ConstraintViolation<InitiateUploadRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("fileName"));
    }

    @Test
    void rejectsNonPositiveSize() {
        InitiateUploadRequest request = new InitiateUploadRequest("movie.mp4", null, 0L, "video/mp4");

        Set<ConstraintViolation<InitiateUploadRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("size"));
    }

    @Test
    void acceptsValidRequest() {
        InitiateUploadRequest request = new InitiateUploadRequest("movie.mp4", UUID.randomUUID(), 1024L, "video/mp4");

        Set<ConstraintViolation<InitiateUploadRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
