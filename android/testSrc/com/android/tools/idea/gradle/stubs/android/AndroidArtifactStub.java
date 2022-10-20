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

import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static org.mockito.Mockito.mock;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.ClassField;
import com.android.builder.model.CodeShrinker;
import com.android.builder.model.InstantRun;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.TestOptions;
import com.android.tools.idea.gradle.stubs.FileStructure;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidArtifactStub extends BaseArtifactStub implements AndroidArtifact {
  @NotNull private final List<File> myGeneratedResourceFolders = new ArrayList<>();
  @NotNull private final List<AndroidArtifactOutput> myOutputs;
  @NotNull private String myApplicationId;

  private InstantRun myInstantRun;
  @Nullable private final String myInstrumentedTestTaskName;

  public AndroidArtifactStub(@NotNull String name,
                             @NotNull String folderName,
                             @NonNls @NotNull String buildType,
                             @NotNull FileStructure fileStructure) {
    super(name, folderName, new DependenciesStub(), buildType, fileStructure);
    myApplicationId = "app." + buildType.toLowerCase();
    AndroidArtifactOutputStub output = new AndroidArtifactOutputStub(new OutputFileStub(new File(name + "-" + buildType + ".apk")));
    myOutputs = Collections.singletonList(output);
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
  @NonNull
  public Set<String> getAbiFilters() {
    return Collections.emptySet();
  }

  @Override
  @NotNull
  public Collection<NativeLibrary> getNativeLibraries() {
    return null;
  }

  /**
   * Removed from the model in 4.1.0
   */
  @Override
  @Deprecated
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
    return mock(TestOptions.class);
  }

  @Nullable
  @Override
  public String getInstrumentedTestTaskName() {
    return myInstrumentedTestTaskName;
  }

  @Nullable
  @Override
  public String getBundleTaskName() {
    return null;
  }

  @Nullable
  @Override
  public String getBundleTaskOutputListingFile() {
    return new File(myFileStructure.getRootFolderPath(),
                    "build/intermediates/bundle_ide_model/" + myBuildType + "/output.json").getAbsolutePath();
  }

  @Nullable
  @Override
  public String getApkFromBundleTaskName() {
    return null;
  }

  @Nullable
  @Override
  public String getApkFromBundleTaskOutputListingFile() {
    return new File(myFileStructure.getRootFolderPath(),
                    "build/intermediates/apk_from_bundle_ide_model/" + myBuildType + "/output.json").getAbsolutePath();
  }

  @Nullable
  @Override
  public CodeShrinker getCodeShrinker() {
    return null;
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
}
