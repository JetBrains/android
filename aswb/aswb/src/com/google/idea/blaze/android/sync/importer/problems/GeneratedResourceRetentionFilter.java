/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.android.sync.importer.problems;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.function.Predicate;

/** Generated resources from common dependencies will be retained if they pass this filter. */
public interface GeneratedResourceRetentionFilter extends Predicate<ArtifactLocation> {
  ExtensionPointName<GeneratedResourceRetentionFilter> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.GeneratedResourceRetentionFilter");

  /** Returns a filter which retains generated resources from common dependencies. */
  static Predicate<ArtifactLocation> getFilter() {
    return EP_NAME
        .extensions()
        .map(it -> (Predicate<ArtifactLocation>) it)
        .reduce(Predicate::or)
        .orElse(artifactLocation -> false);
  }
}
