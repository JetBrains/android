/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.android.tools.idea.rendering.tokens;

import com.android.tools.idea.rendering.BuildTargetReference;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.AlreadyDisposedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class BazelBuildTargetReference implements BuildTargetReference {
  private final Module module;

  BazelBuildTargetReference(Module module) {
    this.module = module;
  }

  @NotNull
  @Override
  public Module getModule() {
    if (module.isDisposed()) {
      throw new AlreadyDisposedException("Already disposed: " + module);
    }
    return module;
  }

  @NotNull
  @Override
  public Project getProject() {
    return module.getProject();
  }

  @Nullable
  @Override
  public Module getModuleIfNotDisposed() {
    if (module.isDisposed()) {
      return null;
    }
    return module;
  }
}
