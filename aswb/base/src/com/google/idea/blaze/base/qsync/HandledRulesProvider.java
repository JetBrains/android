/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Set;

/**
 * Extension point for other plugins to inform query sync that they can handle symbol resolution for
 * project targets of certain rule kinds that would otherwise require building
 */
public interface HandledRulesProvider {
  ExtensionPointName<HandledRulesProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.base.qsync.HandledRulesProvider");

  Set<String> handledRuleKinds(Project project);
}
