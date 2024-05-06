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

import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class HeapTraverseConfig {

  private final static int DEFAULT_HISTOGRAM_PRINT_LIMIT = 50;
  final static int DEFAULT_SUMMARY_REQUIRED_SUBTREE_SIZE = 10_000_000; //10mb;

  @NotNull
  private final ComponentsSet componentsSet;
  final boolean collectHistograms;
  final boolean collectDisposerTreeInfo;
  final int histogramPrintLimit;
  final boolean collectObjectTreesData;
  final long summaryRequiredSubtreeSize;
  @NotNull
  final List<ComponentsSet.Component> exceededComponents;

  public HeapTraverseConfig(@NotNull final ComponentsSet componentsSet,
                            boolean collectHistograms,
                            boolean collectDisposerTreeInfo) {
    this(componentsSet, collectHistograms, collectDisposerTreeInfo, DEFAULT_HISTOGRAM_PRINT_LIMIT, false,
         DEFAULT_SUMMARY_REQUIRED_SUBTREE_SIZE, Collections.emptyList());
  }

  public HeapTraverseConfig(@NotNull final ComponentsSet componentsSet,
                            boolean collectHistograms,
                            boolean collectDisposerTreeInfo,
                            int histogramPrintLimit,
                            boolean collectObjectTreesData,
                            long summaryRequiredSubtreeSize,
                            @NotNull final List<ComponentsSet.Component> exceededComponents) {
    this.histogramPrintLimit = histogramPrintLimit;
    this.componentsSet = componentsSet;
    this.collectHistograms = collectHistograms;
    this.collectDisposerTreeInfo = collectDisposerTreeInfo;
    this.collectObjectTreesData = collectObjectTreesData;
    this.summaryRequiredSubtreeSize = summaryRequiredSubtreeSize;
    this.exceededComponents = exceededComponents;
  }

  @NotNull
  public ComponentsSet getComponentsSet() {
    return componentsSet;
  }
}
