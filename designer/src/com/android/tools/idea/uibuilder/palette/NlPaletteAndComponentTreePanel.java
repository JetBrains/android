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
package com.android.tools.idea.uibuilder.palette;

import com.android.tools.idea.uibuilder.editor.PaletteToolWindow;
import com.android.tools.idea.uibuilder.structure.NlComponentTree;
import com.android.tools.idea.uibuilder.structure.ToggleBoundsVisibility;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.IdeaApplication;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlPaletteAndComponentTreePanel extends JPanel implements PaletteToolWindow {
  private final NlPalettePanel myPalette;
  private final NlComponentTree myComponentTree;

  public NlPaletteAndComponentTreePanel(@NotNull Project project, @Nullable DesignSurface designSurface) {
    myPalette = new NlPalettePanel(project, designSurface);
    myComponentTree = new NlComponentTree(designSurface);
    JComponent componentTree = createComponentTree(myComponentTree);

    Splitter splitter = new Splitter(true, 0.6f);
    splitter.setShowDividerIcon(false);
    splitter.setResizeEnabled(true);
    splitter.setFirstComponent(myPalette);
    splitter.setSecondComponent(componentTree);

    setLayout(new BorderLayout());
    add(splitter, BorderLayout.CENTER);
  }

  @Override
  public void requestFocusInPalette() {
    myPalette.requestFocus();
  }

  @Override
  public void requestFocusInComponentTree() {
    myComponentTree.requestFocus();
  }

  @Override
  public JComponent getDesignerComponent() {
    return this;
  }

  @NotNull
  @Override
  public AnAction[] getActions() {
    return new AnAction[]{
      new OptionAction()
    };
  }

  private class OptionAction extends AnAction {
    public OptionAction() {
      // todo: Find a set of different icons
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.General.ProjectConfigurable);
      presentation.setHoveredIcon(AllIcons.General.ProjectConfigurableBanner);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      int x = 4;
      int y = 4;
      InputEvent inputEvent = event.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        x = ((MouseEvent)inputEvent).getX();
        y = ((MouseEvent)inputEvent).getY();
      }

      showOptionPopup(inputEvent.getComponent(), x, y);
    }

    private void showOptionPopup(@NotNull Component component, int x, int y) {
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new TogglePaletteModeAction(myPalette, PaletteMode.ICON_AND_NAME));
      group.add(new TogglePaletteModeAction(myPalette, PaletteMode.LARGE_ICONS));
      group.add(new TogglePaletteModeAction(myPalette, PaletteMode.SMALL_ICONS));

      if (Boolean.getBoolean(IdeaApplication.IDEA_IS_INTERNAL_PROPERTY)) {
        group.addSeparator();
        group.add(new ToggleBoundsVisibility(PropertiesComponent.getInstance(), myComponentTree));
      }

      ActionPopupMenu popupMenu = ((ActionManagerImpl)ActionManager.getInstance()).createActionPopupMenu(
        ToolWindowContentUi.POPUP_PLACE, group, new MenuItemPresentationFactory(true));
      popupMenu.getComponent().show(component, x, y);
    }
  }

  @NotNull
  @Override
  public JComponent getFocusedComponent() {
    return myPalette;
  }

  @Override
  public void setDesignSurface(@Nullable DesignSurface designSurface) {
    myPalette.setDesignSurface(designSurface);
    myComponentTree.setDesignSurface(designSurface);
  }

  @NotNull
  private static JComponent createComponentTree(@NotNull NlComponentTree componentTree) {
    JPanel panel = new JPanel(new BorderLayout());
    JBLabel label = new JBLabel("Component Tree");
    label.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    label.setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                                                       BorderFactory.createEmptyBorder(4, 6, 4, 0)));
    panel.add(label, BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(componentTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER));
    return panel;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myPalette);
  }
}
