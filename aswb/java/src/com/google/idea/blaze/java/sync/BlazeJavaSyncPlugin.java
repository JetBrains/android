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
package com.google.idea.blaze.java.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SourceFolderProvider;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.java.projectview.ExcludeLibrarySection;
import com.google.idea.blaze.java.projectview.ExcludedLibrarySection;
import com.google.idea.blaze.java.projectview.JavaLanguageLevelSection;
import com.google.idea.blaze.java.sync.importer.BlazeJavaWorkspaceImporter;
import com.google.idea.blaze.java.sync.importer.JavaSourceFilter;
import com.google.idea.blaze.java.sync.jdeps.JdepsFileReader;
import com.google.idea.blaze.java.sync.jdeps.JdepsMap;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.projectstructure.JavaSourceFolderProvider;
import com.google.idea.blaze.java.sync.projectstructure.Jdks;
import com.google.idea.blaze.java.sync.workingset.JavaWorkingSet;
import com.google.idea.common.util.Transactions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.pom.java.LanguageLevel;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;

/** Sync support for Java. */
public class BlazeJavaSyncPlugin implements BlazeSyncPlugin {
  private final JdepsFileReader jdepsFileReader = new JdepsFileReader();
  private static final Logger logger = Logger.getInstance(BlazeJavaSyncPlugin.class);

  @Override
  public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of(WorkspaceType.JAVA);
  }

  @Nullable
  @Override
  public WorkspaceType getDefaultWorkspaceType() {
    return WorkspaceType.JAVA;
  }

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    if (workspaceType == WorkspaceType.JAVA) {
      return ImmutableSet.of(LanguageClass.JAVA);
    }
    return ImmutableSet.of();
  }

  @Nullable
  @Override
  public ModuleType<?> getWorkspaceModuleType(WorkspaceType workspaceType) {
    if (workspaceType == WorkspaceType.JAVA) {
      return StdModuleTypes.JAVA;
    }
    return null;
  }

  @Override
  public void updateSyncState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      BlazeVersionData blazeVersionData,
      @Nullable WorkingSet workingSet,
      ArtifactLocationDecoder artifactLocationDecoder,
      TargetMap targetMap,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState,
      SyncMode syncMode) {
    JavaWorkingSet javaWorkingSet = null;
    if (workingSet != null) {
      javaWorkingSet =
          new JavaWorkingSet(
              workspaceRoot, workingSet, Blaze.getBuildSystemProvider(project)::isBuildFile);
    }
    JavaSourceFilter sourceFilter =
        new JavaSourceFilter(
            Blaze.getBuildSystemName(project), workspaceRoot, projectViewSet, targetMap);

    JdepsMap jdepsMap =
        jdepsFileReader.loadJdepsFiles(
            project,
            context,
            artifactLocationDecoder,
            sourceFilter.getSourceTargets(),
            syncStateBuilder,
            previousSyncState,
            syncMode);
    if (context.isCancelled() || jdepsMap == null) {
      return;
    }

    BlazeJavaWorkspaceImporter blazeJavaWorkspaceImporter =
        new BlazeJavaWorkspaceImporter(
            project,
            workspaceRoot,
            projectViewSet,
            workspaceLanguageSettings,
            targetMap,
            sourceFilter,
            jdepsMap,
            javaWorkingSet,
            artifactLocationDecoder,
            previousSyncState);
    BlazeJavaImportResult importResult =
        Scope.push(
            context,
            (childContext) -> {
              childContext.push(new TimingScope("JavaWorkspaceImporter", EventType.Other));
              return blazeJavaWorkspaceImporter.importWorkspace(childContext);
            });
    Glob.GlobSet excludedLibraries =
        new Glob.GlobSet(
            ImmutableList.<Glob>builder()
                .addAll(projectViewSet.listItems(ExcludeLibrarySection.KEY))
                .addAll(projectViewSet.listItems(ExcludedLibrarySection.KEY))
                .build());
    syncStateBuilder.put(new BlazeJavaSyncData(importResult, excludedLibraries));
  }

  @Override
  public void updateProjectSdk(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isWorkspaceType(WorkspaceType.JAVA)) {
      return;
    }
    updateJdk(project, context, projectViewSet, blazeProjectData);
  }

  @Nullable
  @Override
  public SourceFolderProvider getSourceFolderProvider(BlazeProjectData projectData) {
    if (!projectData.getWorkspaceLanguageSettings().isWorkspaceType(WorkspaceType.JAVA)) {
      return null;
    }
    return new JavaSourceFolderProvider(projectData.getSyncState().get(BlazeJavaSyncData.class));
  }

  private static void updateJdk(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData) {
    Sdk currentSdk = ProjectRootManager.getInstance(project).getProjectSdk();

    LanguageLevel javaLanguageLevel =
        JavaLanguageLevelHelper.getJavaLanguageLevel(projectViewSet, blazeProjectData);

    Sdk sdk = Jdks.chooseOrCreateJavaSdk(currentSdk, javaLanguageLevel);
    if (sdk == null) {
      String msg =
          String.format(
              "Unable to find a JDK %1$s installed.\n", javaLanguageLevel.getPresentableText());
      msg +=
          "After configuring a suitable JDK in the \"Project Structure\" dialog, "
              + "sync the project again.";
      IssueOutput.error(msg).submit(context);
      return;
    }

    LanguageLevel currentLanguageLevel =
        LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
    if (sdk != currentSdk || javaLanguageLevel != currentLanguageLevel) {
      setProjectSdkAndLanguageLevel(project, sdk, javaLanguageLevel);
    }
  }

  private static void setProjectSdkAndLanguageLevel(
      final Project project, final Sdk sdk, final LanguageLevel javaLanguageLevel) {
    Transactions.submitWriteActionTransactionAndWait(
        () -> {
          ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(project);
          rootManager.setProjectSdk(sdk);
          LanguageLevelProjectExtension ext = LanguageLevelProjectExtension.getInstance(project);
          ext.setLanguageLevel(javaLanguageLevel);
        });
  }

  @Override
  public boolean validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    BlazeJavaSyncData syncData = blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    if (syncData == null) {
      return true;
    }
    warnAboutDeployJars(context, syncData);
    return true;
  }

  @Override
  public Collection<SectionParser> getSections() {
    return ImmutableList.of(
        ExcludedLibrarySection.PARSER,
        ExcludeLibrarySection.PARSER,
        JavaLanguageLevelSection.PARSER);
  }

  @Nullable
  @Override
  public LibrarySource getLibrarySource(
      ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.JAVA)) {
      return null;
    }
    return new BlazeJavaLibrarySource(blazeProjectData);
  }

  /**
   * Looks at your jars for anything that seems to be a deploy jar and warns about it. This often
   * turns out to be a duplicate copy of all your application's code, so you don't want it in your
   * project.
   */
  private static void warnAboutDeployJars(BlazeContext context, BlazeJavaSyncData syncData) {
    for (BlazeLibrary library : syncData.getImportResult().libraries.values()) {
      if (!(library instanceof BlazeJarLibrary)) {
        continue;
      }
      BlazeJarLibrary jarLibrary = (BlazeJarLibrary) library;
      LibraryArtifact libraryArtifact = jarLibrary.libraryArtifact;
      ArtifactLocation artifactLocation = libraryArtifact.jarForIntellijLibrary();
      if (artifactLocation.isExternal()) {
        return;
      }
      if (artifactLocation.getRelativePath().endsWith("deploy.jar")
          || artifactLocation.getRelativePath().endsWith("deploy-ijar.jar")
          || artifactLocation.getRelativePath().endsWith("deploy-hjar.jar")) {
        String warningMessage =
            "Performance warning: You have added a deploy jar as a library. "
                + "This can lead to poor indexing performance, and the debugger may "
                + "become confused and step into the deploy jar instead of your code. "
                + "Consider redoing the rule to not use deploy jars, exclude the target "
                + "from your .blazeproject, or exclude the library.\n"
                + "Library path: "
                + artifactLocation.getRelativePath();
        logger.warn(warningMessage);
        context.output(new PerformanceWarning(warningMessage));
      }
    }
  }
}
