/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.cppimpl.debug;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebuggerManagerListener;
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * Warns the user when they start a native debug session if the IDE is configured to prefer IPv6, as
 * native debugging components (LLDBFrontend, ProtobufServer) currently hardcode IPv4.
 */
public class NativeDebuggerIpv6WarningListener implements XDebuggerManagerListener {

  @VisibleForTesting
  static Supplier<Boolean> ipv6Detector =
      () -> {
        try {
          // If localhost resolves to IPv6, then IPv6 is preferred/active for local connections.
          return !(InetAddress.getByName("localhost") instanceof Inet4Address);
        } catch (UnknownHostException e) {
          return false;
        }
      };

  @Override
  public void processStarted(@NotNull XDebugProcess debugProcess) {
    Project project = debugProcess.getSession().getProject();
    if (!Blaze.isBlazeProject(project)) {
      return;
    }
    if (!(debugProcess instanceof CidrDebugProcess)) {
      return;
    }
    if (ipv6Detector.get()) {
      showWarning(project);
    }
  }

  private void showWarning(Project project) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Native Debugger Warning")
        .createNotification(
            "Native Debugger IPv6 Warning",
            "Native debugging (LLDB) may fail to connect because the IDE is configured to prefer"
                + " IPv6, but some native debugging components are restricted to IPv4. If you"
                + " experience connection issues, consider setting"
                + " -Djava.net.preferIPv6Addresses=false in your IDE's VM options.",
            NotificationType.WARNING)
        .notify(project);
  }
}
