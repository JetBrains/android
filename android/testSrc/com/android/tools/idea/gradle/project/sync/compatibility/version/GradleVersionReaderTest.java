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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link GradleVersionReader}.
 */
public class GradleVersionReaderTest extends AndroidGradleTestCase {
  private GradleVersions myOriginalGradleVersions;
  private GradleVersionReader myVersionReader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myVersionReader = new GradleVersionReader();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myOriginalGradleVersions != null) {
        IdeComponents.replaceService(GradleVersions.class, myOriginalGradleVersions);
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testAppliesToWithGradleModule() throws Exception {
    loadSimpleApplication();

    Module appModule = myModules.getAppModule();
    assertTrue(myVersionReader.appliesTo(appModule));
  }

  public void testAppliesToWithNonGradleModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);

    Module topLevelModule = myModules.getModule(getProject().getName());
    assertFalse(myVersionReader.appliesTo(topLevelModule));
  }

  public void testGetComponentVersion() throws Exception {
    loadSimpleApplication();

    Module appModule = myModules.getAppModule();

    myOriginalGradleVersions = GradleVersions.getInstance();
    GradleVersions gradleVersions = IdeComponents.replaceServiceWithMock(GradleVersions.class);

    Project project = getProject();
    GradleVersion gradleVersion = GradleVersion.parse("2.14.1");
    when(gradleVersions.getGradleVersion(project)).thenReturn(gradleVersion);

    String actualGradleVersion = myVersionReader.getComponentVersion(appModule);
    assertEquals(gradleVersion.toString(), actualGradleVersion);

    verify(gradleVersions).getGradleVersion(project);
  }

  public void testIsProjectLevel() {
    assertTrue(myVersionReader.isProjectLevel());
  }

  public void testGetName() {
    assertEquals("Gradle", myVersionReader.getComponentName());
  }
}