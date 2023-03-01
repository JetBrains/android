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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.AnalyticsSettingsData;
import com.android.tools.analytics.crash.CrashReport;
import com.android.tools.idea.diagnostics.crash.ExceptionDataCollection;
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter;
import com.android.tools.idea.diagnostics.report.DiagnosticReport;
import com.android.tools.idea.diagnostics.report.FreezeReport;
import com.android.tools.idea.diagnostics.report.HistogramReport;
import com.intellij.internal.statistic.analytics.StudioCrashDetection;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.testFramework.PlatformLiteFixture;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;

/**
 * Tests for {@link SystemHealthMonitor}.
 */
public class AndroidStudioSystemHealthMonitorTest extends PlatformLiteFixture {

  @Mock private StudioCrashReporter studioCrashReporterMock;
  @Mock private NotificationGroupManager notificationGroupManagerMock;
  @Mock private StudioReportDatabase studioReportDatabaseMock;
  @Mock private ExceptionDataCollection exceptionDataCollectionMock;
  @Mock private NotificationGroup notificationGroupMock;

  private static int TIMEOUT = 3000;

  private AndroidStudioSystemHealthMonitor androidStudioSystemHealthMonitor;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    initApplication();
    getApplication().registerService(StudioCrashReporter.class, studioCrashReporterMock);
    getApplication().registerService(NotificationGroupManager.class, notificationGroupManagerMock);
    getApplication().registerService(ExceptionDataCollection.class, exceptionDataCollectionMock);
    when(notificationGroupManagerMock.getNotificationGroup(any(String.class))).thenReturn(notificationGroupMock);
    androidStudioSystemHealthMonitor = spy(new AndroidStudioSystemHealthMonitor(studioReportDatabaseMock));

    // When this method is invoked more than once (side-effect of running androidStudioSystemHealthMonitor.start())
    // across different tests, we see the initial listener that is registered being preserved. This causes an exception
    // to be thrown.
    doNothing().when(androidStudioSystemHealthMonitor).registerPlatformEventsListener();
  }

  @Test
  public void testCombinationOfReportsSubmitted() throws Exception {
    AnalyticsSettingsData analyticsSettings = new AnalyticsSettingsData();
    analyticsSettings.setOptedIn(true);
    AnalyticsSettings.setInstanceForTest(analyticsSettings);

    FreezeReport freezeReport = mock(FreezeReport.class);
    CrashReport freezeCrashReport = mock(CrashReport.class);
    when(freezeReport.asCrashReport()).thenReturn(freezeCrashReport);
    when(freezeReport.getType()).thenReturn(FreezeReport.REPORT_TYPE);

    HistogramReport histogramReport = mock(HistogramReport.class);
    CrashReport histogramCrashReport = mock(CrashReport.class);
    when(histogramReport.asCrashReport()).thenReturn(histogramCrashReport);
    when(histogramReport.getType()).thenReturn(HistogramReport.REPORT_TYPE);

    List<DiagnosticReport> diagnosticReports = Arrays.asList(freezeReport, histogramReport);
    when(studioReportDatabaseMock.reapReports()).thenReturn(diagnosticReports);

    androidStudioSystemHealthMonitor.startInternal();

    Integer expectedNumberOfReportsSubmitted = 2;
    verify(studioCrashReporterMock, timeout(TIMEOUT).times(expectedNumberOfReportsSubmitted)).submit(any(), eq(true));
    InOrder orderVerifier = inOrder(studioCrashReporterMock);
    orderVerifier.verify(studioCrashReporterMock, timeout(TIMEOUT)).submit(histogramCrashReport, true);
    orderVerifier.verify(studioCrashReporterMock, timeout(TIMEOUT)).submit(freezeCrashReport, true);
  }
  
  @Test
  public void testSubsetOfHistogramReportsSubmitted() throws Exception {
    AnalyticsSettingsData analyticsSettings = new AnalyticsSettingsData();
    analyticsSettings.setOptedIn(true);
    AnalyticsSettings.setInstanceForTest(analyticsSettings);

    List<DiagnosticReport> diagnosticReports = new ArrayList<>();
    CrashReport histogramCrashReport = mock(CrashReport.class);
    when(histogramCrashReport.getType()).thenReturn(HistogramReport.REPORT_TYPE);
    for (int i = 0; i < AndroidStudioSystemHealthMonitor.getMaxHistogramReportsCount() + 5; i++) {
      HistogramReport histogramReport = mock(HistogramReport.class);
      when(histogramReport.asCrashReport()).thenReturn(histogramCrashReport);
      when(histogramReport.getType()).thenReturn(HistogramReport.REPORT_TYPE);
      diagnosticReports.add(histogramReport);
    }
    when(studioReportDatabaseMock.reapReports()).thenReturn(diagnosticReports);

    androidStudioSystemHealthMonitor.startInternal();

    Integer expectedNumberOfReportsSubmitted = AndroidStudioSystemHealthMonitor.getMaxHistogramReportsCount();
    ArgumentCaptor<CrashReport> crashReportArgumentCaptor = ArgumentCaptor.forClass(CrashReport.class);
    verify(studioCrashReporterMock, timeout(TIMEOUT).times(expectedNumberOfReportsSubmitted)).submit(crashReportArgumentCaptor.capture(), eq(true));
    long numHistogramReportsSubmitted = crashReportArgumentCaptor.getAllValues().stream().filter(crashReport -> crashReport.getType().equals(HistogramReport.REPORT_TYPE)).count();
    assertEquals(AndroidStudioSystemHealthMonitor.getMaxHistogramReportsCount().longValue(), numHistogramReportsSubmitted);
  }

}
