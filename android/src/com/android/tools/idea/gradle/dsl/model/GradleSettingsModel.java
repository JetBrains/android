/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.parser.elements.GradlePsiElement;
import com.android.tools.idea.gradle.dsl.parser.GradlePsiFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleSettingsFile;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;

public class GradleSettingsModel extends GradleFileModel {
  private static final String INCLUDE = "include";

  @Nullable
  public static GradleSettingsModel get(@NotNull Project project) {
    VirtualFile file = getGradleSettingsFile(getBaseDirPath(project));
    return file != null ? parseBuildFile(file, project, "settings") : null;
  }

  @NotNull
  public static GradleSettingsModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    GradleSettingsPsiFile settingsFile = new GradleSettingsPsiFile(file, project, moduleName);
    settingsFile.parse();
    return new GradleSettingsModel(settingsFile);
  }

  private GradleSettingsModel(@NotNull GradleSettingsPsiFile parsedModel) {
    super(parsedModel);
  }

  /**
   * Returns the module paths specified by the include statements. Note that these path are not file paths, but instead specify the
   * location of the modules in the project hierarchy. As such, the paths use the ':' character as separator.
   *
   * <p>For example, the path a:b or :a:b represents the module in the directory $projectDir/a/b.
   */
  @Nullable
  public List<String> modulePaths() {
    return myGradlePsiFile.getListProperty(INCLUDE, String.class);
  }

  @NotNull
  public GradleSettingsModel addModulePath(@NotNull String modulePath) {
    if (!modulePath.startsWith(":")) {
      modulePath = ":" + modulePath;
    }
    myGradlePsiFile.addToListProperty(INCLUDE, modulePath);
    return this;
  }

  @NotNull
  public GradleSettingsModel removeModulePath(@NotNull String modulePath) {
    // Try to remove the module path whether it has ":" prefix or not.
    if (!modulePath.startsWith(":")) {
      myGradlePsiFile.removeFromListProperty(INCLUDE, ":" + modulePath);
    }
    myGradlePsiFile.removeFromListProperty(INCLUDE, modulePath);
    return this;
  }

  @NotNull
  public GradleSettingsModel replaceModulePath(@NotNull String oldModulePath, @NotNull String newModulePath) {
    // Try to replace the module path whether it has ":" prefix or not.
    if (!newModulePath.startsWith(":")) {
      newModulePath = ":" + newModulePath;
    }
    if (!oldModulePath.startsWith(":")) {
      myGradlePsiFile.replaceInListProperty(INCLUDE, ":" + oldModulePath, newModulePath);
    }
    myGradlePsiFile.replaceInListProperty(INCLUDE, oldModulePath, newModulePath);
    return this;
  }

  private static class GradleSettingsPsiFile extends GradlePsiFile {
    private GradleSettingsPsiFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
      super(file, project, moduleName);
    }

    @Override
    public void addPsiElement(@NotNull String property, @NotNull GradlePsiElement element) {
      if (property.equals(INCLUDE)) {
        addToPsiLiteralList(property, element);
        return;
      }
      super.addPsiElement(property, element);
    }
  }
}
