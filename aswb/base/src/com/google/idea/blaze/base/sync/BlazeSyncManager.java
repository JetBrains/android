/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync;

import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

/** Manages syncing and its listeners. */
public class BlazeSyncManager {

  private final Project project;
  private static final Logger logger = Logger.getInstance(BlazeSyncManager.class);

  public BlazeSyncManager(Project project) {
    this.project = project;
  }

  public static BlazeSyncManager getInstance(Project project) {
    return project.getService(BlazeSyncManager.class);
  }

  public static void printAndLogError(String errorMessage, Context<?> context) {
    context.output(PrintOutput.error(errorMessage));
    logger.error(errorMessage);
  }
}
