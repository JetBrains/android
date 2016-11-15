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
package com.android.tools.idea.uibuilder.structure;

import com.android.annotations.Nullable;
import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.IdeaApplication;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlComponentTreePanel extends JPanel implements ToolContent<DesignSurface> {
  private final NlComponentTree myTree;

  public NlComponentTreePanel() {
    super(new BorderLayout());
    myTree = new NlComponentTree(null);
    add(ScrollPaneFactory.createScrollPane(myTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
    Disposer.register(this, myTree);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void setToolContext(@Nullable DesignSurface designSurface) {
    myTree.setDesignSurface(designSurface);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return this;
  }

  @NotNull
  @Override
  public JComponent getFocusedComponent() {
    return myTree;
  }

  @NotNull
  @Override
  public List<AnAction> getGearActions() {
    if (!Boolean.getBoolean(IdeaApplication.IDEA_IS_INTERNAL_PROPERTY)) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new ToggleBoundsVisibility(PropertiesComponent.getInstance(), myTree));
  }

  @NotNull
  @Override
  public List<AnAction> getAdditionalActions() {
    return Collections.emptyList();
  }

  @Override
  public void registerCloseAutoHideWindow(@NotNull Runnable runnable) {
  }

  @Override
  public boolean supportsFiltering() {
    return false;
  }

  @Override
  public void setFilter(@NotNull String filter) {
  }
}
