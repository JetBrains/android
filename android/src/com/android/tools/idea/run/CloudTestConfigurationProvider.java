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
package com.android.tools.idea.run;


import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.run.AndroidRunningState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link CloudTestConfigurationProvider} allows providers of cloud testing support to plug into Android Studio.
 * A cloud provider can run a given android project on the cloud on a specific cloud project and a matrix of configurations
 * on which the tests need to be run. Studio provides the UI that allows the selection of a specific cloud project, and a
 * specific configuration, and then passes on that information to the cloud provider in order to run the tests.
 */
public abstract class CloudTestConfigurationProvider {
  public static final boolean SHOW_CLOUD_TESTING_OPTION = Boolean.getBoolean("show.google.cloud.testing.option");

  public static final ExtensionPointName<CloudTestConfigurationProvider> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.run.cloudTestingConfigurationProvider");

  /**
   * Returns a list of device configurations supported by this provider for a given android project. The list typically contains a set of
   * default configurations applicable for the project, and custom configurations specifically created by the user for that project.
   * */
  @NotNull
  public abstract List<? extends CloudTestConfiguration> getTestingConfigurations(@NotNull AndroidFacet facet);

  /** Shows a dialog that allows specifying a set of device configurations and returns the selected configuration. */
  @Nullable
  public abstract CloudTestConfiguration openMatrixConfigurationDialog(@NotNull AndroidFacet facet,
                                                                       @NotNull CloudTestConfiguration selectedConfig);

  /** Returns the cloud project id to use. */
  @Nullable
  public abstract String openCloudProjectConfigurationDialog(@NotNull Project project, @Nullable String currentProject);

  public abstract boolean supportsDebugging();

  @NotNull
  public abstract ExecutionResult execute(int selectedConfigurationId,
                                          @NotNull String cloudProjectId,
                                          @NotNull AndroidRunningState runningState,
                                          @NotNull Executor executor) throws ExecutionException;

  @Nullable
  public static CloudTestConfigurationProvider getCloudTestingProvider() {
    if (SHOW_CLOUD_TESTING_OPTION) {
      CloudTestConfigurationProvider[] extensions = EP_NAME.getExtensions();
      if (extensions.length > 0) {
        return extensions[0];
      }
    }

    return null;
  }
}
