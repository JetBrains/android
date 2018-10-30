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
import com.android.tools.deployer.Trace;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;

public class ApplyChangesAction extends DeployAction {
  private static final Logger LOG = Logger.getInstance(ApplyChangesAction.class);

  @Override
  public String getName() {
    return "Apply Changes";
  }

  @Override
  public void deploy(Project project, IDevice device, Deployer deployer, String applicationId, List<File> apkFiles)
    throws DeployerException {
    LOG.info("Applying changes to application: " + applicationId);
    try (Trace trace = Trace.begin("Unified.fullSwap")) {
      deployer.fullSwap(applicationId, getPathsToInstall(apkFiles));
    }
  }
}
