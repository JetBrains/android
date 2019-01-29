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
import java.io.Reader;
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

  @NotNull
  public List<DiagnosticReport> reapReportDetails() {
    List<DiagnosticReport> result;

    synchronized (myDbLock) {
      try (Reader reader = new FileReader(myDb.toFile())) {
        result = DiagnosticReport.Companion.readDiagnosticReports(reader);
      }
      catch (Exception e) {
        result = ImmutableList.of();
      }
      try {
        Files.write(myDb, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
      } catch (IOException e) {
        // If there was a problem with deleting the file, don't return any reports to avoid submitting same reports
        // over and over again.
        result = ImmutableList.of();
      }
    }

    return result;
  }

  public void appendReport(DiagnosticReport report) throws IOException {
    try (StringWriter sw = new StringWriter()) {
      report.serializeReport(sw);
      String content = sw.toString() + "\n";
      synchronized (myDbLock) {
        Files.write(myDb, content.getBytes(Charsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      }
    }
  }
}
