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
package com.google.idea.blaze.python;

import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import javax.annotation.Nullable;

/** Helper methods related to the python SDK. */
public class PySdkUtils {

  /** Find a python SDK associated with a blaze project, or its workspace module. */
  @Nullable
  public static Sdk getPythonSdk(Project project) {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectSdk != null && projectSdk.getSdkType() instanceof PythonSdkType) {
      return projectSdk;
    }
    // look for a SDK associated with a python facet instead.
    return PythonSdkUtil.findPythonSdk(
        ModuleManager.getInstance(project)
            .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME));
  }
}
