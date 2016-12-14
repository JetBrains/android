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
package com.android.tools.profilers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.function.BiFunction;

public class StudioProfilersViewFake extends StudioProfilersView {
  public StudioProfilersViewFake(StudioProfilers profiler) {
    //noinspection Convert2Lambda
    super(profiler, new IdeProfilerComponents() {
      @Nullable
      @Override
      public JComponent getFileViewer(@Nullable File file) {
        return null;
      }
    });
  }

  @Override
  public <S extends Stage, T extends StageView> void bind(@NotNull Class<S> clazz,
                                                          @NotNull BiFunction<StudioProfilersView, S, T> constructor) {
    super.bind(clazz, constructor);
  }

  @Override
  public StageView getStageView() {
    return super.getStageView();
  }
}
