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
package com.android.tools.idea.instantapp;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.AnalyticsSettingsData;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.testing.IdeComponents;
import com.google.android.instantapps.sdk.api.Sdk;
import com.google.android.instantapps.sdk.api.TelemetryManager.OptInStatus;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.testFramework.IdeaTestCase;
import java.io.File;
import java.nio.file.Files;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class InstantAppSdksTest extends IdeaTestCase {
  private @Spy InstantAppSdks myInstantAppSdks;
  private @Mock Sdk myMockLibSdk;
  private @Mock ApplicationInfo myApplicationInfo;

  private TestUsageTracker myUsageTracker;
  private final VirtualTimeScheduler myVirtualTimeScheduler = new VirtualTimeScheduler();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    when(myApplicationInfo.getBuild()).thenReturn(BuildNumber.fromString("123.4567.89.0"));
    when(myApplicationInfo.getFullVersion()).thenReturn("testVersion");
    new IdeComponents(getProject()).replaceApplicationService(ApplicationInfo.class, myApplicationInfo);
    AnalyticsSettingsData analyticsSettings = new AnalyticsSettingsData();
    AnalyticsSettings.setInstanceForTest(analyticsSettings);
    myUsageTracker = new TestUsageTracker(myVirtualTimeScheduler);
    UsageTracker.setWriterForTest(myUsageTracker);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      UsageTracker.cleanAfterTesting();
    }
    finally {
      super.tearDown();
    }
  }

  public void testGetSdkLibraryWithObsoleteSdk() throws Exception {
    // In testing, rather than showing a dialog a RuntimeException with the dialog text is thrown
    installSdkWithoutLib();
    assertThrows(RuntimeException.class, "Required Google Play Instant SDK must be updated to run this task. Do you want to update it now?",
                 myInstantAppSdks::loadLibrary
    );
  }

  public void testGetSdkLibraryWithObsoleteSdkAttemptUpgradesFalse() throws Exception {
    installSdkWithoutLib();
    assertThrows(InstantAppSdks.LoadInstantAppSdkException.class, () -> myInstantAppSdks.loadLibrary(false));
  }

  public void testGetSdkLibraryWithNewSdk() {
    installFakeLib();
    Sdk loadedSdk = myInstantAppSdks.loadLibrary(true);
    assertThat(loadedSdk).isNotNull();
    assertThat(loadedSdk.getLibraryVersion()).isEqualTo("THIS_IS_A_FAKE_LIBRARY");
  }

  public void testGetSdkLibraryPassThroughOptIn() {
    AnalyticsSettings.setOptedIn(true);
    installFakeLib();
    AnalyticsSettings.setOptedIn(true);
    Sdk loadedSdk = myInstantAppSdks.loadLibrary();
    assertThat(loadedSdk.getTelemetryManager().getOptInStatus()).isEqualTo(OptInStatus.OPTED_IN);
  }

  public void testGetSdkLibraryPassThroughOptOut() {
    installFakeLib();
    AnalyticsSettings.setOptedIn(false);
    Sdk loadedSdk = myInstantAppSdks.loadLibrary();
    assertThat(loadedSdk.getTelemetryManager().getOptInStatus()).isEqualTo(OptInStatus.OPTED_OUT);
  }

  /** Points the SDK loader at an empty directory. */
  private void installSdkWithoutLib() throws Exception{
    doReturn(Files.createTempDirectory("empty").toFile()).when(myInstantAppSdks).getOrInstallInstantAppSdk();
  }

  /** Points the SDK loader at a stub JAR implementing the Sdk SPI. */
  private void installFakeLib() {
    doReturn(new File(AndroidTestCase.getTestDataPath() + "/instantapps")).when(myInstantAppSdks).getOrInstallInstantAppSdk();
  }
}
