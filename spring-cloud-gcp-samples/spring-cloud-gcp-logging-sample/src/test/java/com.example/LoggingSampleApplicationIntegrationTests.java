/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.api.client.util.DateTime;
import com.google.api.gax.paging.Page;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Payload.JsonPayload;
import com.google.cloud.spring.core.GcpProjectIdProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Tests for the logging sample app. */
@EnabledIfSystemProperty(named = "it.logging", matches = "true")
@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    classes = {Application.class})
class LoggingSampleApplicationIntegrationTests {

  private static final String LOG_FILTER_FORMAT =
      "trace:%s AND logName=projects/%s/logs/spring.log AND timestamp>=\"%s\"";

  @Autowired private GcpProjectIdProvider projectIdProvider;

  @Autowired private TestRestTemplate testRestTemplate;

  @LocalServerPort private int port;

  private Logging logClient;

  @BeforeEach
  void setupLogging() {
    this.logClient = LoggingOptions.getDefaultInstance().getService();
  }

  @Test
  void testLogRecordedInStackDriver() {
    DateTime startDateTime = new DateTime(System.currentTimeMillis());
    String url = String.format("http://localhost:%s/log", this.port);
    String traceHeader = "gcp-logging-test-" + Instant.now().toEpochMilli();

    HttpHeaders headers = new HttpHeaders();
    headers.add("x-cloud-trace-context", traceHeader);
    ResponseEntity<String> responseEntity =
        this.testRestTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();

    String logFilter =
        String.format(
            LOG_FILTER_FORMAT,
            traceHeader,
            this.projectIdProvider.getProjectId(),
            startDateTime.toStringRfc3339());

    await()
        .atMost(4, TimeUnit.MINUTES)
        .pollInterval(2, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Page<LogEntry> logEntryPage =
                  this.logClient.listLogEntries(Logging.EntryListOption.filter(logFilter));
              List<LogEntry> logEntries = new ArrayList<>();
              logEntryPage
                  .iterateAll()
                  .forEach(
                      le -> {
                        logEntries.add(le);
                      });

              List<String> logContents =
                  logEntries.stream()
                      .map(
                          logEntry ->
                              (String)
                                  ((JsonPayload) logEntry.getPayload())
                                      .getDataAsMap()
                                      .get("message"))
                      .collect(Collectors.toList());

              assertThat(logContents)
                  .containsExactlyInAnyOrder(
                      "This line was written to the log.",
                      "This line was also written to the log with the same Trace ID.");

              for (LogEntry logEntry : logEntries) {
                assertThat(logEntry.getLogName()).isEqualTo("spring.log");
                assertThat(logEntry.getResource().getLabels())
                    .containsEntry("project_id", this.projectIdProvider.getProjectId());
              }
            });
  }
}
