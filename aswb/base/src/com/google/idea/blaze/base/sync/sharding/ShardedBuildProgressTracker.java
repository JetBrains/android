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
package com.google.idea.blaze.base.sync.sharding;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.StateUpdate;
import com.intellij.openapi.util.text.StringUtil;

/**
 * Tracks the state of a sharded build.
 *
 * <p>Keep track of the number of completed & in progress builds, and send a StateOutput to the
 * context whenever it changes.
 */
public final class ShardedBuildProgressTracker {

  private final int totalShards;

  @GuardedBy("this")
  private int buildsInProgress;

  @GuardedBy("this")
  private int buildsCompleted;

  public ShardedBuildProgressTracker(int totalShards) {
    this.totalShards = totalShards;
  }

  public synchronized void onBuildStarted(BlazeContext context) {
    buildsInProgress++;
    sendUpdate(context);
  }

  public synchronized void onBuildCompleted(BlazeContext context) {
    buildsCompleted++;
    buildsInProgress--;
    sendUpdate(context);
  }

  private synchronized void sendUpdate(BlazeContext context) {
    context.output(
        new StateUpdate(makeStateString(totalShards, buildsCompleted, buildsInProgress)));
  }

  private static String makeStateString(
      int totalShards, int buildsCompleted, int buildsInProgress) {
    if (buildsCompleted == totalShards) {
      return String.format(
          "%d %s complete", buildsCompleted, StringUtil.pluralize("shard", buildsCompleted));
    }
    if (buildsInProgress == totalShards) {
      return String.format(
          "%d %s running", buildsInProgress, StringUtil.pluralize("shard", buildsInProgress));
    }
    return String.format(
        "%d %s: %d complete, %d running",
        totalShards, StringUtil.pluralize("shard", totalShards), buildsCompleted, buildsInProgress);
  }
}
