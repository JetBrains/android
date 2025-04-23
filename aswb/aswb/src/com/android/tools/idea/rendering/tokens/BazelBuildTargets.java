/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildTargets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

final class BazelBuildTargets implements BuildTargets {
  @Override
  public @NotNull BuildTargetReference from(@NotNull Module module, @NotNull VirtualFile file) {
    return new BazelBuildTargetReference(module);
  }
}
