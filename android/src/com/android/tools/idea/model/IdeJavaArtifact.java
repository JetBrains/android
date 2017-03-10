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
package com.android.tools.idea.model;

import com.android.annotations.Nullable;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.Library;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * Creates a deep copy of {@link JavaArtifact}.
 *
 * @see IdeAndroidProject
 */
final public class IdeJavaArtifact implements JavaArtifact, Serializable {
  @NotNull private final String myName;
  @NotNull private final String myCompileTaskName;
  @NotNull private final String myAssembleTaskName;
  @NotNull private final File myClassesFolder;
  @NotNull private final File myJavaResourcesFolder;
  @NotNull private final Dependencies myDependencies;
  @NotNull private final Dependencies myCompileDependencies;
  @NotNull private final DependencyGraphs myDependencyGraphs;
  @NotNull private final Set<String> myIdeSetupTaskNames;
  @NotNull private final Collection<File> myGeneratedSourceFolders;
  @Nullable private final SourceProvider myVariantSourceProvider;
  @Nullable private final SourceProvider myMultiFlavorSourceProvider;
  @Nullable private final File myMockablePlatformJar;

  public IdeJavaArtifact(@NotNull JavaArtifact artifact, @NotNull Map<Library, Library> seen, @NotNull GradleVersion gradleVersion) {
    myName = artifact.getName();
    myCompileTaskName = artifact.getCompileTaskName();
    myAssembleTaskName = artifact.getAssembleTaskName();
    myClassesFolder = artifact.getClassesFolder();
    myJavaResourcesFolder = artifact.getJavaResourcesFolder();
    myDependencies = new IdeDependencies(artifact.getDependencies(), seen, gradleVersion);
    myCompileDependencies = new IdeDependencies(artifact.getCompileDependencies(), seen, gradleVersion);
    if (gradleVersion.isAtLeast(2,3,0)) {
      myDependencyGraphs = new IdeDependencyGraphs(artifact.getDependencyGraphs());
    } else {
      myDependencyGraphs = new IdeDependencyGraphs();
    }

    myIdeSetupTaskNames = new HashSet<>(artifact.getIdeSetupTaskNames());
    myGeneratedSourceFolders = new ArrayList<>(artifact.getGeneratedSourceFolders());

    SourceProvider arVariantSourceProvider = artifact.getVariantSourceProvider();
    myVariantSourceProvider = arVariantSourceProvider == null ? null : new IdeSourceProvider(arVariantSourceProvider);

    SourceProvider arMultiFlavorSourceProvider = artifact.getMultiFlavorSourceProvider();
    myMultiFlavorSourceProvider = arMultiFlavorSourceProvider == null ? null : new IdeSourceProvider(arMultiFlavorSourceProvider);
    myMockablePlatformJar = artifact.getMockablePlatformJar();
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getCompileTaskName() {
    return myCompileTaskName;
  }

  @Override
  @NotNull
  public String getAssembleTaskName() {
    return myAssembleTaskName;
  }

  @Override
  @NotNull
  public File getClassesFolder() {
    return myClassesFolder;
  }

  @Override
  @NotNull
  public File getJavaResourcesFolder() {
    return myJavaResourcesFolder;
  }

  @Override
  @NotNull
  public Dependencies getDependencies() {
    return myDependencies;
  }

  @Override
  @NotNull
  public Dependencies getCompileDependencies() {
    return myCompileDependencies;
  }

  @Override
  @NotNull
  public DependencyGraphs getDependencyGraphs() {
    return myDependencyGraphs;
  }

  @Override
  @NotNull
  public Set<String> getIdeSetupTaskNames() {
    return myIdeSetupTaskNames;
  }

  @Override
  @NotNull
  public Collection<File> getGeneratedSourceFolders() {
    return myGeneratedSourceFolders;
  }

  @Override
  @Nullable
  public SourceProvider getVariantSourceProvider() {
    return myVariantSourceProvider;
  }

  @Override
  @Nullable
  public SourceProvider getMultiFlavorSourceProvider() {
    return myMultiFlavorSourceProvider;
  }

  @Override
  @Nullable
  public File getMockablePlatformJar() {
    return myMockablePlatformJar;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaArtifact)) return false;
    JavaArtifact artifact = (JavaArtifact)o;
    return Objects.equals(getName(), artifact.getName()) &&
           Objects.equals(getCompileTaskName(), artifact.getCompileTaskName()) &&
           Objects.equals(getAssembleTaskName(), artifact.getAssembleTaskName()) &&
           Objects.equals(getClassesFolder(), artifact.getClassesFolder()) &&
           Objects.equals(getJavaResourcesFolder(), artifact.getJavaResourcesFolder()) &&
           Objects.equals(getDependencies(), artifact.getDependencies()) &&
           Objects.equals(getCompileDependencies(), artifact.getCompileDependencies()) &&
           Objects.equals(getDependencyGraphs(), artifact.getDependencyGraphs()) &&
           Objects.equals(getIdeSetupTaskNames(), artifact.getIdeSetupTaskNames()) &&
           Objects.equals(getGeneratedSourceFolders(), artifact.getGeneratedSourceFolders()) &&
           Objects.equals(getVariantSourceProvider(), artifact.getVariantSourceProvider()) &&
           Objects.equals(getMultiFlavorSourceProvider(), artifact.getMultiFlavorSourceProvider()) &&
           Objects.equals(getMockablePlatformJar(), artifact.getMockablePlatformJar());
  }

  @Override
  public int hashCode() {
    return Objects
      .hash(getName(), getCompileTaskName(), getAssembleTaskName(), getClassesFolder(), getJavaResourcesFolder(),
            getDependencies(), getCompileDependencies(), getDependencyGraphs(), getIdeSetupTaskNames(),
            getGeneratedSourceFolders(), getVariantSourceProvider(), getMultiFlavorSourceProvider(), getMockablePlatformJar());
  }
}
