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
package com.android.tools.profilers.cpu.analysis;

import com.android.tools.adtui.model.trackgroup.SelectableTrackModel;
import org.jetbrains.annotations.NotNull;

/**
 * Represents something that can generate a {@link CpuAnalysisModel}. Implementations can be a CPU capture trace, a thread or a trace event.
 */
public interface CpuAnalyzable<T extends CpuAnalyzable<?>> extends SelectableTrackModel {
  /**
   * @return a model that contains analysis data.
   */
  @NotNull
  CpuAnalysisModel<T> getAnalysisModel();
}
