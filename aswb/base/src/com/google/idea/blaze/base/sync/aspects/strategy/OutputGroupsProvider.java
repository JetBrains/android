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
package com.google.idea.blaze.base.sync.aspects.strategy;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.intellij.openapi.extensions.ExtensionPointName;

/** Allows specifying output groups in addition to the ones calculated by {@link AspectStrategy}. */
public interface OutputGroupsProvider {
  ExtensionPointName<OutputGroupsProvider> EP_NAME =
      new ExtensionPointName<>("com.google.idea.blaze.OutputGroupsProvider");

  /**
   * Returns additional output groups to be added to blaze build. These output groups are used as
   * is, and are not checked for direct-deps support.
   */
  ImmutableSet<String> getAdditionalOutputGroups(
      OutputGroup outputGroup, ImmutableSet<LanguageClass> activeLanguages);
}
