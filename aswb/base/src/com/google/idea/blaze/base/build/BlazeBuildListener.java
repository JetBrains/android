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
package com.google.idea.blaze.base.build;

import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/** Extension interface to listen for Blaze builds */
public interface BlazeBuildListener {
  ExtensionPointName<BlazeBuildListener> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazeBuildListener");

  /** Called just prior to starting a blaze build */
  default void buildStarting(Project project) {}

  /** Called after blaze build command finishes, and file cache is refreshed */
  default void buildCompleted(Project project, BlazeBuildOutputs buildOutputs) {}
}
