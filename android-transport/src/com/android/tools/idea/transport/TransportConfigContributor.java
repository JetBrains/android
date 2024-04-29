/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.transport;

import com.android.annotations.Nullable;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.Transport;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point used by clients of Transport to customize the Transport configuration.
 */
public interface TransportConfigContributor {
  ExtensionPointName<TransportConfigContributor> EP_NAME =
    new ExtensionPointName<>("com.android.tools.idea.transport.transportConfigContributor");

  /**
   * void onTransportThreadStarts(@NotNull IDevice device, @NotNull Common.Device transportDevice);
   * <p>
   * /**
   * Allows for subscribers to customize the Transport pipeline's ServiceProxy before it is fully initialized.
   */
  void customizeProxyService(@NotNull TransportProxy proxy);

  /**
   * Allows for subscribers to customize the daemon config before it is being pushed to the device, which is then used to initialized
   * the transport daemon.
   *
   * @param configBuilder the DaemonConfig.Builder to customize. Note that it is up to the subscriber to not override fields that are set
   *                      in {@link TransportFileManager} which are primarily used for
   *                      establishing connection to the transport daemon and app agent.
   */
  void customizeDaemonConfig(@NotNull Transport.DaemonConfig.Builder configBuilder);

  /**
   * Allows for subscribers to customize the agent config before it is being pushed to the device, which is then used to initialized
   * the transport app agent.
   *
   * @param configBuilder the AgentConifg.Builder to customize. Note that it is up to the subscriber to not override fields that are set
   *                      in {@link TransportFileManager} which are primarily used for
   *                      establishing connection to the transport daemon and app agent.
   * @param runConfig     the run config associated with the current app launch.
   */
  void customizeAgentConfig(@NotNull Agent.AgentConfig.Builder configBuilder, @Nullable AndroidRunConfigurationBase runConfig);
}
