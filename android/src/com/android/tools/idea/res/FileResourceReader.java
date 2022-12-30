/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.res;

import static com.intellij.util.io.URLUtil.FILE_PROTOCOL;
import static com.intellij.util.io.URLUtil.JAR_PROTOCOL;
import static com.intellij.util.io.URLUtil.JAR_SEPARATOR;

import com.android.ide.common.resources.ProtoXmlPullParser;
import com.android.ide.common.util.PathString;
import com.android.tools.idea.apk.viewer.ApkFileSystem;
import com.android.utils.XmlUtils;
import com.android.zipflinger.ZipMap;
import com.android.zipflinger.ZipRepo;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Methods for working with Android file resources.
 */
public final class FileResourceReader {
  /**
   * Cache to store {@link ZipMap} objects created from zip files.
   * Keys are the full path to the zip file from which the ZipMap is created.
   */
  private static final LoadingCache<String, ZipMap> sZipCache = CacheBuilder.newBuilder()
    .maximumSize(10)
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .build(new CacheLoader<String, ZipMap>() {
      @Override
      public ZipMap load(@NotNull String path) throws IOException {
        return ZipMap.from(Paths.get(path));
      }
    });

  /**
   * Reads and returns the contents of a resource. The resource path can point either to a file on
   * disk, or to a ZIP file entry. In the latter case the URI of the resource path contains a path
   * pointing to the ZIP file and has "apk" scheme. The path part of the resource path corresponds
   * to the path of the ZIP entry.
   *
   * @param resourcePath the path to a file or a zip entry
   * @return the contents of the file resource
   * @throws FileNotFoundException if the resource doesn't exist
   * @throws IOException in case of an I/O error
   */
  @NotNull
  public static byte[] readBytes(@NotNull PathString resourcePath) throws IOException {
    String scheme = resourcePath.getFilesystemUri().getScheme();
    switch (scheme) {
      case FILE_PROTOCOL:
        return readFileBytes(resourcePath.getRawPath());

      case ApkFileSystem.PROTOCOL:
      case JAR_PROTOCOL: {
        String path = resourcePath.getRawPath();
        int separatorPos = path.indexOf(JAR_SEPARATOR);
        int separatorEnd = separatorPos + JAR_SEPARATOR.length();
        if (separatorPos <= 0 || separatorEnd == path.length()) {
          throw new IllegalArgumentException("Invalid path in \"" + resourcePath + "\"");
        }
        return readZipEntryBytes(path.substring(0, separatorPos), path.substring(separatorEnd));
      }

      default:
        throw new IllegalArgumentException("Unknown schema in \"" + resourcePath + "\"");
    }
  }

  /**
   * Reads and returns the contents of a resource. The resource path can point either to a file on
   * disk, or to a ZIP file entry. In the latter case the resource path has the following format:
   * "apk:<i>path_to_zip_file</i>!/<i>path_to_zip_entry</i>.
   *
   * @param resourcePath the path to a file or a zip entry
   * @return the contents of the file resource
   * @throws FileNotFoundException if the resource doesn't exist
   * @throws IOException in case of an I/O error
   */
  @NotNull
  public static byte[] readBytes(@NotNull String resourcePath) throws IOException {
    if (resourcePath.startsWith("apk:") || resourcePath.startsWith("jar:")) {
      int prefixLength = "apk:".length(); // "jar:" has the same length as "apk:".
      if (resourcePath.startsWith("//", prefixLength)) {
        prefixLength += "//".length();
      }
      int separatorPos = resourcePath.lastIndexOf(JAR_SEPARATOR);
      if (separatorPos < prefixLength) {
        throw new IllegalArgumentException("Invalid resource path \"" + resourcePath + "\"");
      }
      return readZipEntryBytes(resourcePath.substring(prefixLength, separatorPos),
                               resourcePath.substring(separatorPos + JAR_SEPARATOR.length()));
    }

    if (resourcePath.startsWith("file:")) {
      int prefixLength = "file:".length();
      if (resourcePath.startsWith("//", prefixLength)) {
        prefixLength += "//".length();
      }
      resourcePath = resourcePath.substring(prefixLength);
    }
    return readFileBytes(resourcePath);
  }

  @NotNull
  private static byte[] readFileBytes(String filePath) throws IOException {
    try (FileInputStream fileStream = new FileInputStream(filePath)) {
      ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
      ByteStreams.copy(fileStream, byteOutputStream);
      return byteOutputStream.toByteArray();
    }
  }

  @NotNull
  private static byte[] readZipEntryBytes(String zipPath, String zipEntryPath) throws IOException {
    // Cache ZipMap as its creation is time consuming.
    ZipMap zipMap;
    try {
      zipMap = sZipCache.get(zipPath);
    }
    catch (ExecutionException e) {
      Throwable nested = e.getCause();
      if (nested instanceof IOException) {
        throw (IOException)nested;
      }
      if (nested instanceof RuntimeException) {
        throw (RuntimeException)nested;
      }
      if (nested instanceof Error) {
        throw (Error)nested;
      }
      throw new UncheckedExecutionException(nested);
    }

    try (ZipRepo zipRepo = new ZipRepo(zipMap)) {
      ByteBuffer entryContent = zipRepo.getContent(zipEntryPath);
      byte[] bytes = new byte[entryContent.remaining()];
      entryContent.get(bytes);
      return bytes;
    }
  }

  /**
   * Creates a {@link XmlPullParser} for the given XML file resource. The file may contain
   * either regular or proto XML.
   *
   * @param resourcePath the path to a file or a zip entry
   * @return the parser for the resource, or null if the resource does not exist
   * @throws IOException in case of an I/O error
   */
  @Nullable
  public static XmlPullParser createXmlPullParser(@NotNull PathString resourcePath) throws IOException {
    try {
      byte[] contents = readBytes(resourcePath);
      return createXmlPullParser(contents);
    }
    catch (FileNotFoundException e) {
      return null;
    }
  }

  /**
   * Creates a {@link XmlPullParser} for the given XML file resource. The file may contain
   * either regular or proto XML.
   *
   * @param resourceFile the resource file
   * @return the parser for the resource, or null if the resource does not exist
   * @throws IOException in case of an I/O error
   */
  @Nullable
  public static XmlPullParser createXmlPullParser(@NotNull VirtualFile resourceFile) throws IOException {
    try {
      byte[] contents = resourceFile.contentsToByteArray();
      return createXmlPullParser(contents);
    }
    catch (FileNotFoundException e) {
      return null;
    }
  }

  /**
   * Creates a {@link XmlPullParser} for the contents of an XML file resource. The contains
   * may contain either regular or proto XML.
   *
   * @param contents the contents of a file resource
   * @return the parser for the resource
   */
  @NotNull
  public static XmlPullParser createXmlPullParser(@NotNull byte[] contents) {
    try {
      // Instantiate an XML pull parser based on the contents of the file.
      XmlPullParser parser;
      if (XmlUtils.isProtoXml(contents)) {
        parser = new ProtoXmlPullParser(); // Parser for proto XML used in AARs.
      }
      else {
        parser = new KXmlParser(); // Parser for regular text XML.
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      }
      parser.setInput(new ByteArrayInputStream(contents), null);
      return parser;
    } catch (XmlPullParserException e) {
      throw new Error("Internal error", e); // Should not happen unless there is a bug.
    }
  }

  /** Do not instantiate - all methods are static. */
  private FileResourceReader() {}
}
