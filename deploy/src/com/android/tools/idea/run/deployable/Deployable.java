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
package com.android.tools.idea.run.deployable;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.DeploymentApplicationService;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

public interface Deployable {
  /**
   * Returns the API level of the device.
   */
  @NotNull
  Future<AndroidVersion> getVersion();

  @NotNull ListenableFuture<@NotNull List<@NotNull Client>> searchClientsForPackageAsync();

  /**
   * @deprecated This is called by the EDT and must execute quickly. The current implementation calls {@link Future#get()} which can block
   * for too long. Use {@link #searchClientsForPackageAsync} instead.
   */
  @Deprecated
  @NotNull
  List<Client> searchClientsForPackage();

  /**
   * @return true if the underlying device is online, or false otherwise.
   */
  boolean isOnline();

  /**
   * @return true if the underlying device is unauthorized, or false otherwise.
   */
  boolean isUnauthorized();

  @NotNull
  static List<Client> searchClientsForPackage(@NotNull IDevice device, @NotNull String packageName) {
    return DeploymentApplicationService.getInstance().findClient(device, packageName);
  }
}
