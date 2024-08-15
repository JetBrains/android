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

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.common.Label;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Optional;

/** Interface for finding the kotlinx coroutines library based on the project Build system. */
public interface KotlinxCoroutinesLibFinder {

  static final ExtensionPointName<KotlinxCoroutinesLibFinder> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.kotlinxCoroutinesLibFinder");

  Optional<ArtifactLocation> getKotlinxCoroutinesLib(TargetIdeInfo depInfo);

  boolean isApplicable(Project project);

  /** Returns true if {@code label} depends on the kotlinx coroutines library. Query-sync only. */
  boolean dependsOnKotlinxCoroutines(Project project, Label label);
}
