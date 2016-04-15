/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.templates.AndroidGradleArtifactsTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

/**
 * Tests for {@link GradleApplicationIdProvider}.
 */
@RunWith(Parameterized.class)
public class GradleApplicationIdProviderTest extends AndroidGradleArtifactsTestCase {
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
    loadProject("projects/runConfig/activity");
    ApplicationIdProvider provider = new GradleApplicationIdProvider(myAndroidFacet);
    // See testData/Projects/runConfig/activity/build.gradle
    assertEquals("from.gradle.debug", provider.getPackageName());
    // Without a specific test package name from the Gradle file, we just get a test prefix.
    assertEquals("from.gradle.debug.test", provider.getTestPackageName());
  }

  @Test
  public void testGetPackageNameForTest() throws Exception {
    loadProject("projects/runConfig/activity");
    ApplicationIdProvider provider = new GradleApplicationIdProvider(myAndroidFacet);
    // See testData/Projects/runConfig/activity/build.gradle
    assertEquals("from.gradle.debug", provider.getPackageName());
    // Without a specific test package name from the Gradle file, we just get a test prefix.
    assertEquals("from.gradle.debug.test", provider.getTestPackageName());
  }
}
