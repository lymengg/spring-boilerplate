package com.example.demo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSP violation reporting endpoint (moved here from the Nuxt server, which is
 * now a static SPA and no longer serves /api/*).
 *
 * Browsers POST violation reports here when a `report-uri` directive is
 * present in the Content-Security-Policy header, with
 * `Content-Type: application/csp-report`:
 *
 *   { "csp-report": {
 *       "document-uri": "https://…/page",
 *       "violated-directive": "script-src",
 *       "blocked-uri": "https://evil.com/script.js",
 *       "line-number": 42,
 *       "source-file": "…"
 *   } }
 *
 * Intentionally public (no session) — violations can occur on the login page
 * before a session exists, and the browser sends the report automatically.
 * Logs a structured warning; wire this to your preferred aggregator (Sentry,
 * Datadog, etc.) as needed. Malformed reports are ignored — reporting is
 * best-effort and must never fail the reporter.
 */
@RestController
@RequestMapping("/api/csp-report")
@RequiredArgsConstructor
@Slf4j
public class CspReportController {

    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Void> report(@RequestBody(required = false) String rawBody) {
        try {
            JsonNode report = objectMapper.readTree(rawBody);
            JsonNode payload = report.has("csp-report") ? report.get("csp-report") : report;
            log.warn("CSP violation: {}", payload.toString());
        }
        catch (Exception e) {
            // Malformed or empty report — ignore.
        }
        return ResponseEntity.noContent().build();
    }
}
