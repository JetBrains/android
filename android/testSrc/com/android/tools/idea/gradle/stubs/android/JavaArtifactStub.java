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

import com.android.annotations.Nullable;
import com.android.builder.model.JavaArtifact;
import com.android.tools.idea.gradle.stubs.FileStructure;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class JavaArtifactStub extends BaseArtifactStub implements JavaArtifact {
  private File myMockablePlatformJar;

  public JavaArtifactStub(@NotNull String name, String dirName, @NotNull String buildType, @NotNull FileStructure fileStructure) {
    super(name, dirName, new DependenciesStub(), buildType, fileStructure);
  }

  @Override
  @Nullable
  public File getMockablePlatformJar() {
    return myMockablePlatformJar;
  }

  public void setMockablePlatformJar(@Nullable File mockablePlatformJar) {
    myMockablePlatformJar = mockablePlatformJar;
  }
}
