/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.sync.importer.JavaSourceFilter;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.openapi.compiler.ClassObject;
import com.intellij.openapi.compiler.CompilationException;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * {@link CompilerManager} with jars for source targets added to the classpath.
 *
 * <p>Necessary for stream evaluation.
 */
public class BlazeCompilerManager extends CompilerManagerImpl {
  private static final BoolExperiment compileAgainstProjectClassJars =
      new BoolExperiment("compile.against.project.class.jars", true);

  private final Project project;

  public BlazeCompilerManager(Project project) {
    super(project);
    this.project = project;
  }

  @Override
  public Collection<ClassObject> compileJavaCode(
      List<String> options,
      Collection<? extends File> platformCp,
      Collection<? extends File> classpath,
      Collection<? extends File> upgradeModulePath,
      Collection<? extends File> modulePath,
      Collection<? extends File> sourcePath,
      Collection<? extends File> files,
      File outputDir)
      throws IOException, CompilationException {
    return super.compileJavaCode(
        options,
        platformCp,
        updateClasspath(project, classpath),
        upgradeModulePath,
        modulePath,
        sourcePath,
        files,
        outputDir);
  }

  private static Collection<File> getAdditionalProjectJars(Project project) {
    if (!compileAgainstProjectClassJars.getValue() || !Blaze.isBlazeProject(project)) {
      return ImmutableList.of();
    }
    Collection<File> projectClassJars =
        SyncCache.getInstance(project)
            .get(BlazeCompilerManager.class, BlazeCompilerManager::updateClasspath);
    return projectClassJars != null ? projectClassJars : ImmutableList.of();
  }

  private static Collection<? extends File> updateClasspath(
      Project project, Collection<? extends File> classpath) {
    return ImmutableList.<File>builder()
        .addAll(classpath)
        .addAll(getAdditionalProjectJars(project))
        .build();
  }

  private static Collection<File> updateClasspath(Project project, BlazeProjectData projectData) {
    ProjectViewTargetImportFilter importFilter =
        new ProjectViewTargetImportFilter(
            Blaze.getBuildSystemName(project),
            WorkspaceRoot.fromProject(project),
            ProjectViewManager.getInstance(project).getProjectViewSet());
    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    return projectData.getTargetMap().targets().stream()
        .filter(target -> target.getJavaIdeInfo() != null)
        .filter(target -> JavaSourceFilter.importAsSource(importFilter, target))
        .map(TargetIdeInfo::getJavaIdeInfo)
        .map(JavaIdeInfo::getJars)
        .flatMap(Collection::stream)
        .map(LibraryArtifact::jarForIntellijLibrary)
        .map(artifact -> OutputArtifactResolver.resolve(project, decoder, artifact))
        .filter(Objects::nonNull)
        .collect(ImmutableList.toImmutableList());
  }
}
