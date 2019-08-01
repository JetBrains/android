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

import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilersView;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents the view of a capture taken from within the {@link CpuProfilerStageView}.
 * all captures of type {@link Cpu.CpuTraceType} are supported.
 */
public class CpuCaptureStageView extends StageView<CpuCaptureStage> {

  public CpuCaptureStageView(@NotNull StudioProfilersView view, @NotNull CpuCaptureStage stage) {
    super(view, stage);
    stage.getAspect().addDependency(this).onChange(CpuCaptureStage.Aspect.STATE, this::updateComponents);
    updateComponents();
  }

  private void updateComponents() {
    getComponent().removeAll();
    if (getStage().getState() == CpuCaptureStage.State.PARSING) {
      getComponent().add(new StatusPanel(getStage().getCaptureHandler(), "Parsing", "Abort"));
    } else {
      getComponent().add(createAnalyzingComponents());
    }
  }

  private JComponent createAnalyzingComponents() {
    return new JPanel();
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
  }
}
