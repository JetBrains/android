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
package com.google.idea.blaze.plugin;

import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules.RuleTypes;
import com.google.idea.blaze.base.model.primitives.Kind;
import javax.annotation.Nullable;

/** Utility methods for intellij_plugin blaze targets */
public class IntellijPluginRule {

  public static boolean isPluginTarget(TargetInfo target) {
    return isPluginTargetKind(target.getKind());
  }

  private static boolean isPluginTargetKind(@Nullable Kind kind) {
    return RuleTypes.INTELLIJ_PLUGIN_DEBUG_TARGET.getKind().equals(kind);
  }
}
