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
package com.android.tools.idea.diagnostics;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StudioReportDatabase {
  private final Object myDbLock = new Object();
  @GuardedBy("myDbLock")
  private final Path myDb;
  private final static long MAX_SUPPORTED_FORMAT_VERSION = 1;

  public StudioReportDatabase(@NotNull File databaseFile) {
    myDb = databaseFile.toPath();
  }

  public void appendPerformanceThreadDump(@NotNull Path threadDumpPath, String description) throws IOException {
    try (StringWriter sw = new StringWriter()) {
      try (JsonWriter jsonWriter = new JsonWriter(sw)) {
        jsonWriter.setIndent("  ");
        jsonWriter.beginObject();
        jsonWriter.name("formatVersion").value(1);
        jsonWriter.name("type").value("PerformanceThreadDump");
        jsonWriter.name("threadDumpPath").value(threadDumpPath.toString());
        if (description != null) {
          jsonWriter.name("description").value(description);
        }
        jsonWriter.endObject();
      }
      String content = sw.toString() + "\n";
      synchronized (myDbLock) {
        Files.write(myDb, content.getBytes(Charsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      }
    }
  }

  public void appendHistogram(@NotNull Path threadDumpPath, @NotNull Path histogramPath, @Nullable String description) throws IOException {
    try (StringWriter sw = new StringWriter()) {
      try (JsonWriter jsonWriter = new JsonWriter(sw)) {
        jsonWriter.setIndent("  ");
        jsonWriter.beginObject();
        jsonWriter.name("formatVersion").value(1);
        jsonWriter.name("type").value("Histogram");
        jsonWriter.name("threadDumpPath").value(threadDumpPath.toString());
        jsonWriter.name("histogramPath").value(histogramPath.toString());
        if (description != null) {
          jsonWriter.name("description").value(description);
        }
        jsonWriter.endObject();
      }
      String content = sw.toString() + "\n";
      synchronized (myDbLock) {
        Files.write(myDb, content.getBytes(Charsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      }
    }
  }

  @NotNull
  public List<StudioReportDetails> reapReportDetails() {
    List<StudioReportDetails> result = new ArrayList<>();

    synchronized (myDbLock) {
      try (JsonReader reader = new JsonReader(new FileReader(myDb.toFile()))) {
        // setLenient = true, as json objects are adjacent to each other
        reader.setLenient(true);
        while (reader.hasNext() && reader.peek() != JsonToken.END_DOCUMENT) {
          reader.beginObject();
          String type = null;
          Path threadDumpPath = null;
          Path histogramPath = null;
          String description = null;
          long version = 0;
          while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
              case "type":
                type = reader.nextString();
                break;
              case "threadDumpPath":
                threadDumpPath = fixDirectoryPathAndCheckIfReadable(Paths.get(reader.nextString()));
                break;
              case "histogramPath":
                histogramPath = fixDirectoryPathAndCheckIfReadable(Paths.get(reader.nextString()));
                break;
              case "description":
                description = reader.nextString();
                break;
              case "formatVersion":
                version = reader.nextLong();
                break;
              default:
                // Ignore unknown fields.
                reader.skipValue();
            }
          }
          reader.endObject();
          if (version != 0 && version <= MAX_SUPPORTED_FORMAT_VERSION && type != null &&
              (histogramPath != null || threadDumpPath != null)) {
            result.add(new StudioReportDetails(type, threadDumpPath, histogramPath, description));
          }
        }
      }
      catch (IOException | IllegalStateException e) {
        result = ImmutableList.of();
      }
      try {
        Files.write(myDb, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
      } catch (IOException e) {
        result = ImmutableList.of();
      }
    }

    return result;
  }

  /**
   * Performance reports are moved to a different directory once UI is responsive again (path contains duration
   * of the freeze). If the file pointed by {@code path} doesn't exist, it checks if it exists under such directory.
   * @returns Path where such report exists, {@code null} otherwise
   */
  @Nullable
  private static Path fixDirectoryPathAndCheckIfReadable(@NotNull Path path) {
    if (Files.isReadable(path)) {
      return path;
    }

    Path directory = path.getParent();
    try {
      final String prefix = directory.getFileName() + "-";
      try (DirectoryStream<Path> paths = Files.newDirectoryStream(
        directory.getParent(),
        entry -> entry.getFileName().toString().startsWith(prefix))) {
        Iterator<Path> iterator = paths.iterator();
        if (!iterator.hasNext()) {
          return null;
        }
        Path newDirectory = iterator.next();
        Path newFile = newDirectory.resolve(path.getFileName());
        return Files.isReadable(newFile) ? newFile : null;
      }
    }
    catch (IOException e) {
      return null;
    }
  }
}
