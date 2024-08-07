/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import icons.BlazeIcons;
import javax.swing.Icon;

/** A type for run configurations that execute Blaze commands. */
public class BlazeCommandRunConfigurationType implements ConfigurationType {

  private final BlazeCommandRunConfigurationFactory factory =
      new BlazeCommandRunConfigurationFactory(this);

  /** A run configuration factory */
  public static class BlazeCommandRunConfigurationFactory extends ConfigurationFactory {
    private BlazeCommandRunConfigurationFactory(ConfigurationType type) {
      super(type);
    }

    @Override
    public String getId() {
      // must be left unchanged for backwards compatibility
      return getName();
    }

    @Override
    public boolean isApplicable(Project project) {
      return Blaze.isBlazeProject(project);
    }

    @Override
    public BlazeCommandRunConfiguration createTemplateConfiguration(Project project) {
      return new BlazeCommandRunConfiguration(project, this, "Unnamed");
    }

    @Override
    @SuppressWarnings({"rawtypes", "RedundantSuppression"}) // Super method uses raw BeforeRunTask.
    public void configureBeforeRunTaskDefaults(
        Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
      task.setEnabled(providerID.equals(BlazeBeforeRunTaskProvider.ID));
    }
  }

  public static BlazeCommandRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(BlazeCommandRunConfigurationType.class);
  }

  @Override
  public String getDisplayName() {
    return Blaze.defaultBuildSystemName() + " Command";
  }

  @Override
  public String getConfigurationTypeDescription() {
    return String.format(
        "Configuration for launching arbitrary %s commands.", Blaze.guessBuildSystemName());
  }

  @Override
  public Icon getIcon() {
    return BlazeIcons.Logo;
  }

  @Override
  public String getId() {
    return "BlazeCommandRunConfigurationType";
  }

  @Override
  public BlazeCommandRunConfigurationFactory[] getConfigurationFactories() {
    return new BlazeCommandRunConfigurationFactory[] {factory};
  }

  public BlazeCommandRunConfigurationFactory getFactory() {
    return factory;
  }
}
