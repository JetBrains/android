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
package com.android.tools.adtui.workbench;

import com.android.annotations.Nullable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import java.awt.Component;
import java.awt.event.KeyListener;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Specifies the content of {@link ToolWindowDefinition}.
 *
 * @param <T> Specifies the type of data controlled by a {@link WorkBench}.
 */
public interface ToolContent<T> extends Disposable {

  /**
   * Key used to store the current {@link ToolContent} in an {@link AttachedToolWindow} panel.
   */
  String TOOL_CONTENT_KEY = "com.android.tools.adtui.workbench.TOOL_CONTENT";

  /**
   * Return the {@link ToolContent} from a given component in a tool window.
   */
  @Nullable
  static ToolContent getToolContent(@Nullable Component component) {
    while (component instanceof JComponent) {
      Object content = ((JComponent)component).getClientProperty(TOOL_CONTENT_KEY);
      if (content instanceof  ToolContent) {
        return (ToolContent)content;
      }
      component = component.getParent();
    }
    return null;
  }

  /**
   * Set the context of a newly created {@link ToolContent}.
   * This value may be <code>null</code> for a floating tool window if there is no suitable content to show.
   */
  void setToolContext(@Nullable T toolContext);

  /**
   * Used to get the root component for this rool window.
   * The root component may not used to interact by default. For example, it is just a decoration.
   * To get the default interactive component, use {@link #getFocusedComponent()} instead.
   * @return the root of component for this tool window.
   */
  @NotNull
  JComponent getComponent();

  /**
   * Request the component that should receive focus initially.
   */
  @NotNull
  default JComponent getFocusedComponent() {
    return getComponent();
  }

  /**
   * @return the actions to be added to the top of the gear dropdown.
   */
  @NotNull
  default List<AnAction> getGearActions() {
    return Collections.emptyList();
  }

  /**
   * @return the actions to be added to the left of the gear dropdown.
   */
  @NotNull
  default List<AnAction> getAdditionalActions() {
    return Collections.emptyList();
  }

  /**
   * Registers callbacks into the AttachedToolWindow from the Content implementation.
   */
  default void registerCallbacks(@NotNull ToolWindowCallback callback) {}

  /**
   * Returns true if filtering is supported.
   */
  default boolean supportsFiltering() {
    return false;
  }

  /**
   * Set a new filter for the content being shown.
   */
  default void setFilter(@NotNull String filter) {}

  /**
   * Optionally a content window can listen to the key events going to the search filter control.
   */
  @Nullable
  default KeyListener getFilterKeyListener() {
    return null;
  }


  /**
   * Return true if the search button currently should be enabled.
   *
   * Note: this method could be called quite often.
   */
  default boolean isFilteringActive() { return true; }
}
