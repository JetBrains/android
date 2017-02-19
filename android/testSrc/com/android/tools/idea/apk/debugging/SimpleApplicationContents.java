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
package com.android.tools.idea.apk.debugging;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.gradle.util.Projects.findModuleRootFolderPath;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.junit.Assert.assertNotNull;

public final class SimpleApplicationContents {
  @NotNull private static final ApkPackage GOOGLE_PACKAGE = new ApkPackage("google", null);
  @NotNull private static final ApkPackage SIMPLEAPPLICATION_PACKAGE = new ApkPackage("simpleapplication", GOOGLE_PACKAGE);
  @NotNull private static final ApkClass MY_ACTIVITY_CLASS = new ApkClass("MyActivity", SIMPLEAPPLICATION_PACKAGE);
  @NotNull private static final ApkClass UNIT_TEST_CLASS = new ApkClass("UnitTest", SIMPLEAPPLICATION_PACKAGE);

  private SimpleApplicationContents() {
  }

  @NotNull
  public static ApkClass getMyActivityApkClass() {
    return MY_ACTIVITY_CLASS;
  }

  @NotNull
  public static ApkClass getUnitTestClass() {
    return UNIT_TEST_CLASS;
  }

  @NotNull
  public static VirtualFile getMyActivityFile(@NotNull Module appModule) {
    String relativePath = join("src", "main", "java", GOOGLE_PACKAGE.getName(), SIMPLEAPPLICATION_PACKAGE.getName(),
                               MY_ACTIVITY_CLASS.getName() + ".java");
    return getModuleFile(appModule, relativePath);
  }

  @NotNull
  public static VirtualFile getUnitTestFile(@NotNull Module appModule) {
    String relativePath = join("src", "test", "java", GOOGLE_PACKAGE.getName(), SIMPLEAPPLICATION_PACKAGE.getName(),
                               UNIT_TEST_CLASS.getName() + ".java");
    return getModuleFile(appModule, relativePath);
  }

  @NotNull
  private static VirtualFile getModuleFile(@NotNull Module module, @NotNull String relativePath) {
    File folderPath = findModuleRootFolderPath(module);
    File filePath = new File(folderPath, relativePath);
    VirtualFile file = findFileByIoFile(filePath, true);
    assertNotNull(file);
    return file;
  }
}
