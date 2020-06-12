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
package com.android.tools.idea.gradle.project.sync.cleanup;

import static com.android.tools.idea.gradle.util.AndroidStudioPreferences.cleanUpPreferences;
import static com.intellij.openapi.options.Configurable.PROJECT_CONFIGURABLE;

import com.android.tools.idea.IdeInfo;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

final class ProjectPreferencesCleanUpTask extends AndroidStudioCleanUpTask {
  private static final List<String> PROJECT_PREFERENCES_TO_REMOVE = Arrays.asList(
    "org.intellij.lang.xpath.xslt.associations.impl.FileAssociationsConfigurable", "com.intellij.uiDesigner.GuiDesignerConfigurable",
    "org.jetbrains.plugins.groovy.gant.GantConfigurable", "org.jetbrains.plugins.groovy.compiler.GroovyCompilerConfigurable",
    "org.jetbrains.android.compiler.AndroidDexCompilerSettingsConfigurable", "org.jetbrains.idea.maven.utils.MavenSettings",
    "com.intellij.compiler.options.CompilerConfigurable"
  );

  @Override
  void doCleanUp(@NotNull Project project) {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      cleanUpPreferences(PROJECT_CONFIGURABLE.getPoint(project), PROJECT_PREFERENCES_TO_REMOVE);
    }
  }
}
