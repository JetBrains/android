/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.idea.blaze.android.run.binary.mobileinstall;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import javax.annotation.Nullable;

/** Setup mobile-install specific ADB connection settings. */
public interface AdbTunnelConfigurator extends AutoCloseable {
  /** Setup SSH tunnel if required. */
  void setupConnection(BlazeContext context);

  /** Tear down SSH tunnel if one was setup. */
  void tearDownConnection();

  /**
   * Returns the port to be used by mobile-install for deploying app to device. Caller should first
   * use {@link #isActive()} to check if the tunnel is active before getting ADB port.
   *
   * @throws IllegalStateException if there isn't a SSH tunnel setup.
   */
  int getAdbServerPort();

  /**
   * Returns true if the SSH tunnel is active and mobile-install should use the port from {@link
   * #getAdbServerPort()}.
   */
  boolean isActive();

  @Override
  default void close() {
    tearDownConnection();
  }

  /**
   * Provider of new {@link AdbTunnelConfigurator} instances.
   *
   * <p>The extension point is bound to providers instead of the configurators because the
   * configurators are single-use-per-build. This allows the configurators to keep context for the
   * build they are called for and avoid messy reuse logic.
   */
  interface AdbTunnelConfiguratorProvider {
    ExtensionPointName<AdbTunnelConfiguratorProvider> EP_NAME =
        ExtensionPointName.create("com.google.idea.blaze.AdbTunnelConfiguratorProvider");

    @Nullable
    AdbTunnelConfigurator createConfigurator(BlazeContext context);
  }
}
