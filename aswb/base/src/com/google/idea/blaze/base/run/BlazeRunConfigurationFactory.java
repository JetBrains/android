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

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/** A factory creating run configurations based on Blaze targets. */
public abstract class BlazeRunConfigurationFactory {
  public static final ExtensionPointName<BlazeRunConfigurationFactory> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.RunConfigurationFactory");

  /** Returns whether this factory can handle a target. */
  public abstract boolean handlesTarget(
      Project project, BlazeProjectData blazeProjectData, Label label);

  /** Returns whether this factory is compatible with the given run configuration type. */
  public final boolean handlesConfiguration(RunConfiguration configuration) {
    return getConfigurationFactory().getType().equals(configuration.getType());
  }

  /** Constructs and initializes {@link RunnerAndConfigurationSettings} for the given rule. */
  public RunnerAndConfigurationSettings createForTarget(
      Project project, RunManager runManager, Label target) {
    ConfigurationFactory factory = getConfigurationFactory();
    RunConfiguration configuration = factory.createTemplateConfiguration(project, runManager);
    setupConfiguration(configuration, target);
    return runManager.createConfiguration(configuration, factory);
  }

  /** The factory used to create configurations. */
  protected abstract ConfigurationFactory getConfigurationFactory();

  /** Initialize the configuration for the given target. */
  public abstract void setupConfiguration(RunConfiguration configuration, Label target);
}
