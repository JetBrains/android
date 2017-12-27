/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.tools.idea.npw.project.AndroidPackageUtils;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.Lists;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.mockito.Mockito;

import java.io.File;

import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;
import static com.intellij.openapi.module.ModuleUtilCore.getModuleDirPath;

/**
 * Tests for {@link AndroidPackageUtils}.
 */
public final class AndroidPackageUtilsTest extends AndroidGradleTestCase {

  public void testGetPackageForPath() throws Exception {
    loadProject(PROJECT_WITH_APPAND_LIB);

    File javaSrcDir = new File(getModuleDirPath(myAndroidFacet.getModule()), "src/main/java");
    AndroidProjectPaths androidProjectPaths = Mockito.mock(AndroidProjectPaths.class);
    Mockito.when(androidProjectPaths.getSrcDirectory(null)).thenReturn(javaSrcDir);

    AndroidSourceSet sourceSet = new AndroidSourceSet("main", androidProjectPaths);
    String defaultPackage = getModel().getApplicationId();

    // Anything inside the Java src directory should return the "local package"
    assertEquals(defaultPackage, getPackageForPath(sourceSet, "app/src/main/java/com/example/projectwithappandlib/app"));
    assertEquals("com.example.projectwithappandlib", getPackageForPath(sourceSet, "app/src/main/java/com/example/projectwithappandlib"));
    assertEquals("com.example", getPackageForPath(sourceSet, "app/src/main/java/com/example"));
    assertEquals("com", getPackageForPath(sourceSet, "app/src/main/java/com"));

    // Anything outside the Java src directory should return the default package
    assertEquals(defaultPackage, getPackageForPath(sourceSet, "app/src/main/java"));
    assertEquals(defaultPackage, getPackageForPath(sourceSet, "app/src/main"));
    assertEquals(defaultPackage, getPackageForPath(sourceSet, "app/src"));
    assertEquals(defaultPackage, getPackageForPath(sourceSet, "app"));
    assertEquals(defaultPackage, getPackageForPath(sourceSet, ""));
    assertEquals(defaultPackage, getPackageForPath(sourceSet, "app/src/main/res"));
    assertEquals(defaultPackage, getPackageForPath(sourceSet, "app/src/main/res/layout"));
  }

  private String getPackageForPath(AndroidSourceSet androidSourceSet, String targetDirPath) {
    LocalFileSystem fs = LocalFileSystem.getInstance();
    VirtualFile targetDirectory = fs.refreshAndFindFileByPath(getProject().getBasePath()).findFileByRelativePath(targetDirPath);

    return AndroidPackageUtils.getPackageForPath(myAndroidFacet, Lists.newArrayList(androidSourceSet), targetDirectory);
  }
}
