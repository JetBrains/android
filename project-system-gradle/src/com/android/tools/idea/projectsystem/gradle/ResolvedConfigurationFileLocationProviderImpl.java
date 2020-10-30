// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.projectsystem.gradle;

import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.projectsystem.AndroidProjectRootUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleModuleModel;

public // FIXME-ank4: public only because it is instantiated via reflection in `GradleModelSource`
class ResolvedConfigurationFileLocationProviderImpl
  implements BuildModelContext.ResolvedConfigurationFileLocationProvider {

  @Nullable
  @Override
  public VirtualFile getGradleBuildFile(@NotNull Module module) {
    GradleModuleModel moduleModel = getGradleModuleModel(module);
    if (moduleModel != null) {
      return moduleModel.getBuildFile();
    }
    return null;
  }

  @Nullable
  @Override
  public @SystemIndependent String getGradleProjectRootPath(@NotNull Module module) {
    return AndroidProjectRootUtil.getModuleDirPath(module);
  }

  @Nullable
  @Override
  public @SystemIndependent String getGradleProjectRootPath(@NotNull Project project) {
    VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir == null) return null;
    return projectDir.getPath();
  }
}
