// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.run;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.*;
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

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new AndroidRunConfiguration(project, this);
    }

    @NotNull
    @Override
    public RunConfigurationSingletonPolicy getSingletonPolicy() {
      return RunConfigurationSingletonPolicy.MULTIPLE_INSTANCE;
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
