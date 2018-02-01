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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.android.instantapps.sdk.api.Sdk;
import org.jetbrains.android.AndroidTestCase;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import java.io.File;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;

public class InstantAppSdksTest {
  private @Spy InstantAppSdks instantAppSdks;
  private @Mock Sdk mockLibSdk;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testGetSdkLibrary() {
    // This loads a stub JAR implementing the Sdk SPI
    doReturn(new File(AndroidTestCase.getTestDataPath() + "/instantapps")).when(instantAppSdks)
      .getInstantAppSdk(false);
    Sdk loadedSdk = instantAppSdks.loadLibrary();
    assertThat(loadedSdk).isNotNull();
    assertThat(loadedSdk.getLibraryVersion()).isEqualTo("THIS_IS_A_FAKE_LIBRARY");
  }

  @Test
  public void testShouldUseSdkLibraryToRunIsFalseIfLibraryNotPresent() {
    StudioFlags.RUNDEBUG_USE_AIA_SDK_LIBRARY.override(true);
    doReturn(null).when(instantAppSdks).loadLibrary();

    assertThat(instantAppSdks.shouldUseSdkLibraryToRun()).isFalse();
  }

  @Test
  public void testShouldUseSdkLibraryToRunIsFalseIfStudioFlagFalse() {
    StudioFlags.RUNDEBUG_USE_AIA_SDK_LIBRARY.override(false);
    doReturn(mockLibSdk).when(instantAppSdks).loadLibrary();

    assertThat(instantAppSdks.shouldUseSdkLibraryToRun()).isFalse();
  }

  @Test
  public void testShouldUseSdkLibraryToRunIsTrueIfLibraryPresentAndFlagTrue() {
    StudioFlags.RUNDEBUG_USE_AIA_SDK_LIBRARY.override(true);
    doReturn(mockLibSdk).when(instantAppSdks).loadLibrary();

    assertThat(instantAppSdks.shouldUseSdkLibraryToRun()).isTrue();
  }
}