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

import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.level2.DependencyGraphs;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class BaseArtifactStub extends BaseStub implements BaseArtifact {
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
  @NotNull private final Set<File> myAdditionalClassesFolders;
    ;

  public BaseArtifactStub() {
    this("name", "compile", "assemble", new File("classes"), new HashSet<>(),
         new File("javaResources"), new DependenciesStub(), new DependenciesStub(),
         new DependencyGraphsStub(), Sets.newHashSet("setup"), Lists.newArrayList(new File("generated")),
         new SourceProviderStub(), new SourceProviderStub());
  }

  public BaseArtifactStub(@NotNull String name,
                          @NotNull String compileTaskName,
                          @NotNull String assembleTaskName,
                          @NotNull File classesFolder,
                          @NotNull Set<File> classesFolders,
                          @NotNull File javaResourcesFolder,
                          @NotNull Dependencies dependencies,
                          @NotNull Dependencies compileDependencies,
                          @NotNull DependencyGraphs graphs,
                          @NotNull Set<String> names,
                          @NotNull Collection<File> folders,
                          @Nullable SourceProvider variantSourceProvider,
                          @Nullable SourceProvider multiFlavorSourceProvider) {
    myName = name;
    myCompileTaskName = compileTaskName;
    myAssembleTaskName = assembleTaskName;
    myClassesFolder = classesFolder;
    myAdditionalClassesFolders = classesFolders;
    myJavaResourcesFolder = javaResourcesFolder;
    myDependencies = dependencies;
    myCompileDependencies = compileDependencies;
    myDependencyGraphs = graphs;
    myIdeSetupTaskNames = names;
    myGeneratedSourceFolders = folders;
    myVariantSourceProvider = variantSourceProvider;
    myMultiFlavorSourceProvider = multiFlavorSourceProvider;
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
  @NotNull
  public Set<File> getAdditionalClassesFolders() {
    return myAdditionalClassesFolders;
  }

  @Override
  public String toString() {
    return "BaseArtifactStub{" +
           "myName='" + myName + '\'' +
           ", myCompileTaskName='" + myCompileTaskName + '\'' +
           ", myAssembleTaskName='" + myAssembleTaskName + '\'' +
           ", myClassesFolder=" + myClassesFolder +
           ", myAdditionalClassesFolders=" + myAdditionalClassesFolders +
           ", myJavaResourcesFolder=" + myJavaResourcesFolder +
           ", myDependencies=" + myDependencies +
           ", myCompileDependencies=" + myCompileDependencies +
           ", myDependencyGraphs=" + myDependencyGraphs +
           ", myIdeSetupTaskNames=" + myIdeSetupTaskNames +
           ", myGeneratedSourceFolders=" + myGeneratedSourceFolders +
           ", myVariantSourceProvider=" + myVariantSourceProvider +
           ", myMultiFlavorSourceProvider=" + myMultiFlavorSourceProvider +
           "}";
  }
}
