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

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.diagnostics.report.DiagnosticReport;
import com.android.tools.idea.diagnostics.report.DiagnosticReportProperties;
import com.android.tools.idea.diagnostics.report.FreezeReport;
import com.android.tools.idea.diagnostics.report.HistogramReport;
import com.android.tools.idea.diagnostics.report.MemoryReportReason;
import com.android.tools.idea.diagnostics.report.PerformanceThreadDumpReport;
import com.google.common.base.Charsets;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.hamcrest.CoreMatchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StudioReportDatabaseTest {
  @Rule
  public TemporaryFolder myTestFolder = new TemporaryFolder();

  private File databaseFile;
  private StudioReportDatabase db;

  @Before
  public void setup() {
    databaseFile = new File(myTestFolder.getRoot(), "threads.dmp");
    db = new StudioReportDatabase(databaseFile);
  }

  @Test
  public void testEmptyDatabase() throws IOException {
    List<DiagnosticReport> reports = db.getReports();
    assertEquals(0, reports.size());
    db.appendReport(new PerformanceThreadDumpReport(createTempFileWithThreadDump("1"), "test"));
    reports = db.reapReports();
    reports = db.getReports();
    assertEquals(0, reports.size());
    reports = db.reapReports();
    assertEquals(0, reports.size());
  }

  @Test
  public void testParser() throws IOException {
    Path t1 = createTempFileWithThreadDump("1");
    Path t2 = createTempFileWithThreadDump("1");

    db.appendReport(new PerformanceThreadDumpReport(t1, "test"));
    db.appendReport(new PerformanceThreadDumpReport(t2, "test"));

    List<DiagnosticReport> reports = db.reapReports();
    List<Path> paths = ContainerUtil.map(reports, r -> ((PerformanceThreadDumpReport)r).getThreadDumpPath());
    assertThat(paths, hasItems(t1, t2));

    reports = db.reapReports();
    assertTrue(reports.isEmpty());
  }

  @Test
  public void testDifferentTypes() throws IOException {
    Path h1 = createTempFileWithThreadDump("H1");
    Path t1 = createTempFileWithThreadDump("T1");

    Path t2 = createTempFileWithThreadDump("T2");

    Path h3 = createTempFileWithThreadDump("H3");
    Path t3 = createTempFileWithThreadDump("T3");

    db.appendReport(new HistogramReport(t1, h1, MemoryReportReason.LowMemory, "test"));
    db.appendReport(new PerformanceThreadDumpReport(t2, "test"));
    db.appendReport(new HistogramReport(t3, h3, MemoryReportReason.LowMemory, "test"));

    List<DiagnosticReport> reports = db.reapReports();

    assertEquals(3, reports.size());
    assertEquals(2, reports.stream().filter(r -> r.getType().equals("Histogram")).count());
    assertEquals(1, reports.stream().filter(r -> r.getType().equals("PerformanceThreadDump")).count());
  }

  @Test
  public void testHistogramContent() throws IOException {
    Path h1 = createTempFileWithThreadDump("H1");
    Path t1 = createTempFileWithThreadDump("T1");

    db.appendReport(new HistogramReport(t1, h1, MemoryReportReason.LowMemory, "Histogram description"));

    DiagnosticReport details = db.reapReports().get(0);

    assertThat(details, CoreMatchers.is(instanceOf(HistogramReport.class)));
    HistogramReport report = (HistogramReport) details;
    assertEquals("Histogram", details.getType());
    assertEquals("LowMemory", report.getReason().toString());
    assertEquals(t1, report.getThreadDumpPath());
    assertEquals(h1, report.getHistogramPath());
    assertEquals("Histogram description", report.getDescription());
  }

  @Test
  public void testFreezeContent() throws IOException {
    Path threadDump = createTempFileWithThreadDump("T1");
    Path actions = createTempFileWithThreadDump("Actions");
    Path memoryUse = createTempFileWithThreadDump("Memory use");
    Path profile = createTempFileWithThreadDump("Profile");
    Map<String, Path> paths = new TreeMap<>();
    paths.put("actionsDiagnostics", actions);
    paths.put("memoryUseDiagnostics", memoryUse);
    paths.put("profileDiagnostics", profile);
    db.appendReport(new FreezeReport(threadDump, paths, new HashMap<>(), false, 20L, "Freeze report"));
    db.appendReport(new FreezeReport(threadDump, paths, new HashMap<>(), true, null, "Freeze report"));

    List<DiagnosticReport> diagnosticReports = db.reapReports();
    FreezeReport report = (FreezeReport) diagnosticReports.get(0);
    assertEquals("Freeze", report.getType());
    assertEquals(threadDump, report.getThreadDumpPath());
    assertEquals(paths, report.getReportParts());
    assertEquals(20, report.getTotalDuration().longValue());
    assertFalse(report.getTimedOut());
    assertEquals("Freeze report", report.getDescription());

    assertTrue(((FreezeReport) diagnosticReports.get(1)).getTimedOut());
    assertNull(((FreezeReport) diagnosticReports.get(1)).getTotalDuration());
  }

  @Test
  public void testEmptyFreezeReport() throws IOException {
    db.appendReport(new FreezeReport(null, new TreeMap<>(), new TreeMap<>(), false, null, null));
    FreezeReport report = (FreezeReport) db.reapReports().get(0);

    assertNull(report.getThreadDumpPath());
    assertEquals(0, report.getReportParts().size());
    assertNull(report.getTotalDuration());
    assertNull(report.getDescription());
  }

  @Test
  public void testPerformanceThreadDumpContent() throws IOException {
    Path t1 = createTempFileWithThreadDump("T1");

    db.appendReport(new PerformanceThreadDumpReport(t1, "Performance thread dump description"));

    DiagnosticReport details = db.reapReports().get(0);

    assertEquals("PerformanceThreadDump", details.getType());
    assertEquals(t1, ((PerformanceThreadDumpReport) details).getThreadDumpPath());
    assertEquals("Performance thread dump description", ((PerformanceThreadDumpReport) details).getDescription());
  }

  @Test
  public void testCorruptedDatabaseFile() throws IOException {
    Path t1 = createTempFileWithThreadDump("T1");
    db.appendReport(new PerformanceThreadDumpReport(t1, "Performance thread dump description"));
    Files.write(databaseFile.toPath(), "Corrupted json".getBytes(Charsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
    List<DiagnosticReport> details = db.reapReports();

    // If the db file contains corrupted of malformed json, return no reports.
    assertEquals(0, details.size());

    // Test that database works even after its file gets corrupted.
    Path t2 = createTempFileWithThreadDump("T2");
    db.appendReport(new PerformanceThreadDumpReport(t2, "Performance thread dump description"));
    details = db.reapReports();

    assertEquals(1, details.size());
    assertEquals(t2, ((PerformanceThreadDumpReport) details.get(0)).getThreadDumpPath());
  }

  @Test
  public void testDiagnosticProperties() throws Exception {
    Path t1 = createTempFileWithThreadDump("T1");
    Path t2 = createTempFileWithThreadDump("T2");
    long time = new SimpleDateFormat("MM/dd/yyyy h:mm a, z", Locale.US).parse("07/10/2018 4:05 PM, PDT").getTime();
    DiagnosticReportProperties properties = new DiagnosticReportProperties(
      1000, // uptime
      time, // report time
      "testSessionId",
      "1.2.3.4", //studio version
      "9.8.7.6" // kotlin version
    );
    db.appendReport(new HistogramReport(t1, t2, MemoryReportReason.LowMemory, "", properties));
    List<DiagnosticReport> reports = db.reapReports();
    HistogramReport report = (HistogramReport) reports.get(0);
    assertNotSame(properties, report.getProperties());
    assertEquals(properties, report.getProperties());
  }

  @NotNull
  private Path createTempFileWithThreadDump(@NotNull String contents) throws IOException {
    File file = myTestFolder.newFile();
    Files.write(file.toPath(), contents.getBytes(Charsets.UTF_8), StandardOpenOption.CREATE);
    return file.toPath();
  }
}
