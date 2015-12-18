/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.idea.fd.FastDeployManager;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class DeployDexPatchTask implements LaunchTask {
  @NotNull private final AndroidFacet myFacet;

  public DeployDexPatchTask(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Installing Dex files";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.DEPLOY_DEXPATCH;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    boolean result = FastDeployManager.installDex(myFacet, device);
    if (!result) {
      printer.stderr("Pushing incremental patch to the device failed; you might need to do a clean build.");
    }

    return result;
  }
}
