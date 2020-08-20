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
package com.android.tools.idea.common.editor;

import com.android.tools.adtui.actions.ZoomInAction;
import com.android.tools.adtui.actions.ZoomLabelAction;
import com.android.tools.adtui.actions.ZoomOutAction;
import com.android.tools.adtui.actions.ZoomToFitAction;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

public class ToolbarActionGroups implements Disposable {
  protected final DesignSurface mySurface;

  public ToolbarActionGroups(@NotNull DesignSurface surface) {
    mySurface = surface;
  }

  @NotNull
  protected ActionGroup getNorthGroup() {
    return ActionGroup.EMPTY_GROUP;
  }

  @NotNull
  protected ActionGroup getEastGroup() {
    return ActionGroup.EMPTY_GROUP;
  }

  @NotNull
  protected ActionGroup getNorthEastGroup() {
    return ActionGroup.EMPTY_GROUP;
  }

  @Override
  public void dispose() {

  }

  /**
   * Includes a trailing separator when adding a non-empty collection of {@link AnAction}s to a {@link DefaultActionGroup}.
   */
  protected static void addActionsWithSeparator(@NotNull DefaultActionGroup group, @NotNull Collection<AnAction> actions) {
    if (!actions.isEmpty()) {
      group.addAll(actions);
      group.addSeparator();
    }
  }
}
