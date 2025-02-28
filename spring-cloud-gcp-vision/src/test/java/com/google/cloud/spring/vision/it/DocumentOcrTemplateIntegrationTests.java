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

package com.google.cloud.spring.vision.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.spring.storage.GoogleStorageLocation;
import com.google.cloud.spring.vision.DocumentOcrResultSet;
import com.google.cloud.spring.vision.DocumentOcrTemplate;
import com.google.cloud.vision.v1.TextAnnotation;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.concurrent.ListenableFuture;

@EnabledIfSystemProperty(named = "it.vision", matches = "true")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {VisionTestConfiguration.class})
class DocumentOcrTemplateIntegrationTests {

  @Autowired private DocumentOcrTemplate documentOcrTemplate;

  @Test
  void testDocumentOcrTemplate()
      throws ExecutionException, InterruptedException, InvalidProtocolBufferException,
          TimeoutException {

    GoogleStorageLocation document =
        GoogleStorageLocation.forFile("vision-integration-test-bucket", "test.pdf");
    GoogleStorageLocation outputLocationPrefix =
        GoogleStorageLocation.forFile("vision-integration-test-bucket", "it_output/test-");

    ListenableFuture<DocumentOcrResultSet> result =
        this.documentOcrTemplate.runOcrForDocument(document, outputLocationPrefix);

    DocumentOcrResultSet ocrPages = result.get(5, TimeUnit.MINUTES);

    String page1Text = ocrPages.getPage(1).getText();
    assertThat(page1Text).contains("Hello World. Is mayonnaise an instrument?");

    String page2Text = ocrPages.getPage(2).getText();
    assertThat(page2Text).contains("Page 2 stuff");

    ArrayList<String> pageContent = new ArrayList<>();

    Iterator<TextAnnotation> pageIterator = ocrPages.getAllPages();
    while (pageIterator.hasNext()) {
      pageContent.add(pageIterator.next().getText());
    }

    assertThat(pageContent)
        .containsExactly(
            "Hello World. Is mayonnaise an instrument?\n",
            "Page 2 stuff\n",
            "Page 3 stuff\n",
            "Page 4 stuff\n");
  }

  @Test
  void testParseOcrResultSet() throws InvalidProtocolBufferException {
    GoogleStorageLocation ocrOutputPrefix =
        GoogleStorageLocation.forFolder("vision-integration-test-bucket", "json_output_set/");

    DocumentOcrResultSet result = this.documentOcrTemplate.readOcrOutputFileSet(ocrOutputPrefix);

    String text = result.getPage(2).getText();
    assertThat(text).contains("Hello World. Is mayonnaise an instrument?");
  }

  @Test
  void testParseOcrFile() throws InvalidProtocolBufferException {
    GoogleStorageLocation ocrOutputFile =
        GoogleStorageLocation.forFile(
            "vision-integration-test-bucket", "json_output_set/test_output-2-to-2.json");

    DocumentOcrResultSet pages = this.documentOcrTemplate.readOcrOutputFile(ocrOutputFile);

    String text = pages.getPage(2).getText();
    assertThat(text).contains("Hello World. Is mayonnaise an instrument?");
  }
}
