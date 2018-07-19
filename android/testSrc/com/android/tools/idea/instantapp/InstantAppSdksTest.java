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

import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.IdeComponents;
import com.android.utils.NullLogger;
import com.google.android.instantapps.sdk.api.Sdk;
import com.google.android.instantapps.sdk.api.TelemetryManager.OptInStatus;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.AndroidTestCase;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import java.io.File;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class InstantAppSdksTest extends IdeaTestCase {
  private @Spy InstantAppSdks myInstantAppSdks;
  private @Mock Sdk myMockLibSdk;
  private @Mock ApplicationInfo myApplicationInfo;

  private TestUsageTracker myUsageTracker;
  private AnalyticsSettings myAnalyticsSettings;
  private final VirtualTimeScheduler myVirtualTimeScheduler = new VirtualTimeScheduler();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    when(myApplicationInfo.getFullVersion()).thenReturn("testVersion");
    new IdeComponents(getProject()).replaceApplicationService(ApplicationInfo.class, myApplicationInfo);
    myAnalyticsSettings = new AnalyticsSettings();
    AnalyticsSettings.setInstanceForTest(myAnalyticsSettings);
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

  public void testGetSdkLibrary() {
    installFakeLib();
    Sdk loadedSdk = myInstantAppSdks.loadLibrary();
    assertThat(loadedSdk).isNotNull();
    assertThat(loadedSdk.getLibraryVersion()).isEqualTo("THIS_IS_A_FAKE_LIBRARY");
  }

  public void testGetSdkLibraryPassThroughOptIn() {
    AnalyticsSettings.getInstance(new NullLogger()).setOptedIn(true);
    installFakeLib();
    myAnalyticsSettings.setOptedIn(true);
    Sdk loadedSdk = myInstantAppSdks.loadLibrary();
    assertThat(loadedSdk.getTelemetryManager().getOptInStatus()).isEqualTo(OptInStatus.OPTED_IN);
  }

  public void testGetSdkLibraryPassThroughOptOut() {
    installFakeLib();
    myAnalyticsSettings.setOptedIn(false);
    Sdk loadedSdk = myInstantAppSdks.loadLibrary();
    assertThat(loadedSdk.getTelemetryManager().getOptInStatus()).isEqualTo(OptInStatus.OPTED_OUT);
  }

  public void testShouldUseSdkLibraryToRunIsFalseIfLibraryNotPresent() {
    StudioFlags.RUNDEBUG_USE_AIA_SDK_LIBRARY.override(true);
    doReturn(null).when(myInstantAppSdks).loadLibrary();

    assertThat(myInstantAppSdks.shouldUseSdkLibraryToRun()).isFalse();
  }

  public void testShouldUseSdkLibraryToRunIsFalseIfStudioFlagFalse() {
    StudioFlags.RUNDEBUG_USE_AIA_SDK_LIBRARY.override(false);
    doReturn(myMockLibSdk).when(myInstantAppSdks).loadLibrary();

    assertThat(myInstantAppSdks.shouldUseSdkLibraryToRun()).isFalse();
  }

  public void testShouldUseSdkLibraryToRunIsTrueIfLibraryPresentAndFlagTrue() {
    StudioFlags.RUNDEBUG_USE_AIA_SDK_LIBRARY.override(true);
    doReturn(myMockLibSdk).when(myInstantAppSdks).loadLibrary();

    assertThat(myInstantAppSdks.shouldUseSdkLibraryToRun()).isTrue();
  }

  /** Loads a stub JAR implementing the Sdk SPI. */
  private void installFakeLib() {
    doReturn(new File(AndroidTestCase.getTestDataPath() + "/instantapps")).when(myInstantAppSdks).getInstantAppSdk(false);
  }
}
