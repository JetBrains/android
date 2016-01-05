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
package com.android.tools.idea.gradle.testing;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.testing.TestArtifactCustomScopeProvider.AndroidTestsScope;
import com.android.tools.idea.gradle.testing.TestArtifactCustomScopeProvider.UnitTestsScope;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.PackageSetBase;

import static com.intellij.openapi.vfs.VfsUtilCore.findRelativeFile;

public class TestArtifactCustomScopeTest extends AndroidGradleTestCase {
  private boolean oldLoadAllTestArtifactsValue;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    oldLoadAllTestArtifactsValue = GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS;
    GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS = true;
    loadProject("guiTests/SimpleApplication", false);
  }

  @Override
  public void tearDown() throws Exception {
    GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS = oldLoadAllTestArtifactsValue;
    super.tearDown();
  }

  public void testUnitTestFileColor() throws Exception {
    VirtualFile file = findRelativeFile("app/src/test/java/google/simpleapplication/UnitTest.java",
                                        getProject().getBaseDir());
    assertNotNull(file);
    UnitTestsScope scope = new UnitTestsScope();
    PackageSetBase packageSet = (PackageSetBase)scope.getValue();
    assertNotNull(packageSet);
    assertTrue(packageSet.contains(file, getProject(), null));
  }

  public void testAndroidTestFileColor() throws Exception {
    VirtualFile file = findRelativeFile("app/src/androidTest/java/google/simpleapplication/ApplicationTest.java",
                                        getProject().getBaseDir());
    assertNotNull(file);
    AndroidTestsScope scope = new AndroidTestsScope();
    PackageSetBase packageSet = (PackageSetBase)scope.getValue();
    assertNotNull(packageSet);
    assertTrue(packageSet.contains(file, getProject(), null));
  }
}
