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
package com.google.idea.blaze.base.qsync.action;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.common.Label;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * A UI popup promting the user to choose a target to build for a file when multiple alternatives are available.
 */
public class BuildDependenciesHelperSelectTargetPopup extends BaseListPopupStep<Label> {
  static BuildDependenciesHelperSelectTargetPopup create(
    String fileDisplayLabel, Consumer<Label> onChosen, Runnable onCancelled, @NotNull Set<@NotNull Label> targets) {
    ImmutableList<Label> rows =
      ImmutableList.sortedCopyOf(Comparator.comparing(Label::toString), targets);

    return new BuildDependenciesHelperSelectTargetPopup(rows, fileDisplayLabel, onChosen, onCancelled);
  }

  private final Consumer<Label> onChosen;
  private final Runnable onCancelled;

  BuildDependenciesHelperSelectTargetPopup(ImmutableList<Label> rows,
                                           String fileDisplayLabel,
                                           Consumer<Label> onChosen,
                                           Runnable cancelled) {
    super("Select target to build for " + fileDisplayLabel, rows);
    this.onChosen = onChosen;
    onCancelled = cancelled;
  }

  /**
   * Prompts user to disambiguate multiple targets that can be used to build dependencies for a file.
   *
   * <p><em>Note:</em> This is a non-blocking method. It has to be invoked in the EDT.
   */
  public static BuildDependenciesHelper.DisambiguateTargetPrompt createDisambiguateTargetPrompt(PopupPositioner positioner) {
    return (fileDisplayLabel, targets, chosenConsumer, onCancelled) -> {
      JBPopupFactory factory = JBPopupFactory.getInstance();
      ListPopup popup =
        factory.createListPopup(create(fileDisplayLabel, chosenConsumer::invoke, onCancelled::invoke, targets));
      positioner.showInCorrectPosition(popup);
    };
  }

  @Override
  public PopupStep<?> onChosen(Label selectedValue, boolean finalChoice) {
    if (selectedValue == null) {
      return FINAL_CHOICE;
    }
    if (finalChoice) {
      onChosen.accept(selectedValue);
    }
    return FINAL_CHOICE;
  }

  @Override
  public void canceled() {
    onCancelled.run();
  }
}
