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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.JavaArtifact;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

public final class JavaArtifactStub extends BaseArtifactStub implements JavaArtifact {
  @Nullable private final File myMockablePlatformJar;

  public JavaArtifactStub() {
    this(new File("jar"));
  }

  public JavaArtifactStub(@Nullable File mockablePlatformJar) {
    myMockablePlatformJar = mockablePlatformJar;
  }

  @Override
  @Nullable
  public File getMockablePlatformJar() {
    return myMockablePlatformJar;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof JavaArtifact)) {
      return false;
    }
    JavaArtifact stub = (JavaArtifact)o;
    return Objects.equals(getName(), stub.getName()) &&
           Objects.equals(getCompileTaskName(), stub.getCompileTaskName()) &&
           Objects.equals(getAssembleTaskName(), stub.getAssembleTaskName()) &&
           Objects.equals(getClassesFolder(), stub.getClassesFolder()) &&
           Objects.equals(getJavaResourcesFolder(), stub.getJavaResourcesFolder()) &&
           Objects.equals(getDependencies(), stub.getDependencies()) &&
           Objects.equals(getCompileDependencies(), stub.getCompileDependencies()) &&
           Objects.equals(getDependencyGraphs(), stub.getDependencyGraphs()) &&
           Objects.equals(getIdeSetupTaskNames(), stub.getIdeSetupTaskNames()) &&
           Objects.equals(getGeneratedSourceFolders(), stub.getGeneratedSourceFolders()) &&
           Objects.equals(getVariantSourceProvider(), stub.getVariantSourceProvider()) &&
           Objects.equals(getMultiFlavorSourceProvider(), stub.getMultiFlavorSourceProvider()) &&
           Objects.equals(getMockablePlatformJar(), stub.getMockablePlatformJar());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getCompileTaskName(), getAssembleTaskName(), getClassesFolder(), getJavaResourcesFolder(),
                        getDependencies(), getCompileDependencies(), getDependencyGraphs(), getIdeSetupTaskNames(),
                        getGeneratedSourceFolders(), getVariantSourceProvider(), getMultiFlavorSourceProvider(), getMockablePlatformJar());
  }

  @Override
  public String toString() {
    return "JavaArtifactStub{" +
           "myMockablePlatformJar=" + myMockablePlatformJar +
           "} " + super.toString();
  }
}
