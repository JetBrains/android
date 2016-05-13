/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.uibuilder.api.StructurePaneComponentHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.lint.detector.api.LintUtils;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Decorator for the structure pane tree control.
 */
public class StructureTreeDecorator {
  private StructureTreeDecorator() {
  }

  /**
   * Decorate a tree node with ID, title, and attributes of the current component.
   */
  static void decorate(@NotNull ColoredTextContainer container, @NotNull NlComponent component) {
    append(container, component);
    container.setIcon(getViewHandler(component).getIcon(component));
  }

  @NotNull
  static String toString(@NotNull NlComponent component) {
    ColoredTextContainer container = new StringBuilderContainer();
    append(container, component);

    return container.toString();
  }

  private static final class StringBuilderContainer implements ColoredTextContainer {
    private final StringBuilder myBuilder = new StringBuilder();

    @Override
    public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
      myBuilder.append(fragment);
    }

    @Override
    public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, @NotNull Object tag) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setIcon(@Nullable Icon icon) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setToolTipText(@Nullable String text) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public String toString() {
      return myBuilder.toString();
    }
  }

  private static void append(@NotNull ColoredTextContainer container, @NotNull NlComponent component) {
    String id = LintUtils.stripIdPrefix(component.getId());

    if (!id.isEmpty()) {
      container.append(id, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }

    StructurePaneComponentHandler handler = getViewHandler(component);
    String title = handler.getTitle(component);

    if (!StringUtil.startsWithIgnoreCase(id, title)) {
      container.append(id.isEmpty() ? title : " (" + title + ')', SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    String attributes = handler.getTitleAttributes(component);

    if (!attributes.isEmpty()) {
      container.append(' ' + attributes, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  @NotNull
  private static StructurePaneComponentHandler getViewHandler(@NotNull NlComponent component) {
    StructurePaneComponentHandler handler = component.getViewHandler();
    return handler == null ? ViewHandlerManager.NONE : handler;
  }
}
