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
package com.google.idea.blaze.base.run;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import javax.annotation.Nullable;

/** Marker interface for all run configurations */
public interface BlazeRunConfiguration {

  /**
   * Returns a list of target expressions this configuration should run, empty if its targets aren't
   * known or valid.
   *
   * <p>Will be calculated synchronously, and in edge cases may involve significant work, so
   * shouldn't be called on the EDT.
   */
  ImmutableList<? extends TargetExpression> getTargets();

  /** Keep in sync with source XML */
  void setKeepInSync(@Nullable Boolean keepInSync);

  Boolean getKeepInSync();
}
