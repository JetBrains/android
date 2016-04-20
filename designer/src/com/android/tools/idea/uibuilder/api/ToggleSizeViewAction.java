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
package com.android.tools.idea.uibuilder.api;

import com.android.tools.idea.uibuilder.api.actions.ToggleViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.android.SdkConstants.*;

public class ToggleSizeViewAction extends ToggleViewAction {
  private final String myAttribute;

  public ToggleSizeViewAction(@NotNull String label,
                              @NotNull String attribute,
                              @NotNull Icon fillIcon,
                              @NotNull Icon wrapIcon) {
    super(fillIcon, wrapIcon, "", null);
    myAttribute = attribute;
  }

  @Override
  public boolean isSelected(@NotNull ViewEditor editor, @NotNull ViewHandler handler, @NotNull NlComponent parent,
                            @NotNull List<NlComponent> selectedChildren) {
    return isFill(selectedChildren);
  }

  @Override
  public void setSelected(@NotNull ViewEditor editor,
                          @NotNull ViewHandler handler,
                          @NotNull NlComponent parent,
                          @NotNull List<NlComponent> selectedChildren,
                          boolean selected) {
    for (NlComponent component : selectedChildren) {
      component.setAttribute(ANDROID_URI, myAttribute, selected ? VALUE_MATCH_PARENT : VALUE_WRAP_CONTENT);
    }
  }

  private boolean isFill(@NotNull List<NlComponent> selectedChildren) {
    if (!selectedChildren.isEmpty()) {
      String value = selectedChildren.get(0).getAttribute(ANDROID_URI, myAttribute);
      return VALUE_MATCH_PARENT.equals(value) || VALUE_FILL_PARENT.equals(value);
    }
    return false;
  }

  @Override
  public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                 @NotNull ViewEditor editor,
                                 @NotNull ViewHandler handler,
                                 @NotNull NlComponent component,
                                 @NotNull List<NlComponent> selectedChildren,
                                 boolean selected) {
    presentation.setEnabled(!selectedChildren.isEmpty());

    // The label says what the action will do:
    String text = String.format("Set %1$s to %2$s", myAttribute, selected ? VALUE_WRAP_CONTENT : VALUE_MATCH_PARENT);
    presentation.setLabel(text);
  }
}
