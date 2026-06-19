package com.project.back.domain.document.controller;

import com.project.back.domain.document.dto.QuotePdfData;
import com.project.back.domain.document.dto.QuotePdfRequest;
import com.project.back.domain.document.service.QuoteDocumentService;
import com.project.back.global.exception.CustomException;
import com.project.back.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class QuoteDocumentController {

    private final QuoteDocumentService quoteDocumentService;

    @PostMapping("/quotes/pdf")
    public ResponseEntity<byte[]> downloadQuotePdf(
            @Valid @RequestBody QuotePdfRequest request
    ) {

        QuotePdfData.DocumentResult result;

        try {
                result = quoteDocumentService.generatePdf(request);
        } catch (IOException e) {
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(result.fileName(), StandardCharsets.UTF_8)
                        .build()
        );
        headers.setContentLength(result.fileSize());

        return ResponseEntity.ok()
                .headers(headers)
                .body(result.content());
    }
}
