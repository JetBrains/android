/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.dom.structure.manifest;

import com.android.tools.idea.projectsystem.AndroidIconProviderProjectToken;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.ide.presentation.PresentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import icons.StudioIcons;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ApplicationPresentationProvider extends PresentationProvider<Application> {
  @Nullable
  @Override
  public String getName(Application application) {
    final PsiClass aClass = application.getName().getValue();
    return aClass == null ? null : aClass.getName();
  }

  @Nullable
  @Override
  public Icon getIcon(Application application) {
    // Use Android Studio icons
    Module module = application.getModule();
    if (module != null) {
      Project project = module.getProject();
      AndroidProjectSystem projectSystem = ProjectSystemUtil.getProjectSystem(project);
      AndroidIconProviderProjectToken<AndroidProjectSystem> token =
        AndroidIconProviderProjectToken.getEP_NAME().getExtensionList().stream()
          .filter(it -> it.isApplicable(projectSystem))
          .findFirst().orElse(null);
      if (token != null) {
        return token.getModuleIcon(projectSystem, module);
      }
    }
    return StudioIcons.Shell.Filetree.ANDROID_MODULE;
  }

  @Nullable
  @Override
  public String getTypeName(Application application) {
    return "Application";
  }
}
