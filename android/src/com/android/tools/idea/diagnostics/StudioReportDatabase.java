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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.diagnostics.report.DiagnosticReport;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class StudioReportDatabase {
  private final Object myDbLock = new Object();
  @GuardedBy("myDbLock")
  private final Path myDb;

  public StudioReportDatabase(@NotNull File databaseFile) {
    this(databaseFile.toPath());
  }

  public StudioReportDatabase(@NotNull Path databasePath) {
    myDb = databasePath;
  }

  @NotNull
  public List<DiagnosticReport> reapReports() {
    List<DiagnosticReport> result;

    synchronized (myDbLock) {
      try {
        result = getReports();
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

  @NotNull
  public List<DiagnosticReport> getReports() throws IOException {
    synchronized (myDbLock) {
      if (!Files.exists(myDb) || Files.size(myDb) == 0L) {
        return ImmutableList.of();
      }
      try (Reader reader = new InputStreamReader(new FileInputStream(myDb.toFile()), UTF_8)) {
        return DiagnosticReport.Companion.readDiagnosticReports(reader);
      }
    }
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
