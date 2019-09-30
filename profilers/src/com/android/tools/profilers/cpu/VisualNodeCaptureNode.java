/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import org.jetbrains.annotations.NotNull;

/**
 * Child class of {@link CaptureNode} that allows children traversals but does not reset the parent node of the children. This is useful
 * when we want to rebase children visually but not destroy the data hierarchy.
 */
public class VisualNodeCaptureNode extends CaptureNode {
  public VisualNodeCaptureNode(@NotNull CaptureNodeModel model) {
    super(model);
  }
  /**
   * By default {@link #addChild(CaptureNode)} is destructive. It rebases the parent of a child node. When creating
   * a temp node for visualization we don't want this. As such this function allows nodes to be created that are
   * only used for visualizing data. An example of where this is used is benficial is the {@link com.android.tools.profilers.cpu.analysis.CpuAnalysisChartModel}
   */
  @Override
  public void addChild(CaptureNode node) {
    myChildren.add(node);
  }
}
