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

import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.api.StructurePaneComponentHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.lint.detector.api.LintUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

/**
 * Decorator for the structure pane tree control.
 */
public class StructureTreeDecorator {
  private final ViewHandlerManager myViewHandlerManager;

  public StructureTreeDecorator(@NonNull Project project) {
    myViewHandlerManager = ViewHandlerManager.get(project);
  }

  /**
   * Decorate a tree node with ID, title, and attributes of the current component.
   * Any changes made to this method should be duplicated in {@link #getText}.
   */
  public void decorate(@NonNull NlComponent component, @NonNull SimpleColoredComponent renderer, boolean full) {
    String id = component.getId();
    id = LintUtils.stripIdPrefix(id);
    id = StringUtil.nullize(id);

    StructurePaneComponentHandler handler = component.getViewHandler();

    if (handler == null) {
      handler = ViewHandlerManager.NONE;
    }

    String title = handler.getTitle(component);
    String attrs = handler.getTitleAttributes(component);

    if (id != null) {
      renderer.append(id, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }

    // Don't display the type title if it's obvious from the id (e.g.
    // if the id is button1, don't display (Button) as the type)
    if (id == null || !StringUtil.startsWithIgnoreCase(id, title)) {
      renderer.append(id != null ? " (" + title + ")" : title, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    if (!StringUtil.isEmpty(attrs)) {
      renderer.append(String.format(" %1$s", attrs), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    if (full) {
      renderer.setIcon(handler.getIcon(component));
    }
  }

  /**
   * Generate the string shown by {@link #decorate} that can be used for searches.
   * Any changes made to this method should be duplicated in {@link #decorate}.
   */
  @NonNull
  public String getText(@NonNull NlComponent component) {
    if (component.getTag().equals(EmptyXmlTag.INSTANCE)) {
      return "";
    }
    String id = component.getId();
    id = LintUtils.stripIdPrefix(id);
    id = StringUtil.nullize(id);

    ViewHandler handler = myViewHandlerManager.getHandlerOrDefault(component);
    String title = handler.getTitle(component);
    String attrs = handler.getTitleAttributes(component);

    String text = id != null ? id + "(" + title + ")" : title;
    if (!StringUtil.isEmpty(attrs)) {
      text += " " + attrs;
    }
    return text;
  }
}
