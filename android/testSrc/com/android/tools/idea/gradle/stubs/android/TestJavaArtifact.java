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
package com.android.tools.idea.gradle.stubs.android;

import com.android.ide.common.gradle.model.stubs.DependenciesStub;
import com.android.ide.common.gradle.model.stubs.DependencyGraphsStub;
import com.android.ide.common.gradle.model.stubs.JavaArtifactStub;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.text.StringUtil.capitalize;

public class TestJavaArtifact extends JavaArtifactStub {
  public TestJavaArtifact(@NotNull String name,
                          @NotNull String folderName,
                          @NonNls @NotNull String buildType,
                          @NotNull FileStructure fileStructure) {
    super(name, "compile" + capitalize(buildType), "assemble" + capitalize(buildType), createClassesFolder(folderName, fileStructure),
          Sets.newHashSet() /* additional classes folder */, createJavaResourcesFolder(folderName, fileStructure),
          new DependenciesStub(), new DependenciesStub(), new DependencyGraphsStub(), Sets.newHashSet() /* IDE setup task names */,
          Lists.newArrayList() /* generated source folders */, null, null, null);
  }

  @NotNull
  private static File createClassesFolder(@NotNull String folderName, @NotNull FileStructure fileStructure) {
    String path = join("build", "intermediates", "classes",  folderName);
    return new File(fileStructure.getRootFolderPath(), path);
  }

  @NotNull
  private static File createJavaResourcesFolder(@NotNull String folderName, @NotNull FileStructure fileStructure) {
    String path = join("build", "intermediates", "javaResources",  folderName);
    return new File(fileStructure.getRootFolderPath(), path);
  }
}
