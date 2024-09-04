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
package com.google.idea.blaze.base.util;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Arrays;

/** An extension point that works for only a specific set of build systems. */
public interface BuildSystemExtensionPoint {

  ImmutableSet<BuildSystemName> getSupportedBuildSystems();

  /**
   * Get the bound instance of the given extension point that matches the given build system. Any
   * extension point that calls this is expected to provide one extension for each compatible build
   * system that the plugin supports. Failure to do so will result in an IllegalStateException.
   */
  static <T extends BuildSystemExtensionPoint> T getInstance(
      ExtensionPointName<T> extensionPointName, BuildSystemName buildSystemName) {
    ImmutableSet<T> matching =
        Arrays.stream(extensionPointName.getExtensions())
            .filter(f -> f.getSupportedBuildSystems().contains(buildSystemName))
            .collect(toImmutableSet());
    checkState(
        !matching.isEmpty(), "No %s for build system %s", extensionPointName, buildSystemName);
    checkState(
        matching.size() == 1,
        "Multiple instances of %s for build system %s: %s",
        extensionPointName,
        buildSystemName,
        matching);
    return matching.iterator().next();
  }

  /** Get all the bound instances of the given extension point that match the given build system. */
  static <T extends BuildSystemExtensionPoint> ImmutableList<T> getInstances(
      ExtensionPointName<T> extensionPointName, BuildSystemName buildSystemName) {
    return Arrays.stream(extensionPointName.getExtensions())
        .filter(f -> f.getSupportedBuildSystems().contains(buildSystemName))
        .collect(toImmutableList());
  }
}
