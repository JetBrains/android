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

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer;
import com.android.tools.idea.gradle.dependency.Dependency;
import com.android.tools.idea.gradle.dependency.DependencySet;
import com.android.tools.idea.gradle.dependency.LibraryDependency;
import com.android.tools.idea.gradle.dependency.ModuleDependency;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.google.common.base.Objects;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.FAILED_TO_SET_UP_DEPENDENCIES;

/**
 * Sets the dependencies of a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class DependenciesModuleCustomizer extends AbstractDependenciesModuleCustomizer<IdeaAndroidProject> {
  private static final Logger LOG = Logger.getInstance(AbstractDependenciesModuleCustomizer.class);

  @Override
  protected void setUpDependencies(@NotNull ModifiableRootModel model,
                                   @NotNull IdeaAndroidProject androidProject,
                                   @NotNull List<Message> errorsFound) {
    DependencySet dependencies = Dependency.extractFrom(androidProject);
    for (LibraryDependency dependency : dependencies.onLibraries()) {
      updateDependency(model, dependency);
    }
    for (ModuleDependency dependency : dependencies.onModules()) {
      updateDependency(model, dependency, errorsFound);
    }

    Collection<String> unresolvedDependencies = androidProject.getDelegate().getUnresolvedDependencies();
    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(model.getProject());
    messages.reportUnresolvedDependencies(unresolvedDependencies);
  }

  private void updateDependency(@NotNull ModifiableRootModel model, @NotNull LibraryDependency dependency) {
    Collection<String> binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
    setUpLibraryDependency(model, dependency.getName(), dependency.getScope(), binaryPaths);
  }

  private void updateDependency(@NotNull ModifiableRootModel model,
                                @NotNull ModuleDependency dependency,
                                @NotNull List<Message> errorsFound) {
    ModuleManager moduleManager = ModuleManager.getInstance(model.getProject());
    Module moduleDependency = null;
    for (Module module : moduleManager.getModules()) {
      AndroidGradleFacet androidGradleFacet = AndroidGradleFacet.getInstance(module);
      if (androidGradleFacet != null) {
        String gradlePath = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
        if (Objects.equal(gradlePath, dependency.getGradlePath())) {
          moduleDependency = module;
          break;
        }
      }
    }
    if (moduleDependency != null) {
      ModuleOrderEntry orderEntry = model.addModuleOrderEntry(moduleDependency);
      orderEntry.setExported(true);
      return;
    }

    LibraryDependency backup = dependency.getBackupDependency();
    boolean hasLibraryBackup = backup != null;
    String msg = String.format("Unable to find module with Gradle path '%1$s'.", dependency.getGradlePath());

    Message.Type type = Message.Type.ERROR;
    if (hasLibraryBackup) {
      msg += String.format(" Linking to library '%1$s' instead.", backup.getName());
      type = Message.Type.WARNING;
    }

    LOG.info(msg);

    errorsFound.add(new Message(FAILED_TO_SET_UP_DEPENDENCIES, type, msg));

    // fall back to library dependency, if available.
    if (hasLibraryBackup) {
      updateDependency(model, backup);
    }
  }
}
