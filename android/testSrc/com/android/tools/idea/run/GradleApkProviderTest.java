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
import com.android.tools.idea.templates.AndroidGradleArtifactsTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link GradleApkProvider}.
 */
@RunWith(Parameterized.class)
public class GradleApkProviderTest extends AndroidGradleArtifactsTestCase {
  @Parameterized.Parameter
  public boolean myLoadAllTestArtifacts;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      { false }, { true }
    });
  }

  @Rule public TestName testName = new TestName();

  @Override
  protected boolean loadAllTestArtifacts() {
    return myLoadAllTestArtifacts;
  }

  @Override
  public String getName() {
    return testName.getMethodName();
  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
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

  @Test
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
      if (modelVersion.compareIgnoringQualifiers("2.2.0") < 0) {
        assertThat(path).endsWith(getName() + "-debug-androidTest-unaligned.apk");
      }
      else {
        assertThat(path).endsWith(getName() + "-debug-androidTest.apk");
      }
    }
  }
}
