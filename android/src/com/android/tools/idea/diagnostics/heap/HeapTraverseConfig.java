/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.diagnostics.heap;

import org.jetbrains.annotations.NotNull;

public class HeapTraverseConfig {

  @NotNull
  private final ComponentsSet componentsSet;
  final boolean collectHistograms;
  final boolean collectDisposerTreeInfo;

  public HeapTraverseConfig(@NotNull final ComponentsSet componentsSet,
                            boolean collectHistograms,
                            boolean collectDisposerTreeInfo) {
    this.componentsSet = componentsSet;
    this.collectHistograms = collectHistograms;
    this.collectDisposerTreeInfo = collectDisposerTreeInfo;
  }

  @NotNull
  public ComponentsSet getComponentsSet() {
    return componentsSet;
  }
}
