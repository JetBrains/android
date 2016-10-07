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

import com.android.tools.idea.startup.AndroidStudioInitializer;
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

public class AndroidRunConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory = new AndroidRunConfigurationFactory(this);

  public static class AndroidRunConfigurationFactory extends ConfigurationFactory {
    protected AndroidRunConfigurationFactory(@NotNull ConfigurationType type) {
      super(type);
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new AndroidRunConfiguration(project, this);
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
      // Under Android Studio, disable the default Make compile step for this run configuration type
      if (AndroidStudioInitializer.isAndroidStudio() && CompileStepBeforeRun.ID.equals(providerID)) {
        task.setEnabled(false);
      }
    }
  }

  public static AndroidRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AndroidRunConfigurationType.class);
  }

  @Override
  public String getDisplayName() {
    return AndroidBundle.message("android.run.configuration.type.name");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return AndroidBundle.message("android.run.configuration.type.description");
  }

  @Override
  public Icon getIcon() {
    return AndroidIcons.AndroidModule;
  }

  @Override
  @NotNull
  public String getId() {
    return "AndroidRunConfigurationType";
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public ConfigurationFactory getFactory() {
    return myFactory;
  }
}
