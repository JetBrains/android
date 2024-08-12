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
package com.google.idea.blaze.android.sync;


import com.android.tools.idea.model.AndroidModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.projectview.AndroidMinSdkSection;
import com.google.idea.blaze.android.projectview.AndroidSdkPlatformSection;
import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.sync.importer.BlazeAndroidWorkspaceImporter;
import com.google.idea.blaze.android.sync.importer.BlazeImportInput;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer;
import com.google.idea.blaze.android.sync.sdk.AndroidSdkFromProjectView;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SourceFolderProvider;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.java.sync.JavaLanguageLevelHelper;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.projectstructure.JavaSourceFolderProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ui.UIUtil;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** ASwB sync plugin. */
public class BlazeAndroidSyncPlugin implements BlazeSyncPlugin {

  @Override
  public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of(WorkspaceType.ANDROID);
  }

  @Nullable
  @Override
  public WorkspaceType getDefaultWorkspaceType() {
    return WorkspaceType.ANDROID;
  }

  @Nullable
  @Override
  public ModuleType getWorkspaceModuleType(WorkspaceType workspaceType) {
    if (workspaceType == WorkspaceType.ANDROID) {
      return StdModuleTypes.JAVA;
    }
    return null;
  }

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    if (workspaceType != WorkspaceType.ANDROID) {
      return ImmutableSet.of();
    }
    return ImmutableSet.of(LanguageClass.ANDROID, LanguageClass.JAVA, LanguageClass.C);
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
    if (!isAndroidWorkspace(workspaceLanguageSettings)) {
      return;
    }

    // Non-build syncs don't produce changes to android IDE data (e.g. resources, aars, etc).
    // Pass on the previous android sync data instead since it will be the same.
    if (!syncMode.involvesBlazeBuild()) {
      if (previousSyncState != null) {
        BlazeAndroidSyncData previousSyncData = previousSyncState.get(BlazeAndroidSyncData.class);
        if (previousSyncData != null) {
          syncStateBuilder.put(previousSyncData);
        }
      }
      return;
    }

    AndroidSdkPlatform androidSdkPlatform =
        AndroidSdkFromProjectView.getAndroidSdkPlatform(context, projectViewSet);

    BlazeImportInput inputs =
        BlazeImportInput.forProject(
            project, workspaceRoot, projectViewSet, targetMap, artifactLocationDecoder);

    BlazeAndroidWorkspaceImporter workspaceImporter =
        new BlazeAndroidWorkspaceImporter(project, context, inputs);
    BlazeAndroidImportResult importResult =
        Scope.push(
            context,
            (childContext) -> {
              childContext.push(new TimingScope("AndroidWorkspaceImporter", EventType.Other));
              return workspaceImporter.importWorkspace();
            });
    syncStateBuilder.put(new BlazeAndroidSyncData(importResult, androidSdkPlatform));
  }

  @Override
  public void updateProjectSdk(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      BlazeProjectData blazeProjectData) {
    if (!isAndroidWorkspace(blazeProjectData.getWorkspaceLanguageSettings())) {
      return;
    }
    AndroidSdkPlatform androidSdkPlatform = null;
    BlazeAndroidSyncData syncData = blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    if (syncData != null) {
      androidSdkPlatform = syncData.androidSdkPlatform;
    } else if (ProjectRootManagerEx.getInstanceEx(project).getProjectSdk() == null) {
      // If syncData is null then this could have been a directory only sync.  In this case,
      // calculate
      // the androidSdkPlatform directly from project view if the project SDK is not yet set.
      // This ensures the android SDK is available even if the initial project sync fails or simply
      // takes too long.
      androidSdkPlatform = AndroidSdkFromProjectView.getAndroidSdkPlatform(context, projectViewSet);
    }
    if (androidSdkPlatform == null) {
      IssueOutput.error(
              "No android SDK configured. Please ensure the SDK defined under"
                  + " \"android_sdk_platform\" attribute points to a valid SDK. Android"
                  + " Studio functionalities will fail without the SDK.")
          .submit(context);
      return;
    }
    Sdk sdk = BlazeSdkProvider.getInstance().findSdk(androidSdkPlatform.androidSdk);
    if (sdk == null) {
      IssueOutput.error(
              String.format("Android platform '%s' not found.", androidSdkPlatform.androidSdk))
          .submit(context);
      return;
    }

    LanguageLevel javaLanguageLevel =
        JavaLanguageLevelHelper.getJavaLanguageLevel(projectViewSet, blazeProjectData);
    setProjectSdkAndLanguageLevel(project, sdk, javaLanguageLevel);
  }

  @Override
  public void updateProjectStructure(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      @Nullable BlazeProjectData oldBlazeProjectData,
      ModuleEditor moduleEditor,
      Module workspaceModule,
      ModifiableRootModel workspaceModifiableModel) {
    BlazeAndroidProjectStructureSyncer.updateProjectStructure(
        project,
        context,
        projectViewSet,
        blazeProjectData,
        oldBlazeProjectData,
        moduleEditor,
        workspaceModule,
        workspaceModifiableModel,
        isAndroidWorkspace(blazeProjectData.getWorkspaceLanguageSettings()));
  }

  @Override
  public void updateInMemoryState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Module workspaceModule,
      SyncMode syncMode) {
    ApplicationManager.getApplication()
        .runReadAction(
            () ->
                BlazeAndroidProjectStructureSyncer.updateInMemoryState(
                    project,
                    context,
                    workspaceRoot,
                    projectViewSet,
                    blazeProjectData,
                    workspaceModule,
                    isAndroidWorkspace(blazeProjectData.getWorkspaceLanguageSettings())));
  }

  @Nullable
  @Override
  public SourceFolderProvider getSourceFolderProvider(BlazeProjectData projectData) {
    if (!projectData.getWorkspaceLanguageSettings().isWorkspaceType(WorkspaceType.ANDROID)) {
      return null;
    }
    return new JavaSourceFolderProvider(projectData.getSyncState().get(BlazeJavaSyncData.class));
  }

  @Override
  public boolean validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    if (!isAndroidWorkspace(blazeProjectData.getWorkspaceLanguageSettings())) {
      return true;
    }

    if (blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class) == null) {
      return true;
    }

    boolean valid = true;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && AndroidModel.isRequired(facet) && AndroidModel.get(facet) == null) {
        IssueOutput.error("Android model missing for module: " + module.getName()).submit(context);
        valid = false;
      }
    }
    return valid;
  }

  @Override
  public boolean validateProjectView(
      @Nullable Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    if (!isAndroidWorkspace(workspaceLanguageSettings)) {
      return true;
    }

    if (AndroidSdkFromProjectView.getAndroidSdkPlatform(context, projectViewSet) == null) {
      return false;
    }
    return true;
  }

  private static void setProjectSdkAndLanguageLevel(
      final Project project, final Sdk sdk, final LanguageLevel javaLanguageLevel) {
    UIUtil.invokeAndWaitIfNeeded(
        (Runnable)
            () ->
                ApplicationManager.getApplication()
                    .runWriteAction(
                        () -> {
                          ProjectRootManagerEx rootManager =
                              ProjectRootManagerEx.getInstanceEx(project);
                          rootManager.setProjectSdk(sdk);
                          LanguageLevelProjectExtension ext =
                              LanguageLevelProjectExtension.getInstance(project);
                          ext.setLanguageLevel(javaLanguageLevel);
                        }));
  }

  @Override
  public Collection<SectionParser> getSections() {
    return ImmutableList.of(
        AndroidMinSdkSection.PARSER,
        AndroidSdkPlatformSection.PARSER,
        GeneratedAndroidResourcesSection.PARSER);
  }

  @Nullable
  @Override
  public LibrarySource getLibrarySource(
      ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
    if (!isAndroidWorkspace(blazeProjectData.getWorkspaceLanguageSettings())) {
      return null;
    }
    return new BlazeAndroidLibrarySource(blazeProjectData);
  }

  private static boolean isAndroidWorkspace(WorkspaceLanguageSettings workspaceLanguageSettings) {
    return workspaceLanguageSettings.isWorkspaceType(WorkspaceType.ANDROID);
  }
}
