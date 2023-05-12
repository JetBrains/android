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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.StructurePaneComponentHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.lint.detector.api.Lint.stripIdPrefix;

public final class TreeSearchUtil {

  private TreeSearchUtil() {
  }

  /**
   * Provide a string representation of the given component to search
   * help searching by different useful features that can identify a component
   * (e.g text of a text view)
   */
  @NotNull
  public static String toString(@NotNull NlComponent component) {
    StringBuilder container = new StringBuilder();
    String id = stripIdPrefix(component.getId());
    if (!id.isEmpty()) {
      container.append(id);
    }

    StructurePaneComponentHandler handler = getViewHandler(component);
    String title = handler.getTitle(component);

    if (!StringUtil.startsWithIgnoreCase(id, title)) {
      container.append(id.isEmpty() ? title : " (" + title + ')');
    }

    String attributes = handler.getTitleAttributes(component);

    if (!attributes.isEmpty()) {
      container.append(' ').append(attributes);
    }
    return container.toString();
  }

  @NotNull
  private static StructurePaneComponentHandler getViewHandler(@NotNull NlComponent component) {
    StructurePaneComponentHandler handler = NlComponentHelperKt.getViewHandler(component, () -> {});
    return handler == null ? ViewHandlerManager.NONE : handler;
  }
}
