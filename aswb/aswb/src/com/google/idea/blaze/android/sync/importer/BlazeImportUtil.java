/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.importer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.base.ideinfo.AndroidAarIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.common.Output;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/** Static utility methods used for blaze import. */
public class BlazeImportUtil {

  @Nullable
  public static String javaResourcePackageFor(TargetIdeInfo target) {
    AndroidIdeInfo ideInfo = target.getAndroidIdeInfo();
    if (ideInfo != null) {
      return ideInfo.getResourceJavaPackage();
    }

    AndroidAarIdeInfo aarIdeInfo = target.getAndroidAarIdeInfo();
    if (aarIdeInfo != null) {
      return aarIdeInfo.getCustomJavaPackage();
    }

    return null;
  }

  @Nullable
  public static String javaResourcePackageFor(TargetIdeInfo target, boolean inferPackage) {
    String definedJavaPackage = javaResourcePackageFor(target);
    if (!inferPackage || definedJavaPackage != null) {
      return definedJavaPackage;
    }

    return inferJavaResourcePackage(target.getKey().getLabel().blazePackage().relativePath());
  }

  @VisibleForTesting
  static String inferJavaResourcePackage(String blazeRelativePath) {
    // Blaze ensures that all android targets either provide a custom package override, or have
    // blaze package of the form:
    //        //any/path/java/package/name/with/slashes, or
    //        //any/path/javatests/package/name/with/slashes
    // We use this fact to infer package name.

    // Using the separator `/` to ensure we do not accidentally catch things like "/java_src/"
    // or "/somenamejava/"
    String javaPackage = "/" + blazeRelativePath;
    String workingPackage;

    // get everything after `/java/` , or no-op if `/java/` is not present
    workingPackage = StringUtil.substringAfterLast(javaPackage, "/java/");
    javaPackage = workingPackage == null ? javaPackage : "/" + workingPackage;

    // get everything after `/javatests/` , or no-op if `/javatests/` is not present
    workingPackage = StringUtil.substringAfterLast(javaPackage, "/javatests/");
    javaPackage = workingPackage == null ? javaPackage : "/" + workingPackage;

    if (javaPackage.startsWith("/")) {
      javaPackage = javaPackage.substring(1);
    }

    return javaPackage.replace('/', '.');
  }

  static Consumer<Output> asConsumer(BlazeContext context) {
    return (Output issue) -> {
      context.output(issue);
      if (issue instanceof IssueOutput) {
        IssueOutput issueOutput = (IssueOutput) issue;
        if (issueOutput.getCategory()
            == com.google.idea.blaze.base.scope.output.IssueOutput.Category.ERROR) {
          context.setHasError();
        }
      }
    };
  }

  /**
   * Returns the stream of {@link TargetIdeInfo} corresponding to targets that should be considered
   * source targets in the given {@link TargetMap}.
   */
  static Stream<TargetIdeInfo> getSourceTargetsStream(
      TargetMap targetMap, ProjectViewTargetImportFilter importFilter) {
    return targetMap.targets().stream()
        .filter(target -> target.getKind().hasLanguage(LanguageClass.ANDROID))
        .filter(target -> target.getAndroidIdeInfo() != null)
        .filter(importFilter::isSourceTarget)
        .filter(target -> !importFilter.excludeTarget(target));
  }

  /**
   * Returns the stream of {@link TargetIdeInfo} corresponding to source targets in the given {@link
   * BlazeImportInput}.
   */
  public static Stream<TargetIdeInfo> getSourceTargetsStream(BlazeImportInput input) {
    return getSourceTargetsStream(input.targetMap, input.createImportFilter());
  }

  /**
   * Returns the stream of {@link TargetIdeInfo} corresponding to source targets in the given {@link
   * Project}
   */
  public static Stream<TargetIdeInfo> getSourceTargetsStream(Project project) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return Stream.empty();
    }
    return getSourceTargetsStream(
        project, projectData, ProjectViewManager.getInstance(project).getProjectViewSet());
  }

  /**
   * Returns the stream of {@link TargetIdeInfo} corresponding to source targets in the given {@link
   * Project}, {@link BlazeProjectData}, and {@link ProjectViewSet}
   */
  public static Stream<TargetIdeInfo> getSourceTargetsStream(
      Project project, BlazeProjectData projectData, ProjectViewSet projectViewSet) {
    ProjectViewTargetImportFilter importFilter =
        new ProjectViewTargetImportFilter(
            Blaze.getBuildSystemName(project), WorkspaceRoot.fromProject(project), projectViewSet);
    return getSourceTargetsStream(projectData.getTargetMap(), importFilter);
  }

  /** Returns the source targets for the given {@link BlazeImportInput} as a {@link List}. */
  public static List<TargetIdeInfo> getSourceTargets(BlazeImportInput input) {
    return getSourceTargetsStream(input).collect(Collectors.toList());
  }

  /** Returns the javac jars if they can be found in the given list of targets. */
  static ImmutableList<BlazeJarLibrary> getJavacJars(Collection<TargetIdeInfo> targets) {
    TargetIdeInfo target =
        targets.stream().filter(t -> t.getJavaToolchainIdeInfo() != null).findFirst().orElse(null);
    if (target == null) {
      return ImmutableList.of();
    }
    return target.getJavaToolchainIdeInfo().getJavacJars().stream()
        .map(
            javacJar ->
                new BlazeJarLibrary(
                    new LibraryArtifact(null, javacJar, ImmutableList.of()), target.getKey()))
        .collect(ImmutableList.toImmutableList());
  }

  static ImmutableList<BlazeJarLibrary> getResourceJars(Collection<TargetIdeInfo> targets) {
    return targets.stream()
        .filter(
            e -> e.getAndroidIdeInfo() != null && e.getAndroidIdeInfo().getResourceJar() != null)
        .map(e -> new BlazeJarLibrary(e.getAndroidIdeInfo().getResourceJar(), e.getKey()))
        .collect(ImmutableList.toImmutableList());
  }

  /**
   * Returns the set of paths for "allowed generated resource".
   *
   * <p>Normally generated resources are not picked up during sync. However, there maybe cases where
   * some resources are intentionally generated to be used as source. These resources can be
   * "allowed" by including it under the {@link GeneratedAndroidResourcesSection} tag.
   */
  public static ImmutableSet<String> getAllowedGenResourcePaths(ProjectViewSet projectViewSet) {
    return ImmutableSet.copyOf(
        projectViewSet.listItems(GeneratedAndroidResourcesSection.KEY).stream()
            .map(genfilesPath -> genfilesPath.relativePath)
            .collect(Collectors.toSet()));
  }

  /** Returns true if the folder is outside the project view. */
  public static Predicate<ArtifactLocation> isOutsideProjectViewFilter(BlazeImportInput input) {
    ImportRoots importRoots =
        ImportRoots.builder(input.workspaceRoot, input.buildSystemName)
            .add(input.projectViewSet)
            .build();
    return artifactLocation ->
        !importRoots.containsWorkspacePath(new WorkspacePath(artifactLocation.getRelativePath()));
  }
}
