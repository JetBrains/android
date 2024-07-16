/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider;
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager;
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings;

public final class AndroidStudioPreferences {
  private static final List<String> PROJECT_PREFERENCES_TO_REMOVE = Arrays.asList(
    "org.intellij.lang.xpath.xslt.associations.impl.FileAssociationsConfigurable", "com.intellij.uiDesigner.GuiDesignerConfigurable",
    "org.jetbrains.plugins.groovy.gant.GantConfigurable", "org.jetbrains.plugins.groovy.compiler.GroovyCompilerConfigurable",
    "org.jetbrains.idea.maven.utils.MavenSettings",
    "com.intellij.compiler.options.CompilerConfigurable", "org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerConfigurableTab"
  );

  /**
   * Disables all settings that we don't require in Android Studio.
   */
  public static void cleanUpPreferences(@NotNull Project project) {
    // This option currently causes issues with external Gradle builds (see https://issuetracker.google.com/issues/183632446)
    // This option can not be set in Android Studio, this is to disable already set configurations.
    CompilerWorkspaceConfiguration.getInstance(project).MAKE_PROJECT_ON_SAVE = false;

    ExtensionPoint<ConfigurableEP<Configurable>> projectConfigurable = PROJECT_CONFIGURABLE.getPoint(project);

    // Set ExternalSystemProjectTrackerSettings.autoReloadType to none, re-syncing project only if cached data is corrupted, invalid or missing
    ExternalSystemProjectTrackerSettings.getInstance(project).setAutoReloadType(ExternalSystemProjectTrackerSettings.AutoReloadType.NONE);

    // Disable KotlinScriptingSettings.autoReloadConfigurations flag, avoiding unexpected re-sync project with kotlin scripts
    // TODO(b/353550539): this code throws an error with IntelliJ 2024.2 when Kotlin K2 is enabled.
    if (KotlinPluginModeProvider.Companion.isK1Mode()) {
      ScriptDefinitionsManager.Companion.getInstance(project).getAllDefinitions().forEach(scriptDefinition -> {
        KotlinScriptingSettings settings = KotlinScriptingSettings.Companion.getInstance(project);
        if (settings.isScriptDefinitionEnabled(scriptDefinition) && settings.autoReloadConfigurations(scriptDefinition)) {
          settings.setAutoReloadConfigurations(scriptDefinition, false);
        }
      });
    }

    // Note: This unregisters the extensions when the predicate returns False.
    projectConfigurable.unregisterExtensions((s, adapter) -> {
      ConfigurableEP<Configurable> ep = adapter.createInstance(project);
      return ep == null || !PROJECT_PREFERENCES_TO_REMOVE.contains(ep.instanceClass);
    }, false);
  }
}
