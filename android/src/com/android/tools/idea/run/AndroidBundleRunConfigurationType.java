/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AndroidBundleRunConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory = new AndroidRunBundleConfigurationFactory(this);

  public static class AndroidRunBundleConfigurationFactory extends ConfigurationFactory {
    protected AndroidRunBundleConfigurationFactory(@NotNull ConfigurationType type) {
      super(type);
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new AndroidBundleRunConfiguration(project, this);
    }

    @Override
    public boolean canConfigurationBeSingleton() {
      return false;
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID);
    }

    @Override
    public void configureBeforeRunTaskDefaults(Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
      // Disable the default Make compile step for this run configuration type
      if (CompileStepBeforeRun.ID.equals(providerID)) {
        task.setEnabled(false);
      }
    }
  }

  @NotNull
  public static AndroidBundleRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AndroidBundleRunConfigurationType.class);
  }

  /**
   * Returns the display name of the configuration type, but also the id of the configuration factory
   * (see {@link ConfigurationFactory#getId()}).
   */
  @Override
  public String getDisplayName() {
    return AndroidBundle.message("android.run.bundle.configuration.type.name");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return AndroidBundle.message("android.run.bundle.configuration.type.description");
  }

  @Override
  public Icon getIcon() {
    return AndroidIcons.AndroidModule;
  }

  /**
   * Returns the ID of the configuration type. This ID is persisted in the workspace.xml file,
   * so it can never be changed.
   */
  @Override
  @NotNull
  public String getId() {
    return AndroidBundleRunConfigurationType.class.getSimpleName();
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public ConfigurationFactory getFactory() {
    return myFactory;
  }
}