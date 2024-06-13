/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.adtui.flat.FlatComboBox;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.customevent.CustomEventProfilerStage;
import com.android.tools.profilers.memory.MainMemoryProfilerStage;
import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class StageNavigationToolbar extends JPanel {

  private final StudioProfilers profilers;
  private final CommonButton backButton;

  public StageNavigationToolbar(StudioProfilers profilers) {
    super(ProfilerLayout.createToolbarLayout());
    this.profilers = profilers;

    backButton = new CommonButton(AllIcons.Actions.Back);
    backButton.addActionListener(action -> confirmExit("Go back?", () -> {
      profilers.setStage(profilers.getStage().getParentStage());
      // clear the selected artifact by setting it to null as going back
      // guarantees we will no longer be viewing an artifact recording
      profilers.getSessionsManager().resetSelectedArtifactProto();
      profilers.getIdeServices().getFeatureTracker().trackGoBack();
    }));
    add(backButton);
    add(new FlatSeparator());

    JComboBox<Class<? extends Stage>> stageCombo = new FlatComboBox<>();
    Supplier<List<Class<? extends Stage>>> getSupportedStages = () -> profilers.getDirectStages().stream()
      .filter(st -> profilers.getSelectedSessionSupportLevel().isStageSupported((Class<? extends Stage<?>>)st))
      .collect(Collectors.toList());
    JComboBoxView stages = new JComboBoxView<>(stageCombo, profilers, ProfilerAspect.STAGE,
                                               getSupportedStages,
                                               profilers::getStageClass,
                                               stage -> confirmExit("Exit?", () -> {
                                                 // Track first, so current stage is sent with the event
                                                 profilers.getIdeServices().getFeatureTracker().trackSelectMonitor();
                                                 profilers.setNewStage(stage);
                                               }),
                                               () -> profilers.getStage().getHomeStageClass());
    stageCombo.setRenderer(new StageComboBoxRenderer());
    stages.bind();
    add(stageCombo);
    add(new FlatSeparator());
  }

  @VisibleForTesting
  @NotNull
  CommonButton getBackButton() {
    return backButton;
  }

  private void confirmExit(String title, Runnable exit) {
    String msg = profilers.getStage().getConfirmExitMessage();
    if (msg != null) {
      profilers.getIdeServices().openYesNoDialog(msg, title, exit, () -> {});
    } else {
      exit.run();
    }
  }

  public static class StageComboBoxRenderer extends ColoredListCellRenderer<Class> {

    private static ImmutableMap<Class<? extends Stage>, String> CLASS_TO_NAME = ImmutableMap.of(
      CpuProfilerStage.class, "CPU",
      MainMemoryProfilerStage.class, "MEMORY",
      CustomEventProfilerStage.class, "CUSTOM EVENTS");

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Class value, int index, boolean selected, boolean hasFocus) {
      String name = CLASS_TO_NAME.get(value);
      append(name == null ? "[UNKNOWN]" : name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
