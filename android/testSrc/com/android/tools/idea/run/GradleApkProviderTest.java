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
import com.android.tools.idea.templates.AndroidGradleArtifactsTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.util.*;

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
  public void testGetPackageName() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("GradleApkProviderTest.testGetPackageName temporarily disabled");
      return;
    }

    loadProject("projects/runConfig/activity");
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, false);
    // See testData/Projects/runConfig/activity/build.gradle
    assertEquals("from.gradle.debug", provider.getPackageName());
    // Without a specific test package name from the Gradle file, we just get a test prefix.
    assertEquals("from.gradle.debug.test", provider.getTestPackageName());
  }

  @Test
  public void testGetApks() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("GradleApkProviderTest.testGetApks temporarily disabled");
      return;
    }

    loadProject("projects/runConfig/activity");
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, false);
    IDevice device = Mockito.mock(IDevice.class);
    Collection<ApkInfo> apks = provider.getApks(device);
    assertNotNull(apks);
    assertEquals(1, apks.size());
    ApkInfo apk = apks.iterator().next();
    assertEquals("from.gradle.debug", apk.getApplicationId());
    assertTrue(apk.getFile().getPath().endsWith(getName() + "0-debug.apk"));
  }

  @Test
  public void testGetPackageNameForTest() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("GradleApkProviderTest.testGetPackageNameForTest temporarily disabled");
      return;
    }

    loadProject("projects/runConfig/activity");
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, true);
    // See testData/Projects/runConfig/activity/build.gradle
    assertEquals("from.gradle.debug", provider.getPackageName());
    // Without a specific test package name from the Gradle file, we just get a test prefix.
    assertEquals("from.gradle.debug.test", provider.getTestPackageName());
  }

  @Test
  public void testGetApksForTest() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("GradleApkProviderTest.testGetApksForTest temporarily disabled");
      return;
    }

    loadProject("projects/runConfig/activity");
    GradleApkProvider provider = new GradleApkProvider(myAndroidFacet, true);
    IDevice device = Mockito.mock(IDevice.class);
    Collection<ApkInfo> apks = provider.getApks(device);
    assertNotNull(apks);
    assertEquals(2, apks.size());
    // Sort the apks to keep test consistent.
    List<ApkInfo> apkList = new ArrayList<ApkInfo>(apks);
    Collections.sort(apkList, new Comparator<ApkInfo>() {
      @Override
      public int compare(ApkInfo a, ApkInfo b) {
        return a.getApplicationId().compareTo(b.getApplicationId());
      }
    });
    ApkInfo mainApk = apkList.get(0);
    ApkInfo testApk = apkList.get(1);
    assertEquals("from.gradle.debug", mainApk.getApplicationId());
    assertTrue(mainApk.getFile().getPath().endsWith(getName() + "0-debug.apk"));
    assertEquals(testApk.getApplicationId(), "from.gradle.debug.test");
    assertTrue(testApk.getFile().getPath().endsWith(getName() + "0-debug-androidTest-unaligned.apk"));
  }
}
