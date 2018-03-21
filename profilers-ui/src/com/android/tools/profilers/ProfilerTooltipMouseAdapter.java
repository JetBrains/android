/*
 * Copyright (C) 2017 The Android Open Source Project
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

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

/**
 * Basic tooltip mouse listener that toggles active tooltip.
 */
public class ProfilerTooltipMouseAdapter extends MouseAdapter {
  private Stage myStage;
  private Supplier<ProfilerTooltip> myTooltipBuilder;

  public ProfilerTooltipMouseAdapter(@NotNull Stage stage, Supplier<ProfilerTooltip> tooltipBuilder) {
    myStage = stage;
    myTooltipBuilder = tooltipBuilder;
  }

  @Override
  public void mouseEntered(MouseEvent e) {
    myStage.setTooltip(myTooltipBuilder.get());
  }

  @Override
  public void mouseExited(MouseEvent e) {
    myStage.setTooltip(null);
  }
}
