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
package com.android.tools.idea.run;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.deployable.ApplicationIdResolver;
import com.android.tools.idea.run.deployable.DeviceVersion;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import java.util.List;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

public class DeploymentApplicationService implements Disposable {
  private final ApplicationIdResolver myApplicationIdResolver;
  private final DeviceVersion myDeviceVersion;

  @NotNull
  public static DeploymentApplicationService getInstance() {
    return ApplicationManager.getApplication().getService(DeploymentApplicationService.class);
  }

  private DeploymentApplicationService() {
    myApplicationIdResolver = new ApplicationIdResolver();
    myDeviceVersion = new DeviceVersion();
  }

  @Override
  public void dispose() {
    myDeviceVersion.dispose();
    myApplicationIdResolver.dispose();
  }

  @NotNull
  public List<Client> findClient(@NotNull IDevice iDevice, @NotNull String applicationId) {
    return myApplicationIdResolver.resolve(iDevice, applicationId);
  }

  @NotNull
  public Future<AndroidVersion> getVersion(@NotNull IDevice iDevice) {
    return myDeviceVersion.get(iDevice);
  }
}
