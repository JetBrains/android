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
package com.android.tools.idea.testartifacts.junit;

import com.android.tools.idea.testartifacts.scopes.TestArtifactSearchScopes;
import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Android implementation of {@link JUnitConfiguration} so some behaviors can be overridden.
 */
public class AndroidJUnitConfiguration extends JUnitConfiguration {
  public AndroidJUnitConfiguration(@NotNull String name,
                                   @NotNull Project project,
                                   @NotNull ConfigurationFactory configurationFactory) {
    super(name, project, new JUnitConfiguration.Data() {
      @Override
      public TestSearchScope getScope() {
        TestSearchScope original = super.getScope();
        return configuration -> new SourceScope() {
          @Override
          public GlobalSearchScope getGlobalSearchScope() {
            GlobalSearchScope originalScope = original.getSourceScope(configuration).getGlobalSearchScope();
            for (Module module : configuration.getModules()) {
              TestArtifactSearchScopes scopes = TestArtifactSearchScopes.get(module);
              if (scopes != null) {
                originalScope = originalScope.intersectWith(scopes.getAndroidTestExcludeScope());
              }
            }
            return originalScope;
          }

          @Override
          public Project getProject() {
            return original.getSourceScope(configuration).getProject();
          }

          @Override
          public GlobalSearchScope getLibrariesScope() {
            return original.getSourceScope(configuration).getLibrariesScope();
          }

          @Override
          public Module[] getModulesToCompile() {
            return original.getSourceScope(configuration).getModulesToCompile();
          }
        };
      }
    }, configurationFactory);
  }

  @Override
  public SMTRunnerConsoleProperties createTestConsoleProperties(Executor executor) {
    return new AndroidJUnitConsoleProperties(this, executor);
  }

  @Override
  @NotNull
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<AndroidJUnitConfiguration> group = new SettingsEditorGroup<>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new AndroidJUnitConfigurable(getProject()));
    JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
    return group;
  }
}
