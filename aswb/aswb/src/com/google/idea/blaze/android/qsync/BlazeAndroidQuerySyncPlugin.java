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
package com.google.idea.blaze.android.qsync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.joining;

import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder;
import com.android.tools.idea.projectsystem.ScopeType;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.android.qsync.projectstructure.AndroidFacetModuleCustomizer;
import com.google.idea.blaze.android.resources.BlazeLightResourceClassService;
import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel;
import com.google.idea.blaze.android.sync.sdk.AndroidSdkFromProjectView;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.BuildFlagsSection;
import com.google.idea.blaze.base.qsync.BlazeQuerySyncPlugin;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.java.projectview.JavaLanguageLevelSection;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;

/** ASwB sync plugin. */
public class BlazeAndroidQuerySyncPlugin implements BlazeQuerySyncPlugin {

  @Override
  public void updateProjectSettingsForQuerySync(
      Project project, Context<?> context, ProjectViewSet projectViewSet) {
    if (!isAndroidWorkspace(LanguageSupport.createWorkspaceLanguageSettings(projectViewSet))) {
      return;
    }
    AndroidSdkPlatform androidSdkPlatform =
        AndroidSdkFromProjectView.getAndroidSdkPlatform(context, projectViewSet);
    Sdk sdk = BlazeSdkProvider.getInstance().findSdk(androidSdkPlatform.androidSdk);
    LanguageLevel javaLanguageLevel =
        JavaLanguageLevelSection.getLanguageLevel(projectViewSet, LanguageLevel.JDK_11);
    ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(project);
    rootManager.setProjectSdk(sdk);
    LanguageLevelProjectExtension ext = LanguageLevelProjectExtension.getInstance(project);
    ext.setLanguageLevel(javaLanguageLevel);
  }

  @Override
  public void updateProjectStructureForQuerySync(
      Project project,
      Context<?> context,
      IdeModifiableModelsProvider models,
      WorkspaceRoot workspaceRoot,
      Module workspaceModule,
      Set<String> androidResourceDirectories,
      Set<String> androidSourcePackages,
      WorkspaceLanguageSettings workspaceLanguageSettings) {

    if (!isAndroidWorkspace(workspaceLanguageSettings)) {
      return;
    }

    AndroidFacetModuleCustomizer androidFacetModuleCustomizer =
        new AndroidFacetModuleCustomizer(models);

    // Attach AndroidFacet to workspace modules
    androidFacetModuleCustomizer.createAndroidFacet(workspaceModule, false);

    // Add all source resource directories to this AndroidFacet
    AndroidFacet workspaceFacet = AndroidFacet.getInstance(workspaceModule);
    if (workspaceFacet == null) {
      context.output(
          PrintOutput.error(
              "workspace_type is android, but no android facet present; not configuring android"
                  + " resources"));
      context.output(
          PrintOutput.output(
              "Consider adding \"workspace_type: java\" or similar to your .blazeproject file, or"
                  + " add the android facet in project settings if this is an android project."));
      return;
    }
    ImmutableSet<File> androidResourceDirectoryFiles =
        androidResourceDirectories.stream()
            .map(dir -> new File(workspaceRoot.directory(), dir).getAbsoluteFile())
            .collect(toImmutableSet());
    NamedIdeaSourceProvider sourceProvider =
        NamedIdeaSourceProviderBuilder.create(
                workspaceModule.getName(), VfsUtilCore.fileToUrl(new File("MissingManifest.xml")))
            .withScopeType(ScopeType.MAIN)
            .withResDirectoryUrls(
                ContainerUtil.map(androidResourceDirectoryFiles, VfsUtilCore::fileToUrl))
            .build();

    // Set AndroidModel for this AndroidFacet
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    AndroidSdkPlatform androidSdkPlatform =
        AndroidSdkFromProjectView.getAndroidSdkPlatform(context, projectViewSet);

    BlazeAndroidModel androidModel =
        new BlazeAndroidModel(
            project,
            workspaceRoot.directory(),
            sourceProvider,
            Futures.immediateFuture(":workspace"),
            androidSdkPlatform != null ? androidSdkPlatform.androidMinSdkLevel : 1);
    AndroidModel.set(workspaceFacet, androidModel);

    // Register all source java packages as workspace packages
    BlazeLightResourceClassService.Builder rClassBuilder =
        new BlazeLightResourceClassService.Builder(project);
    rClassBuilder.addWorkspacePackages(androidSourcePackages);
    // TODO(b/283282438): Make Preview work with resources in project's res folder in Query Sync
    BlazeLightResourceClassService.getInstance(project).installRClasses(rClassBuilder);
  }

  private static boolean isAndroidWorkspace(WorkspaceLanguageSettings workspaceLanguageSettings) {
    return workspaceLanguageSettings.isWorkspaceType(WorkspaceType.ANDROID);
  }
}
