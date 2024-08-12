/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.run.debug;

import com.google.errorprone.annotations.FormatMethod;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Optional;

/**
 * Interface for providing the kotlinx coroutines debugging library absolute path to be used as a
 * javaagent for coroutines debugging.
 */
public interface KotlinxCoroutinesDebuggingLibProvider {

  static final ExtensionPointName<KotlinxCoroutinesDebuggingLibProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.kotlinxCoroutinesDebuggingLibProvider");

  Optional<String> getKotlinxCoroutinesDebuggingLib(
      ArtifactLocation coroutinesLibArtifact, BlazeCommandRunConfiguration config);

  boolean isApplicable(Project project);

  @FormatMethod
  default void notify(String format, Object... args) {
    Notifications.Bus.notify(
        NotificationGroupManager.getInstance()
            .getNotificationGroup("KotlinDebuggerNotification")
            .createNotification(String.format(format, args), NotificationType.INFORMATION));
  }
}
