/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.templates.AndroidGradleTestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link GradleApkProvider}.
 */
public class GradleApkProviderTest extends AndroidGradleTestCase {
  public void testGetApks() throws Exception {
    loadProject("projects/runConfig/activity");
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), false);
    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    assertThat(apks).hasSize(1);

    ApkInfo apk = getFirstItem(apks);
    assertNotNull(apk);
    assertEquals("from.gradle.debug", apk.getApplicationId());
    String path = apk.getFile().getPath();
    assertThat(path).endsWith(getName() + "-debug.apk");
  }

  public void testGetApksForTest() throws Exception {
    loadProject("projects/runConfig/activity");
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), true);

    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    assertThat(apks).hasSize(2);

    // Sort the APKs to keep test consistent.
    List<ApkInfo> apkList = new ArrayList<>(apks);
    Collections.sort(apkList, (a, b) -> a.getApplicationId().compareTo(b.getApplicationId()));
    ApkInfo mainApk = apkList.get(0);
    ApkInfo testApk = apkList.get(1);

    assertEquals("from.gradle.debug", mainApk.getApplicationId());
    String path = mainApk.getFile().getPath();
    assertThat(path).endsWith(getName() + "-debug.apk");

    assertEquals(testApk.getApplicationId(), "from.gradle.debug.test");
    path = testApk.getFile().getPath();

    GradleVersion modelVersion = getModel().getModelVersion();
    if (modelVersion != null) {
      if (modelVersion.compareIgnoringQualifiers("2.2.0") < 0
          // Packaging reverted in alpha4?
          || modelVersion.compareTo("2.2.0-alpha4") >= 0) {
        assertThat(path).endsWith(getName() + "-debug-androidTest-unaligned.apk");
      }
      else {
        assertThat(path).endsWith(getName() + "-debug-androidTest.apk");
      }
    }
  }

  public void testGetApksForTestOnlyModule() throws Exception {
    loadProject("projects/testOnlyModule", "test");
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, new GradleApplicationIdProvider(myAndroidFacet), true);

    Collection<ApkInfo> apks = provider.getApks(mock(IDevice.class));
    ApkInfo testApk = apks.stream().filter(a -> a.getApplicationId().equals("com.example.android.app.test"))
      .findFirst().orElse(null);
    assertThat(testApk).isNotNull();

    GradleVersion modelVersion = getModel().getModelVersion();
    if (modelVersion != null) {
      if (modelVersion.compareIgnoringQualifiers("2.2.0") < 0) {
        // only the test-module apk should be there
        assertThat(apks).hasSize(1);
      } else {
        // both test-module apk and main apk should be there
        assertThat(apks).hasSize(2);
        ApkInfo mainApk = apks.stream().filter(a -> a.getApplicationId().equals("com.example.android.app"))
          .findFirst().orElse(null);

        assertThat(mainApk).isNotNull();
      }
    }
  }
}
