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
package com.android.tools.idea.naveditor.editor;

import com.android.tools.idea.uibuilder.editor.ActionManager;
import com.android.tools.idea.uibuilder.editor.ActionsToolbar;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Provides and handles actions in the navigation editor
 */
public class NavActionManager extends ActionManager {

  public NavActionManager(@NotNull NavDesignSurface surface) {
    super(surface);
  }

  @Override
  public void registerActions(@NotNull JComponent component) {
    // TODO
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupMenu(@NotNull com.intellij.openapi.actionSystem.ActionManager actionManager,
                                               @Nullable NlComponent leafComponent) {
    // TODO
    return new DefaultActionGroup();
  }

  @Override
  public void addActions(@NotNull DefaultActionGroup group,
                         @Nullable NlComponent component,
                         @Nullable NlComponent parent,
                         @NotNull List<NlComponent> newSelection,
                         boolean toolbar) {
    // TODO
  }
}
