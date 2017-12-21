/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.stubs.*;
import com.android.ide.common.gradle.model.stubs.DependenciesStub;
import com.android.ide.common.gradle.model.stubs.level2.IdeDependenciesStub;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.text.StringUtil.capitalize;

public class TestAndroidArtifact extends AndroidArtifactStub implements IdeAndroidArtifact {
  @NotNull private final FileStructure myFileStructure;
  @NotNull private final IdeDependenciesStub myIdeLevel2DependenciesStub;
  private boolean myTestArtifact;

  public TestAndroidArtifact(@NotNull String name,
                             @NotNull String folderName,
                             @NonNls @NotNull String buildType,
                             @NotNull FileStructure fileStructure) {
    super(name, "compile" + capitalize(buildType), "assemble" + capitalize(buildType), createClassesFolder(folderName, fileStructure),
          Sets.newHashSet() /* additional classes folder */, createJavaResourcesFolder(folderName, fileStructure),
          new DependenciesStub(), new DependenciesStub(), new DependencyGraphsStub(), Sets.newHashSet() /* IDE setup task names */,
          Lists.newArrayList() /* generated source folders */, Lists.newArrayList(new AndroidArtifactOutputStub()),
          "app." + buildType.toLowerCase() /* application ID */, "generate" + capitalize(buildType) + "Sources",
          Maps.newHashMap() /* build config fields */, Maps.newHashMap() /* res values */, new InstantRunStub(),
          Lists.newArrayList() /* additional runtime APKs */, null, null, null, null, null, null, null, true /* signed */);
    myFileStructure = fileStructure;
    myIdeLevel2DependenciesStub = new IdeDependenciesStub();
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

  /**
   * Adds the given path to the list of generated source directories. It also creates the directory in the file system.
   *
   * @param path path of the generated source directory to add, relative to the root directory of the Android project.
   */
  public void addGeneratedSourceFolder(@NotNull String path) {
    File directory = myFileStructure.createProjectFolder(path);
    getGeneratedResourceFolders().add(directory);
  }

  /**
   * Adds the given path to the list of generated resource directories. It also creates the directory in the file system.
   *
   * @param path path of the generated resource directory to add, relative to the root directory of the Android project.
   */
  public void addGeneratedResourceFolder(@NotNull String path) {
    File directory = myFileStructure.createProjectFolder(path);
    getGeneratedResourceFolders().add(directory);
  }

  @Override
  public boolean isTestArtifact() {
    return myTestArtifact;
  }

  public void setTestArtifact(boolean testArtifact) {
    myTestArtifact = testArtifact;
  }

  @Override
  @NotNull
  public IdeDependenciesStub getLevel2Dependencies() {
    return myIdeLevel2DependenciesStub;
  }
}
