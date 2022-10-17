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
package com.android.tools.idea.gradle.util;

import static com.android.tools.idea.gradle.project.ProjectImportUtil.findGradleTarget;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isExternalSystemAwareModule;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Objects;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Utility methods for {@link Project}s.
 */
public final class GradleProjects {
  private GradleProjects() {
  }

  public static void executeProjectChanges(@NotNull Project project, @NotNull Runnable changes) {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      if (!project.isDisposed()) {
        changes.run();
      }
      return;
    }
    ApplicationManager.getApplication().invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      if (!project.isDisposed()) {
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(changes);
      }
    }));
  }

  public static boolean isOfflineBuildModeEnabled(@NotNull Project project) {
    return GradleSettings.getInstance(project).isOfflineWork();
  }

  /**
   * Indicates whether the given module is the one that represents the project or one of the projects included in the composite build.
   * <p>
   * For example, in this project:
   * <pre>
   * project1
   * - module1
   *   - module1.iml
   * - module2
   *   - module2.iml
   * -project1.iml
   * </pre>
   * "project1" is the module that represents the project.
   * </p>
   *
   * @param module the given module.
   * @return {@code true} if the given module is the one that represents the project, {@code false} otherwise.
   */
  public static boolean isGradleProjectModule(@NotNull Module module) {
    return ":".equals(getGradleModulePath(module));
  }

  /**
   * Returns the gradle path of a module, for example ":app" or ":lib:mylib". In the case of a module from a project included in the
   * composite build, the returned Gradle path is prefixed by the included project name, i.e. included_project:libs:mylib or just
   * included_project for an included build root project.
   * @param module the give module
   * @return the gradle path or {@code null} if not a gradle module or can't find the path.
   */
  @Nullable
  public static String getGradleModulePath(@NotNull Module module) {
    if (!isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
      return null;
    }
    Module holderModule = ModuleSystemUtil.getHolderModule(module);
    ExternalSystemModulePropertyManager moduleProperties = ExternalSystemModulePropertyManager.getInstance(holderModule);
    String linkedProjectId = moduleProperties.getLinkedProjectId();
    if (linkedProjectId == null) {
      return null;
    }
    boolean isRootProject = Objects.equals(moduleProperties.getRootProjectPath(), moduleProperties.getLinkedProjectPath());

    return isRootProject ? ":" : linkedProjectId;
  }

  public static boolean isIdeaAndroidModule(@NotNull Module module) {
    if (GradleFacet.getInstance(module) != null) {
      return true;
    }
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null && AndroidModel.isRequired(androidFacet)) {
      return true;
    }
    return false;
  }

  /**
   * Indicates whether the project in the given folder can be imported as a Gradle project.
   *
   * @param importSource the folder containing the project.
   * @return {@code true} if the project can be imported as a Gradle project, {@code false} otherwise.
   */
  public static boolean canImportAsGradleProject(@NotNull VirtualFile importSource) {
    VirtualFile target = findGradleTarget(importSource);
    return target != null && (GradleConstants.EXTENSION.equals(target.getExtension()) ||
                              target.getName().endsWith(GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION));
  }
}
