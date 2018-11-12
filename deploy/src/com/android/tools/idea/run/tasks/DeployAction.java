/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.DeployerException;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public abstract class DeployAction {
  /**
   * @return a string representing the human-readable name of this action.
   */
  public abstract String getName();

  /**
   * Deploys changes to the specified project running on the specified device, using the provided Deployer.
   *
   * @param project       the project containing the changes.
   * @param device        the device to deploy to.
   * @param deployer      the deployer which will execute the deployment.
   * @param applicationId the application package to deploy.
   * @param apkFiles      the apk files to deploy.
   */
  abstract void deploy(Project project, IDevice device, Deployer deployer, String applicationId, List<File> apkFiles)
    throws DeployerException;

  // TODO: Update some interfaces to properly take File or ApkFileUnit in place of String, so we don't need to do this.
  protected static final List<String> getPathsToInstall(List<File> apkFiles) {
    return apkFiles.stream().map(File::getPath).collect(Collectors.toList());
  }
}