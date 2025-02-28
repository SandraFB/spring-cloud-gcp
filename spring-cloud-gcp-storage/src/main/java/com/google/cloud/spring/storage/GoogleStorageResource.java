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

package com.google.cloud.spring.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.WritableResource;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

/**
 * Implements {@link WritableResource} for reading and writing objects in Google Cloud Storage
 * (GCS). An instance of this class represents a handle to a bucket or a blob.
 */
public class GoogleStorageResource implements WritableResource {

  private static final Log LOGGER = LogFactory.getLog(GoogleStorageResource.class);

  private final Storage storage;

  private final GoogleStorageLocation location;

  private final boolean autoCreateFiles;

  /**
   * Constructs the resource representation of a bucket or a blob (file) in Google Cloud Storage.
   *
   * @param storage the Google Cloud Storage client
   * @param locationUri the URI of the bucket or blob, e.g., gs://your-bucket/ or
   *     gs://your-bucket/your-file-name
   * @param autoCreateFiles determines the auto-creation of the file in Google Cloud Storage if an
   *     operation that depends on its existence is triggered (e.g., getting the output stream of a
   *     file)
   * @throws IllegalArgumentException if the location URI is invalid
   */
  public GoogleStorageResource(Storage storage, String locationUri, boolean autoCreateFiles) {
    this(storage, new GoogleStorageLocation(locationUri), autoCreateFiles);
  }

  /**
   * Constructor that defaults autoCreateFiles to true.
   *
   * @param locationUri the cloud storage address
   * @param storage the storage client
   * @see #GoogleStorageResource(Storage, String, boolean)
   */
  public GoogleStorageResource(Storage storage, String locationUri) {
    this(storage, locationUri, true);
  }

  /**
   * Constructs the resource representation of a bucket or a blob (file) in Google Cloud Storage.
   *
   * @param storage the Google Cloud Storage client
   * @param googleStorageLocation the {@link GoogleStorageLocation} of the resource.
   * @param autoCreateFiles determines the auto-creation of the file in Google Cloud Storage if an
   *     operation that depends on its existence is triggered (e.g., getting the output stream of a
   *     file)
   * @throws IllegalArgumentException if the location is an invalid Google Storage location
   * @since 1.2
   */
  public GoogleStorageResource(
      Storage storage, GoogleStorageLocation googleStorageLocation, boolean autoCreateFiles) {
    Assert.notNull(storage, "Storage object can not be null");
    this.storage = storage;
    this.location = googleStorageLocation;
    this.autoCreateFiles = autoCreateFiles;
  }

  public boolean isAutoCreateFiles() {
    return this.autoCreateFiles;
  }

  /**
   * @return Returns true if the bucket or object exists.
   * @throws StorageException if an issue occurs getting the Bucket or Blob.
   */
  @Override
  public boolean exists() {
    return isBucket() ? getBucket() != null : getBlob() != null;
  }

  @Override
  public boolean isReadable() {
    return !isBucket();
  }

  @Override
  public boolean isOpen() {
    return false;
  }

  /**
   * @return the self-link for this GCS resource. Note that this is not a URL to download the
   *     contents of the file.
   */
  @Override
  @NonNull
  public URL getURL() throws IOException {
    String url;
    if (isBucket()) {
      Bucket bucket = getBucket();
      Assert.notNull(bucket, "The bucket " + this.getBucketName() + " does not exist.");
      url = bucket.getSelfLink();
    } else {
      Blob b = getBlob();
      Assert.notNull(b, "The object at " + this.getURI() + " does not exist.");
      url = getBlob().getSelfLink();
    }
    return new URL(url);
  }

  @Override
  @NonNull
  public URI getURI() {
    return this.location.uri();
  }

  /**
   * Gets the underlying storage object in Google Cloud Storage.
   *
   * @return the storage object, will be null if it does not exist in Google Cloud Storage.
   * @throws StorageException if an issue occurs getting the Blob
   * @throws IllegalStateException if the resource reference is to a bucket, and not a blob.
   */
  public Blob getBlob() {
    return this.storage.get(getBlobId());
  }

  /**
   * Creates a signed URL to an object, if it exists. This method will fail if this storage resource
   * was not created using service account credentials.
   *
   * @param timeUnit the time unit used to determine how long the URL is valid.
   * @param timePeriods the number of periods to determine how long the URL is valid.
   * @param options specifies additional options for signing URLs
   * @return the URL if the object exists, and null if it does not.
   * @throws IllegalStateException if the resource reference is to a bucket, and not a blob.
   * @throws StorageException if there are errors in accessing Google Storage
   */
  public URL createSignedUrl(
      TimeUnit timeUnit, long timePeriods, Storage.SignUrlOption... options) {
    if (LOGGER.isWarnEnabled() && !exists()) {
      LOGGER.warn("Creating signed URL for non-existing GCS object " + getURI());
    }

    return this.storage.signUrl(
        BlobInfo.newBuilder(getBlobId()).build(), timePeriods, timeUnit, options);
  }

  /**
   * Creates the blob that this {@link GoogleStorageResource} represents in Google Cloud Storage.
   *
   * @return the created blob object
   * @throws StorageException if any errors during blob creation arise, such as if the blob already
   *     exists
   * @throws IllegalStateException if the resource reference is to a bucket, and not a blob.
   */
  public Blob createBlob() {
    return this.storage.create(BlobInfo.newBuilder(getBlobId()).build());
  }

  /**
   * Creates the blob that this {@link GoogleStorageResource} represents in Google Cloud Storage and
   * fills it with provided content.
   *
   * @param contents the initial file contents to write
   * @return the created blob object
   * @throws StorageException if any errors during blob creation arise, such as if the blob already
   *     exists
   * @throws IllegalStateException if the resource reference is to a bucket, and not a blob.
   * @since 1.2.2
   */
  public Blob createBlob(byte[] contents) {
    return this.storage.create(BlobInfo.newBuilder(getBlobId()).build(), contents);
  }

  /**
   * Creates the bucket that this resource references in Google Cloud Storage.
   *
   * @return the {@link Bucket} object for the bucket
   * @throws StorageException if any errors during bucket creation arise, such as if the bucket
   *     already exists
   */
  public Bucket createBucket() {
    return this.storage.create(BucketInfo.newBuilder(getBucketName()).build());
  }

  /**
   * Returns the {@link Bucket} associated with the resource.
   *
   * @return the bucket if it exists, or null otherwise
   */
  public Bucket getBucket() {
    return this.storage.get(this.location.getBucketName());
  }

  /**
   * Checks for the existence of the {@link Bucket} associated with the resource.
   *
   * @return true if the bucket exists
   */
  public boolean bucketExists() {
    return getBucket() != null;
  }

  private Blob throwExceptionForNullBlob(Blob blob) throws IOException {
    if (blob == null) {
      throw new FileNotFoundException("The blob was not found: " + getURI());
    }
    return blob;
  }

  @Override
  @NonNull
  public File getFile() {
    throw new UnsupportedOperationException(
        getDescription() + " cannot be resolved to absolute file path");
  }

  @Override
  public long contentLength() throws IOException {
    return throwExceptionForNullBlob(getBlob()).getSize();
  }

  @Override
  public long lastModified() throws IOException {
    return throwExceptionForNullBlob(getBlob()).getUpdateTime();
  }

  /**
   * Creates a {@link GoogleStorageResource} handle that is relative to this one. It inherits {@code
   * autoCreateFiles} from this object. Note that it does not actually create the blob.
   *
   * <p>Note that this method does not actually create the blob.
   *
   * @param relativePath the URL to a Google Cloud Storage file
   * @return the {@link GoogleStorageResource} handle for the relative path
   * @throws StorageException if an issue occurs creating the relative GoogleStorageResource
   */
  @Override
  @NonNull
  public GoogleStorageResource createRelative(String relativePath) {
    return new GoogleStorageResource(
        this.storage, getURI().resolve(relativePath).toString(), this.autoCreateFiles);
  }

  @Override
  public String getFilename() {
    return isBucket() ? getBucketName() : getBlobName();
  }

  @Override
  @NonNull
  public String getDescription() {
    return getURI().toString();
  }

  @Override
  @NonNull
  public InputStream getInputStream() throws IOException {
    if (isBucket()) {
      throw new IllegalStateException(
          "Cannot open an input stream to a bucket: '" + getURI() + "'");
    } else {
      return Channels.newInputStream(throwExceptionForNullBlob(getBlob()).reader());
    }
  }

  @Override
  public boolean isWritable() {
    return !isBucket() && (this.autoCreateFiles || exists());
  }

  /**
   * Returns the output stream for a Google Cloud Storage file.
   *
   * @return the object's output stream or {@code null} if the object doesn't exist and cannot be
   *     created
   * @throws IOException if an issue occurs getting the OutputStream
   */
  @Override
  @NonNull
  public OutputStream getOutputStream() throws IOException {
    if (isBucket()) {
      throw new IllegalStateException(
          "Cannot open an output stream to a bucket: '" + getURI() + "'");
    }

    Blob blob = getBlob();

    if ((blob == null || !blob.exists()) && !this.autoCreateFiles) {
      throw new FileNotFoundException("The blob was not found: " + getURI());
    }

    return Channels.newOutputStream(this.storage.writer(BlobInfo.newBuilder(getBlobId()).build()));
  }

  /**
   * @return the blob name of the Google Storage Resource; null if the resource is a bucket
   */
  public String getBlobName() {
    return this.location.getBlobName();
  }

  /**
   * @return true if the resource is a bucket; false otherwise
   */
  public boolean isBucket() {
    return this.location.isBucket();
  }

  /**
   * @return the bucket name of the Google Storage Resource
   */
  public String getBucketName() {
    return this.location.getBucketName();
  }

  /**
   * @return the {@link GoogleStorageLocation} describing the location of the resource in GCS
   * @since 1.2
   */
  public GoogleStorageLocation getGoogleStorageLocation() {
    return this.location;
  }

  private BlobId getBlobId() {
    if (isBucket()) {
      throw new IllegalStateException(
          "No blob id specified in the location: '"
              + getURI()
              + "', and the operation is not allowed on buckets.");
    }
    return BlobId.of(getBucketName(), getBlobName());
  }
}
