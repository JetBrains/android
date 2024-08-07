/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.projectstructure;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Service for looking up modules (might not be committed during tests). */
public interface ModuleFinder {

  static ModuleFinder getInstance(Project project) {
    return project.getService(ModuleFinder.class);
  }

  @Nullable
  Module findModuleByName(String name);
}
