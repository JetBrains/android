/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Traverse the dependency graph searching for kotlinx coroutines library in the transitive
 * dependencies of the target to debug.
 */
@Service
public final class KotlinProjectTraversingService {

  private KotlinProjectTraversingService() {}

  public static KotlinProjectTraversingService getInstance() {
    return ApplicationManager.getApplication().getService(KotlinProjectTraversingService.class);
  }

  public boolean dependsOnKotlinxCoroutinesLib(BlazeCommandRunConfiguration configuration) {
    return doFind(
            configuration,
            (label, coroutinesLibFinder) ->
                coroutinesLibFinder.dependsOnKotlinxCoroutines(
                    configuration.getProject(),
                    com.google.idea.blaze.common.Label.of(label.toString())))
        .orElse(false);
  }

  private <T> Optional<T> doFind(
      BlazeCommandRunConfiguration configuration,
      BiFunction<Label, KotlinxCoroutinesLibFinder, T> fn) {
    Optional<Label> label = getSingleTarget(configuration);
    if (label.isEmpty()) {
      notify(
          "Cannot view coroutines debugging panel: configuration should have a single target"
              + " label");
      return Optional.empty();
    }

    Optional<KotlinxCoroutinesLibFinder> coroutinesLibFinder =
        getApplicableCoroutinesLibFinder(configuration.getProject());

    if (coroutinesLibFinder.isEmpty()) {
      notify(
          "Cannot view coroutines debugging panel: no applicable KotlinxCoroutinesLibFinder"
              + " EP found.");
      return Optional.empty();
    }
    return Optional.of(fn.apply(label.get(), coroutinesLibFinder.get()));
  }

  /** Returns the applicable {@code KotlinCoroutinesLibFinder} based on the project build system. */
  private static Optional<KotlinxCoroutinesLibFinder> getApplicableCoroutinesLibFinder(
      Project project) {
    return KotlinxCoroutinesLibFinder.EP_NAME.getExtensionList().stream()
        .filter(f -> f.isApplicable(project))
        .findFirst();
  }

  /** Return the Label of the target to debug. */
  private static Optional<Label> getSingleTarget(BlazeCommandRunConfiguration config) {
    String pattern = config.getSingleTargetPattern();
    if (pattern == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(Label.createIfValid(pattern));
  }

  private static void notify(String content) {
    Notifications.Bus.notify(
        NotificationGroupManager.getInstance()
            .getNotificationGroup("KotlinDebuggerNotification")
            .createNotification(content, NotificationType.INFORMATION));
  }
}
