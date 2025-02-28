/*
 * Copyright 2017-2018 the original author or authors.
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

package com.google.cloud.spring.storage.integration;

import com.google.cloud.storage.BlobInfo;
import java.util.Date;
import org.springframework.integration.file.remote.AbstractFileInfo;
import org.springframework.util.Assert;

/** An object that holds metadata information for a Cloud Storage file. */
public class GcsFileInfo extends AbstractFileInfo<BlobInfo> {

  private BlobInfo gcsFile;

  public GcsFileInfo(BlobInfo gcsFile) {
    Assert.notNull(gcsFile, "The GCS blob can't be null.");
    this.gcsFile = gcsFile;
  }

  @Override
  public boolean isDirectory() {
    return this.gcsFile.isDirectory();
  }

  @Override
  public boolean isLink() {
    return false;
  }

  @Override
  public long getSize() {
    return this.gcsFile.getSize();
  }

  @Override
  public long getModified() {
    return this.gcsFile.getUpdateTime();
  }

  @Override
  public String getFilename() {
    return this.gcsFile.getName();
  }

  @Override
  public String getPermissions() {
    throw new UnsupportedOperationException("Use [BlobInfo.getAcl()] to obtain permissions.");
  }

  @Override
  public BlobInfo getFileInfo() {
    return this.gcsFile;
  }

  @Override
  public String toString() {
    return "FileInfo [isDirectory="
        + isDirectory()
        + ", isLink="
        + isLink()
        + ", Size="
        + getSize()
        + ", ModifiedTime="
        + new Date(getModified())
        + ", Filename="
        + getFilename()
        + ", RemoteDirectory="
        + getRemoteDirectory()
        + "]";
  }
}
