/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.producers;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncProject;
import com.google.idea.blaze.base.run.producers.BinaryContextProvider;
import com.google.idea.blaze.base.run.testmap.FilteredTargetMap;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.java.run.RunUtil;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.ProjectTarget;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiMethodUtil;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/** Creates run configurations for Java main classes sourced by java_binary targets. */
public class JavaBinaryContextProvider implements BinaryContextProvider {

  private static final String JAVA_BINARY_MAP_KEY = "BlazeJavaBinaryMap";

  @Nullable
  @Override
  public BinaryRunContext getRunContext(ConfigurationContext context) {
    PsiClass mainClass = getMainClass(context);
    if (mainClass == null) {
      return null;
    }

    TargetInfo targetInfo = getTargetInfo(context.getProject(), mainClass);
    if (targetInfo == null) {
      return null;
    }
    // Try setting source element to a main method so ApplicationConfigurationProducer
    // can't override our configuration by producing a more specific one.
    PsiMethod mainMethod = PsiMethodUtil.findMainMethod(mainClass);
    return BinaryRunContext.create(
        /* sourceElement= */ mainMethod != null ? mainMethod : mainClass, targetInfo);
  }

  @Nullable
  private static PsiClass getMainClass(ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    if (location == null) {
      return null;
    }
    PsiElement element = location.getPsiElement();
    if (!element.isPhysical()) {
      return null;
    }
    return ApplicationConfigurationType.getMainClass(element);
  }

  @Nullable
  @VisibleForTesting
  public static TargetInfo getTargetInfo(Project project, PsiClass mainClass) {
    File mainClassFile = RunUtil.getFileForClass(mainClass);
    if (mainClassFile == null) {
      return null;
    }

    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      return getTargetInfoQuerySync(project, mainClass, mainClassFile);
    } else {
      return getTargetInfoAspectSync(project, mainClass, mainClassFile);
    }
  }

  @Nullable
  private static TargetInfo getTargetInfoQuerySync(
      Project project, PsiClass mainClass, File mainClassFile) {
    QuerySyncProject querySyncProject =
        QuerySyncManager.getInstance(project).getLoadedProject().orElse(null);
    if (querySyncProject == null) {
      return null;
    }
    BuildGraphData buildGraphData =
        querySyncProject
            .getSnapshotHolder()
            .getCurrent()
            .map(QuerySyncProjectSnapshot::graph)
            .orElse(null);
    if (buildGraphData == null) {
      return null;
    }

    WorkspacePath path = querySyncProject.getWorkspaceRoot().workspacePathFor(mainClassFile);
    ImmutableSet<Label> targetOwners = buildGraphData.getSourceFileOwners(Path.of(path.relativePath()));

    if (targetOwners == null) {
      return null;
    }

    ImmutableSet<ProjectTarget> binaryTargets =
        targetOwners.stream()
            .map(label -> buildGraphData.targetMap().get(label))
            .filter(Objects::nonNull)
            .filter(t -> t.kind().equals("java_binary"))
            .collect(toImmutableSet());

    if (binaryTargets.isEmpty()) {
      return null;
    }

    ProjectTarget pt;
    String qualifiedName = mainClass.getQualifiedName();
    String className = mainClass.getName();
    if (qualifiedName == null || className == null) {
      // out of date psi element; just take the first match
      pt = binaryTargets.stream().findFirst().orElseThrow();
    } else {
      Optional<ProjectTarget> possibleMatch =
          binaryTargets.stream()
              .filter(
                  binary ->
                      (binary.mainClass().isPresent()
                              && binary.mainClass().get().equals(qualifiedName))
                          || binary.label().getName().toString().equals(className))
              .findFirst();
      pt = possibleMatch.orElse(binaryTargets.stream().findFirst().orElseThrow());
    }
    return TargetInfo.fromBuildTarget(pt);
  }

  @Nullable
  private static TargetInfo getTargetInfoAspectSync(
      Project project, PsiClass mainClass, File mainClassFile) {
    TargetIdeInfo targetIdeInfo = getTargetIdeInfo(project, mainClass, mainClassFile);
    if (targetIdeInfo == null) {
      return null;
    }
    return targetIdeInfo.toTargetInfo();
  }

  @Nullable
  private static TargetIdeInfo getTargetIdeInfo(
      Project project, PsiClass mainClass, File mainClassFile) {
    Collection<TargetIdeInfo> javaBinaryTargets = findJavaBinaryTargets(project, mainClassFile);

    String qualifiedName = mainClass.getQualifiedName();
    String className = mainClass.getName();
    if (qualifiedName == null || className == null) {
      // out of date psi element; just take the first match
      return Iterables.getFirst(javaBinaryTargets, null);
    }

    // first look for a matching main_class
    TargetIdeInfo match =
        javaBinaryTargets.stream()
            .filter(
                target ->
                    target.getJavaIdeInfo() != null
                        && qualifiedName.equals(target.getJavaIdeInfo().getJavaBinaryMainClass()))
            .findFirst()
            .orElse(null);
    if (match != null) {
      return match;
    }

    match =
        javaBinaryTargets.stream()
            .filter(target -> className.equals(target.getKey().getLabel().targetName().toString()))
            .findFirst()
            .orElse(null);
    if (match != null) {
      return match;
    }
    return Iterables.getFirst(javaBinaryTargets, null);
  }

  /** Returns all java_binary targets reachable from the given source file. */
  private static Collection<TargetIdeInfo> findJavaBinaryTargets(
      Project project, File mainClassFile) {
    FilteredTargetMap map =
        SyncCache.getInstance(project)
            .get(JAVA_BINARY_MAP_KEY, JavaBinaryContextProvider::computeTargetMap);
    return map != null ? map.targetsForSourceFile(mainClassFile) : ImmutableList.of();
  }

  private static FilteredTargetMap computeTargetMap(Project project, BlazeProjectData projectData) {
    return new FilteredTargetMap(
        project,
        projectData.getArtifactLocationDecoder(),
        projectData.getTargetMap(),
        target ->
            target.isPlainTarget()
                && target.getKind().hasLanguage(LanguageClass.JAVA)
                && target.getKind().getRuleType().equals(RuleType.BINARY));
  }
}
