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
package com.android.tools.idea.gradle.project.sync.compatibility.version;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link IdeVersionReader}.
 */
public class IdeVersionReaderTest extends AndroidGradleTestCase {
  private IdeComponents myIdeComponents;
  private IdeVersionReader myVersionReader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myVersionReader = new IdeVersionReader("Android Studio");
    myIdeComponents = new IdeComponents(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testAppliesToWithAndroidStudio() {
    ApplicationInfo applicationInfo = replaceApplicationInfo();

    String name = "Android Studio";
    when(applicationInfo.getVersionName()).thenReturn(name);

    assertTrue(myVersionReader.appliesTo(mock(Module.class)));
  }

  public void testAppliesToWithIdea() {
    ApplicationInfo applicationInfo = replaceApplicationInfo();

    String name = "Idea";
    when(applicationInfo.getVersionName()).thenReturn(name);

    assertFalse(myVersionReader.appliesTo(mock(Module.class)));
  }

  public void testGetComponentVersion() {
    ApplicationInfo applicationInfo = replaceApplicationInfo();

    String version = "2.0";
    when(applicationInfo.getStrictVersion()).thenReturn(version);

    assertEquals(version, myVersionReader.getComponentVersion(mock(Module.class)));

    verify(applicationInfo).getStrictVersion();
  }

  public void testIsProjectLevel() {
    assertTrue(myVersionReader.isProjectLevel());
  }

  public void testGetComponentName() {
    ApplicationInfo applicationInfo = replaceApplicationInfo();

    String name = "Android Studio";
    when(applicationInfo.getVersionName()).thenReturn(name);

    assertEquals(name, myVersionReader.getComponentName());

    verify(applicationInfo).getVersionName();
  }

  @NotNull
  private ApplicationInfo replaceApplicationInfo() {
    return myIdeComponents.mockService(ApplicationInfo.class);
  }
}