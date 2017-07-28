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
package com.android.tools.idea.uibuilder.handlers.linear.actions;

import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation;
import com.android.tools.idea.uibuilder.handlers.DelegatingViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.common.model.NlComponent;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * Base class for action using {@link LinearLayoutHandler}
 */
public abstract class LinearLayoutAction extends DirectViewAction {

  public LinearLayoutAction() {
    super();
  }

  public LinearLayoutAction(Icon icon, String label) {
    super(icon, label);
  }

  @Override
  public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                 @NotNull ViewEditor editor,
                                 @NotNull ViewHandler handler,
                                 @NotNull NlComponent component,
                                 @NotNull List<NlComponent> selectedChildren,
                                 @JdkConstants.InputEventMask int modifiers) {
    if (handler instanceof DelegatingViewGroupHandler) {
      handler = ((DelegatingViewGroupHandler)handler).getDelegateHandler();
    }

    if (handler instanceof LinearLayoutHandler) {
      updatePresentation(presentation, editor, ((LinearLayoutHandler)handler), component, selectedChildren, modifiers);
    }
    else {
      presentation.setVisible(false);
    }
  }

  @Override
  public final void perform(@NotNull ViewEditor editor, @NotNull ViewHandler handler, @NotNull NlComponent component,
                            @NotNull List<NlComponent> selectedChildren, @JdkConstants.InputEventMask int modifiers) {
    if (handler instanceof DelegatingViewGroupHandler) {
      handler = ((DelegatingViewGroupHandler)handler).getDelegateHandler();
    }

    if (handler instanceof LinearLayoutHandler) {
      perform(editor, (LinearLayoutHandler)handler, component, selectedChildren, modifiers);
    }
  }

  protected abstract void perform(@NotNull ViewEditor editor,
                                  @NotNull LinearLayoutHandler handler,
                                  @NotNull NlComponent component,
                                  @NotNull List<NlComponent> selectedChildren,
                                  @JdkConstants.InputEventMask int modifiers);


  protected abstract void updatePresentation(@NotNull ViewActionPresentation presentation,
                                             @NotNull ViewEditor editor,
                                             @NotNull LinearLayoutHandler handler,
                                             @NotNull NlComponent component,
                                             @NotNull List<NlComponent> selectedChildren,
                                             @JdkConstants.InputEventMask int modifiers);
}
