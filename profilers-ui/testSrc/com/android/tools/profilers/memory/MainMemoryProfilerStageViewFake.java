/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class MainMemoryProfilerStageViewFake extends MainMemoryProfilerStageView {
  public MainMemoryProfilerStageViewFake(@NotNull StudioProfilersView profilersView,
                                         @NotNull MainMemoryProfilerStage stage) {
    super(profilersView, stage);
  }

  @NotNull
  protected JComponent createEventMonitor(@NotNull StudioProfilers profilers) {
    return new JPanel();
  }
}
