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

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * This test uploads a file to Google Cloud Storage and verifies that it was received in the local
 * directory specified in application-test.properties.
 *
 * <p>To run this test locally, first specify the buckets and local directory used by sample in
 * java/com/example/resources/application.properties. Then, run: mvn -Dit.storage=true test.
 */
@EnabledIfSystemProperty(named = "it.storage", matches = "true")
@ExtendWith(SpringExtension.class)
@PropertySource("classpath:application.properties")
@SpringBootTest(classes = {GcsSpringIntegrationApplication.class})
class GcsSpringIntegrationTests {

  private static final String TEST_FILE_NAME = "test_file";

  @Autowired private Storage storage;

  @Value("${gcs-read-bucket}")
  private String cloudInputBucket;

  @Value("${gcs-write-bucket}")
  private String cloudOutputBucket;

  @Value("${gcs-local-directory}")
  private String outputFolder;

  @BeforeEach
  void setupTestEnvironment() {
    cleanupCloudStorage();
  }

  @AfterEach
  void teardownTestEnvironment() throws IOException {
    cleanupCloudStorage();
    cleanupLocalDirectory();
  }

  @Test
  void testFilePropagatedToLocalDirectory() {
    BlobId blobId = BlobId.of(this.cloudInputBucket, TEST_FILE_NAME);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
    this.storage.create(blobInfo, "Hello World!".getBytes(StandardCharsets.UTF_8));

    Awaitility.await()
        .atMost(15, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Path outputFile = Paths.get(this.outputFolder + "/" + TEST_FILE_NAME);
              assertThat(Files.exists(outputFile)).isTrue();
              assertThat(Files.isRegularFile(outputFile)).isTrue();

              String firstLine = Files.lines(outputFile).findFirst().get();
              assertThat(firstLine).isEqualTo("Hello World!");

              List<String> blobNamesInOutputBucket = new ArrayList<>();
              this.storage
                  .list(this.cloudOutputBucket)
                  .iterateAll()
                  .forEach(b -> blobNamesInOutputBucket.add(b.getName()));

              assertThat(blobNamesInOutputBucket).contains(TEST_FILE_NAME);
            });
  }

  void cleanupCloudStorage() {
    Page<Blob> blobs = this.storage.list(this.cloudInputBucket);
    for (Blob blob : blobs.iterateAll()) {
      blob.delete();
    }
  }

  void cleanupLocalDirectory() throws IOException {
    Path localDirectory = Paths.get(this.outputFolder);
    List<Path> files = Files.list(localDirectory).collect(Collectors.toList());
    for (Path file : files) {
      Files.delete(file);
    }
    Files.deleteIfExists(Paths.get(this.outputFolder));
  }
}
