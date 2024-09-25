/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.sync;

import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.PyIdeInfo.PythonVersion;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import javax.annotation.Nullable;

/** Extension to allow suggestion of Python SDK to use for a particular project */
public abstract class PySdkSuggester {

  public static final ExtensionPointName<PySdkSuggester> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.PySdkSuggester");

  /**
   * Suggests the home path of a python interpreter to use for the given project and python version.
   *
   * @param project the project to suggest for
   * @param version the python version for
   * @return the String path of a compatible python interpreter appropriate for the project, or null
   */
  @Nullable
  protected abstract String suggestPythonHomePath(Project project, PythonVersion version);

  /**
   * Registered Python SDK to use for the given project and python version if this PySdkSuggester
   * can provide one.
   *
   * @param project the project to register an SDK for
   * @param version the python version to register an SDK for
   */
  boolean createSdkIfNeeded(Project project, PythonVersion version) {
    String homePath = suggestPythonHomePath(project, version);
    if (homePath == null) {
      return false;
    }
    Sdk sdk = findPythonSdk(homePath);
    if (sdk != null) {
      return false;
    }
    return SdkConfigurationUtil.createAndAddSDK(homePath, PythonSdkType.getInstance()) != null;
  }

  /**
   * Suggests a registered Python SDK to use for the given project. If the function cannot find a
   * compatible registered SDK, it will return null.
   *
   * @param project the project to suggest the SDK for
   * @param version the python version to suggest an SDK for
   * @return an SDK appropriate for the project, or null
   */
  @Nullable
  Sdk suggestSdk(Project project, PythonVersion version) {
    String homePath = suggestPythonHomePath(project, version);
    if (homePath == null) {
      return null;
    }
    return findPythonSdk(homePath);
  }

  /**
   * This is a mechanism allowing the plugin to migrate the suggested SDK. If a project/facet's
   * PythonSDK is considered deprecated, the sync process will treat it as unset.
   *
   * @param sdk an SDK to check for deprecatedness
   * @return a boolean indicated whether sdk is considered deprecated
   */
  public abstract boolean isDeprecatedSdk(Sdk sdk);

  /** Utility method for PySdkSuggester to resolve a homepath to a registered SDK. */
  @Nullable
  private static Sdk findPythonSdk(String homePath) {
    return PythonSdkUtil.getAllSdks().stream()
        .filter(sdk -> homePath.equals(sdk.getHomePath()))
        .findAny()
        .orElse(null);
  }
}
