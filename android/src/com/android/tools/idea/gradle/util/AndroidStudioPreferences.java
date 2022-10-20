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
package com.android.tools.idea.gradle.util;

import static com.intellij.openapi.options.Configurable.PROJECT_CONFIGURABLE;

import com.android.tools.idea.IdeInfo;
import com.google.common.collect.Lists;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class AndroidStudioPreferences {
  private static final List<String> PROJECT_PREFERENCES_TO_REMOVE = Lists.newArrayList(
    "org.intellij.lang.xpath.xslt.associations.impl.FileAssociationsConfigurable", "com.intellij.uiDesigner.GuiDesignerConfigurable",
    "org.jetbrains.plugins.groovy.gant.GantConfigurable", "org.jetbrains.plugins.groovy.compiler.GroovyCompilerConfigurable",
    "org.jetbrains.idea.maven.utils.MavenSettings",
    "com.intellij.compiler.options.CompilerConfigurable", "org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerConfigurableTab"
  );

  /**
   * Disables all settings that we don't require in Android Studio.
   */
  public static void cleanUpPreferences(@NotNull Project project) {
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      // These should all be left intact when running as part of IDEA.
      return;
    }

    // This option currently causes issues with external Gradle builds (see https://issuetracker.google.com/issues/183632446)
    // This option can not be set in Android Studio, this is to disable already set configurations.
    CompilerWorkspaceConfiguration.getInstance(project).MAKE_PROJECT_ON_SAVE = false;

    ExtensionPoint<ConfigurableEP<Configurable>> projectConfigurable = PROJECT_CONFIGURABLE.getPoint(project);

    List<ConfigurableEP<Configurable>> nonStudioExtensions = new ArrayList<>();
    for (ConfigurableEP<Configurable> extension : projectConfigurable.getExtensionList()) {
      if (PROJECT_PREFERENCES_TO_REMOVE.contains(extension.instanceClass)) {
        nonStudioExtensions.add(extension);
      }
    }

    projectConfigurable.unregisterExtensions(ep -> nonStudioExtensions.contains(ep));
  }
}
