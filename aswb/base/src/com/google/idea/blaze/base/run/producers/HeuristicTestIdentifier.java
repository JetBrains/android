/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.producers;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.ExecutorType;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * A rough heuristic to recognize test files without resolving PSI elements.
 *
 * <p>Used by run configuration producers in situations where it's expensive to resolve PSI (e.g.
 * for files outside the project).
 */
public interface HeuristicTestIdentifier {

  ExtensionPointName<HeuristicTestIdentifier> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.HeuristicTestIdentifier");

  /**
   * Returns the {@link ExecutorType}s relevant for this file path, or an empty set if the file path
   * doesn't appear to be runnable.
   *
   * <p>A best effort, rough heuristic based on the file name + path.
   *
   * <p>This method is run frequently on the EDT, so must be fast.
   */
  ImmutableSet<ExecutorType> supportedExecutors(WorkspacePath path);
}
