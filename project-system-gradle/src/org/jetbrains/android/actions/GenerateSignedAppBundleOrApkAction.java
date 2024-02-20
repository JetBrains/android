/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.actions;

import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class GenerateSignedAppBundleOrApkAction extends AnAction {

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public GenerateSignedAppBundleOrApkAction() {
    super(AndroidBundle.message("android.generate.signed.app.bundle.or.apk.action.text"));
  }

  @VisibleForTesting
  static boolean allowBundleSigning(@Nullable Project project) {
    return project != null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    List<AndroidFacet> facets =
      ProjectSystemUtil.getAndroidFacets(project).stream().filter(facet -> facet.getConfiguration().isAppProject())
        .collect(Collectors.toList());

    assert !facets.isEmpty();

    ExportSignedPackageWizard wizard = new ExportSignedPackageWizard(project, facets);
    wizard.show();
  }

  private static boolean hasAtLeastOneApp(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (ModuleSystemUtil.androidProjectType(module) == AndroidModuleSystem.Type.TYPE_APP) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    boolean enabled = project != null && hasAtLeastOneApp(project) &&
                      /* Available for Android Gradle projects only */
                      (ProjectSystemUtil.getProjectSystem(project) instanceof GradleProjectSystem);
    e.getPresentation().setEnabledAndVisible(enabled);
    if (enabled) {
      e.getPresentation().setText(AndroidBundle.message("android.generate.signed.app.bundle.or.apk.action.text"));
    }
  }
}
