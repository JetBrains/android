/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.android;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer;
import com.android.tools.idea.gradle.dependency.*;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.variant.view.BuildVariantModuleCustomizer;
import com.google.common.base.Objects;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

import static com.android.SdkConstants.FD_JARS;
import static com.android.tools.idea.gradle.dependency.LibraryDependency.PathType.BINARY;
import static com.android.tools.idea.gradle.util.FilePaths.findParentContentEntry;
import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.Projects.setModuleCompiledArtifact;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;

/**
 * Sets the dependencies of a module imported from an {@link AndroidProject}.
 */
public class DependenciesModuleCustomizer extends AbstractDependenciesModuleCustomizer<IdeaAndroidProject>
  implements BuildVariantModuleCustomizer<IdeaAndroidProject> {

  @Override
  protected void setUpDependencies(@NotNull Module module,
                                   @NotNull IdeModifiableModelsProvider modelsProvider,
                                   @NotNull IdeaAndroidProject androidProject) {
    DependencySet dependencies = Dependency.extractFrom(androidProject);
    for (LibraryDependency dependency : dependencies.onLibraries()) {
      updateLibraryDependency(module, modelsProvider, dependency, androidProject.getDelegate());
    }
    for (ModuleDependency dependency : dependencies.onModules()) {
      updateModuleDependency(module, modelsProvider, dependency, androidProject.getDelegate());
    }

    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(module.getProject());
    Collection<SyncIssue> syncIssues = androidProject.getSyncIssues();
    if (syncIssues != null) {
      messages.reportSyncIssues(syncIssues, module);
    }
    else {
      Collection<String> unresolvedDependencies = androidProject.getDelegate().getUnresolvedDependencies();
      messages.reportUnresolvedDependencies(unresolvedDependencies, module);
    }
  }

  private void updateModuleDependency(@NotNull Module module,
                                      @NotNull IdeModifiableModelsProvider modelsProvider,
                                      @NotNull ModuleDependency dependency,
                                      @NotNull AndroidProject androidProject) {
    Module moduleDependency = null;
    for (Module m : modelsProvider.getModules()) {
      AndroidGradleFacet androidGradleFacet = AndroidGradleFacet.getInstance(m);
      if (androidGradleFacet != null) {
        String gradlePath = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
        if (Objects.equal(gradlePath, dependency.getGradlePath())) {
          moduleDependency = m;
          break;
        }
      }
    }
    LibraryDependency compiledArtifact = dependency.getBackupDependency();

    if (moduleDependency != null) {
      ModuleOrderEntry orderEntry = modelsProvider.getModifiableRootModel(module).addModuleOrderEntry(moduleDependency);
      orderEntry.setExported(true);

      if (compiledArtifact != null) {
        setModuleCompiledArtifact(moduleDependency, compiledArtifact);
      }
      return;
    }

    String backupName = compiledArtifact != null ? compiledArtifact.getName() : null;

    DependencySetupErrors setupErrors = getSetupErrors(module.getProject());
    setupErrors.addMissingModule(dependency.getGradlePath(), module.getName(), backupName);

    // fall back to library dependency, if available.
    if (compiledArtifact != null) {
      updateLibraryDependency(module, modelsProvider, compiledArtifact, androidProject);
    }
  }

  public static void updateLibraryDependency(@NotNull Module module,
                                             @NotNull IdeModifiableModelsProvider modelsProvider,
                                             @NotNull LibraryDependency dependency,
                                             @NotNull AndroidProject androidProject) {
    Collection<String> binaryPaths = dependency.getPaths(BINARY);
    setUpLibraryDependency(module, modelsProvider, dependency.getName(), dependency.getScope(), binaryPaths);

    File buildFolder = androidProject.getBuildFolder();

    // Exclude jar files that are in "jars" folder in "build" folder.
    // see https://code.google.com/p/android/issues/detail?id=123788
    ContentEntry[] contentEntries = modelsProvider.getModifiableRootModel(module).getContentEntries();
    for (String binaryPath : binaryPaths) {
      File parent = new File(binaryPath).getParentFile();
      if (parent != null && FD_JARS.equals(parent.getName()) && isAncestor(buildFolder, parent, true)) {
        ContentEntry parentContentEntry = findParentContentEntry(parent, contentEntries);
        if (parentContentEntry != null) {
          parentContentEntry.addExcludeFolder(pathToIdeaUrl(parent));
        }
      }
    }
  }

  @Override
  @NotNull
  public ProjectSystemId getProjectSystemId() {
    return GRADLE_SYSTEM_ID;
  }

  @Override
  @NotNull
  public Class<IdeaAndroidProject> getSupportedModelType() {
    return IdeaAndroidProject.class;
  }
}
