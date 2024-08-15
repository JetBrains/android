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

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import java.awt.BorderLayout;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NlComponentTreePanel extends AdtSecondaryPanel implements ToolContent<DesignSurface<?>> {
  private final NlComponentTree myTree;
  private final NlVisibilityGutterPanel myVisibilityGutter = new NlVisibilityGutterPanel();
  private final BackNavigationComponent myNavigationComponent;
  private final JPanel myTreeContainer = new JPanel(new BorderLayout());

  public NlComponentTreePanel(@NotNull Disposable parentDisposable) {
    super(new BorderLayout());
    Disposer.register(parentDisposable, this);
    myTree = new NlComponentTree(null, myVisibilityGutter);
    myTreeContainer.setOpaque(true);
    myTreeContainer.setBackground(StudioColorsKt.getSecondaryPanelBackground());
    myTreeContainer.add(myTree, BorderLayout.CENTER);
    myTreeContainer.add(myVisibilityGutter, BorderLayout.EAST);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTreeContainer,
                                                          VERTICAL_SCROLLBAR_AS_NEEDED,
                                                          HORIZONTAL_SCROLLBAR_NEVER);
    pane.setBorder(null);
    myNavigationComponent = new BackNavigationComponent();
    add(myNavigationComponent, BorderLayout.NORTH);
    add(pane, BorderLayout.CENTER);
    myTree.setBackground(StudioColorsKt.getSecondaryPanelBackground());
    Disposer.register(this, myTree);
    Disposer.register(this, myVisibilityGutter);
  }

  @Override
  public void dispose() {
    myNavigationComponent.setDesignSurface(null);
  }

  @Override
  public void setToolContext(@Nullable DesignSurface<?> designSurface) {
    myNavigationComponent.setDesignSurface(designSurface);
    myTree.setDesignSurface((NlDesignSurface)designSurface);
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
    if (!ApplicationManager.getApplication().isInternal()) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new ToggleBoundsVisibility(PropertiesComponent.getInstance(), myTree));
  }
}
