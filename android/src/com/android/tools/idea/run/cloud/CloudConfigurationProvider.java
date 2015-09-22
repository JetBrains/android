/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.run.cloud;


import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.cloud.CloudConfiguration.Kind;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
 * {@link CloudConfigurationProvider} allows providers of cloud execution support to plug into Android Studio.
 * A cloud provider can run a given android project on the cloud on a specific cloud project and a matrix of configurations
 * on which the tests need to be run. Studio provides the UI that allows the selection of a specific cloud project, and a
 * specific configuration, and then passes on that information to the cloud provider in order to run the tests.
 */
public abstract class CloudConfigurationProvider {

  // If this JVM option is present and set to true, enable the plugin regardless of the Settings dialog enable property's value.
  private static final String EXTERNAL_ENABLE_FLAG = "enable.google.cloud.testing.plugin";

  public static final ExtensionPointName<CloudConfigurationProvider> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.run.cloudConfigurationProvider");

  /**
   * Returns a list of device configurations supported by this provider for a given android project. The list typically contains a set of
   * default configurations applicable for the project, and custom configurations specifically created by the user for that project.
   * */
  @NotNull
  public abstract List<? extends CloudConfiguration> getCloudConfigurations(@NotNull AndroidFacet facet,
                                                                            @NotNull Kind configurationKind);

  /** Shows a dialog that allows specifying a set of device configurations and returns the selected configuration.
   * Uses {@code kind} only if {@code selectedConfig} is {@code null}, otherwise the kind of the dialog matches the kind of
   * {@code selectedConfig}.*/
  @Nullable
  public abstract CloudConfiguration openMatrixConfigurationDialog(@NotNull AndroidFacet facet,
                                                                   @Nullable CloudConfiguration selectedConfig,
                                                                   @NotNull Kind configurationKind);
  /** Returns the cloud project id to use. */
  @Nullable
  public abstract String openCloudProjectConfigurationDialog(@NotNull Project project, @Nullable String currentProject);

  /**
   * A long running operation - returns only after the cloud device is ready (or launching has failed).
   */
  public abstract void launchCloudDevice(int selectedConfigurationId,
                                         @NotNull String cloudProjectId,
                                         @NotNull AndroidFacet facet);

  @NotNull
  public abstract ExecutionResult executeCloudMatrixTests(int selectedConfigurationId,
                                                          @NotNull String cloudProjectId,
                                                          @NotNull CloudMatrixTestRunningState runningState,
                                                          @NotNull Executor executor) throws ExecutionException;

  public static boolean isEnabled() {
    return Boolean.getBoolean(EXTERNAL_ENABLE_FLAG) || CloudTestingConfigurable.getPersistedEnableProperty();
  }

  /**
   * Returns whether there exists a provider that can currently be enabled.
   */
  public static boolean canEnable() {
    CloudConfigurationProvider provider = getExtension();
    if (provider != null) {
      return provider.canBeEnabled();
    }
    return false;
  }

  /**
   * Returns whether this provider can currently be enabled.
   * This could, for example, query the cloud to determine this result.
   */
  protected abstract boolean canBeEnabled();

  @NotNull
  public abstract Collection<IDevice> getLaunchingCloudDevices();

  @Nullable
  public abstract Icon getCloudDeviceIcon();

  /**
   * Returns {@code null} if the provided device is not a cloud device.
   */
  @Nullable
  public abstract String getCloudDeviceConfiguration(IDevice device);

  @Nullable
  public static CloudConfigurationProvider getCloudConfigurationProvider() {
    if (!isEnabled()) {
      return null;
    }
    return getExtension();
  }

  @Nullable
  private static CloudConfigurationProvider getExtension() {
    CloudConfigurationProvider[] extensions = EP_NAME.getExtensions();
    if (extensions.length > 0) {
      return extensions[0];
    }
    return null;
  }
}
