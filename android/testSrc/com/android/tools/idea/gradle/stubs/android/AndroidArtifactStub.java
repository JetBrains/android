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
import com.android.builder.model.*;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.stubs.level2.IdeDependenciesStub;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static org.mockito.Mockito.mock;

public class AndroidArtifactStub extends BaseArtifactStub implements IdeAndroidArtifact {
  @NotNull private final List<File> myGeneratedResourceFolders = Lists.newArrayList();
  @NotNull private final Collection<AndroidArtifactOutput> myOutputs;
  @NotNull private final Collection<NativeLibrary> myNativeLibraries = Lists.newArrayList();
  @NotNull private final IdeDependenciesStub myIdeLevel2DependenciesStub;
  @NotNull private String myApplicationId;

  private InstantRun myInstantRun;
  private TestOptions myTestOptions;
  @Nullable private final String myInstrumentedTestTaskName;

  public AndroidArtifactStub(@NotNull String name,
                             @NotNull String folderName,
                             @NonNls @NotNull String buildType,
                             @NotNull FileStructure fileStructure) {
    super(name, folderName, new DependenciesStub(), buildType, fileStructure);
    myApplicationId = "app." + buildType.toLowerCase();
    AndroidArtifactOutputStub output = new AndroidArtifactOutputStub(new OutputFileStub(new File(name + "-" + buildType + ".apk")));
    myOutputs = Collections.singletonList(output);
    myIdeLevel2DependenciesStub = new IdeDependenciesStub();
    myInstrumentedTestTaskName = "instrumentedTestsTaskName";
  }

  @Override
  @NotNull
  public Collection<AndroidArtifactOutput> getOutputs() {
    return myOutputs;
  }

  @Override
  public boolean isSigned() {
    return true;
  }

  @Override
  @Nullable
  public String getSigningConfigName() {
    return "test";
  }

  @Override
  @NotNull
  public String getApplicationId() {
    return myApplicationId;
  }

  public AndroidArtifactStub setApplicationId(@NotNull String applicationId) {
    myApplicationId = applicationId;
    return this;
  }

  @Override
  @NotNull
  public String getSourceGenTaskName() {
    return "generate" + capitalize(myBuildType) + "Sources";
  }

  @Override
  @NotNull
  public List<File> getGeneratedResourceFolders() {
    return myGeneratedResourceFolders;
  }

  @Override
  @Nullable
  public Set<String> getAbiFilters() {
    return null;
  }

  @Override
  @NotNull
  public Collection<NativeLibrary> getNativeLibraries() {
    return myNativeLibraries;
  }

  @Override
  @NotNull
  public Map<String, ClassField> getBuildConfigFields() {
    return Collections.emptyMap();
  }

  @Override
  @NotNull
  public Map<String, ClassField> getResValues() {
    return Collections.emptyMap();
  }

  @Override
  @NotNull
  public InstantRun getInstantRun() {
    return myInstantRun != null ? myInstantRun : mock(InstantRun.class);
  }

  @NonNull
  @Override
  public Collection<File> getAdditionalRuntimeApks() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public TestOptions getTestOptions() {
    return myTestOptions != null ? myTestOptions : mock(TestOptions.class);
  }

  @Nullable
  @Override
  public String getInstrumentedTestTaskName() {
    return myInstrumentedTestTaskName;
  }

  public AndroidArtifactStub setInstantRun(InstantRun instantRun) {
    myInstantRun = instantRun;
    return this;
  }

  /**
   * Adds the given path to the list of generated resource directories. It also creates the directory in the file system.
   *
   * @param path path of the generated resource directory to add, relative to the root directory of the Android project.
   */
  public void addGeneratedResourceFolder(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myGeneratedResourceFolders.add(directory);
  }

  @Override
  public boolean isTestArtifact() {
    return true;
  }

  @Override
  @NotNull
  public IdeDependenciesStub getLevel2Dependencies() {
    return myIdeLevel2DependenciesStub;
  }
}
