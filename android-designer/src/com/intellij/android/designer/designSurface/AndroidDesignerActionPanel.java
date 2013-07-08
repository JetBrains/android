/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.intellij.android.designer.designSurface;

import com.android.tools.idea.rendering.SaveScreenshotAction;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewLayout;
import com.intellij.designer.actions.DesignerActionPanel;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.ZoomType;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadLayout;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.PsiNavigateUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AndroidDesignerActionPanel extends DesignerActionPanel {
  public AndroidDesignerActionPanel(DesignerEditorPanel designer, JComponent shortcuts) {
    super(designer, shortcuts);

    DefaultActionGroup popupGroup = getPopupGroup();
    popupGroup.addSeparator();
    popupGroup.add(new SaveScreenshotAction((AndroidDesignerEditorPanel)myDesigner));
  }

  @Override
  protected JComponent createToolbar() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    // Create a layout where there are three toolbars:
    // +----------------------------------------------------------------------------+
    // | Normal toolbar, minus dynamic actions                                      |
    // +---------------------------------------------+------------------------------+
    // | Dynamic layout actions                      | Zoom actions and file status |
    // +---------------------------------------------+------------------------------+

    ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar actionToolbar = actionManager.createActionToolbar(TOOLBAR, getActionGroup(), true);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    panel.add(actionToolbar.getComponent(), BorderLayout.NORTH);

    ActionToolbar layoutToolBar = actionManager.createActionToolbar(TOOLBAR, getDynamicActionGroup(), true);
    layoutToolBar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);

    // The default toolbar layout adds too much spacing between the buttons. Switch to mini mode,
    // but also set a minimum size which will add *some* padding for our 16x16 icons.
    layoutToolBar.setMiniMode(true);
    layoutToolBar.setMinimumButtonSize(new Dimension(22, 24));

    ActionToolbar zoomToolBar = actionManager.createActionToolbar(TOOLBAR, getRhsActions(), true);
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(layoutToolBar.getComponent(), BorderLayout.WEST);
    bottom.add(zoomToolBar.getComponent(), BorderLayout.EAST);
    panel.add(bottom, BorderLayout.SOUTH);

    return panel;
  }

  @Override
  protected DefaultActionGroup createActionGroup() {
    // Like super implementation, but only adding the static group; the dynamic
    // actions are added to a separate toolbar
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(getActionGroup());
    return group;
  }

  @Override
  public void update() {
    // Overriding super; we don't want to dynamically hide or show the toolbar; it's there all the time
  }

  private ActionGroup getRhsActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new ToggleAction(null, "Zoom to Fit (0)", AndroidIcons.ZoomFit) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return ((AndroidDesignerEditorPanel)myDesigner).isZoomToFit();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myDesigner.zoom(ZoomType.FIT);
      }
    });
    group.add(new AnAction(null, "Reset Zoom to 100% (1)", AndroidIcons.ZoomActual) {
      @Override
      public void actionPerformed(AnActionEvent e) { myDesigner.zoom(ZoomType.ACTUAL); }
    });
    group.addSeparator();
    group.add(new AnAction(null, "Zoom In (+)", AndroidIcons.ZoomIn) {
      @Override
      public void actionPerformed(AnActionEvent e) { myDesigner.zoom(ZoomType.IN); }
    });
    group.add(new AnAction(null, "Zoom Out (-)", AndroidIcons.ZoomOut) {
      @Override
      public void actionPerformed(AnActionEvent e) { myDesigner.zoom(ZoomType.OUT); }
    });

    String description = "Jump to Source";
    KeyboardShortcut shortcut = ActionManager.getInstance().getKeyboardShortcut(IdeActions.ACTION_GOTO_DECLARATION);
    if (shortcut != null) {
      description += " (" + KeymapUtil.getShortcutText(shortcut) + ")";
    }
    // Use FilesTypes.Text rather than FileTypes.Xml here to avoid having the icon from
    // the tab bar replicated right below it
    group.add(new AnAction(null, description, AllIcons.FileTypes.Text) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        List<RadComponent> selection = myDesigner.getSurfaceArea().getSelection();
        if (!selection.isEmpty()) {
          RadViewComponent component = (RadViewComponent)selection.get(0);
          PsiNavigateUtil.navigate(component.getTag());
        }
      }

      @Override
      public void update(AnActionEvent e) {
        List<RadComponent> selection = myDesigner.getSurfaceArea().getSelection();
        e.getPresentation().setEnabled(!selection.isEmpty());
      }
    });

    return group;
  }

  @Override
  protected void addSelectionActions(List<RadComponent> selection, DefaultActionGroup group) {
    if (!selection.isEmpty()) {
      // Segment the selected components into lists of siblings
      Map<RadComponent, List<RadComponent>> siblingLists = RadComponent.groupSiblings(selection);
      for (Map.Entry<RadComponent, List<RadComponent>> entry : siblingLists.entrySet()) {
        @Nullable RadComponent parent = entry.getKey();
        @NotNull List<RadComponent> children = entry.getValue();

        if (parent != null) {
          RadLayout layout = parent.getLayout();
          if (layout instanceof RadViewLayout) {
            ((RadViewLayout) layout).addContainerSelectionActions(myDesigner, group, RadViewComponent.getViewComponents(children));
          } else {
            layout.addSelectionActions(myDesigner, group, getShortcuts(), children);
          }
        } else if (selection.size() == 1) {
          // If you select a root layout, offer selection actions on it as well
          RadLayout selected = selection.get(0).getLayout();
          if (selected instanceof RadViewLayout) {
            ((RadViewLayout) selected).addContainerSelectionActions(myDesigner, group, RadViewComponent.getViewComponents(children));
          }
        }
      }

      for (RadComponent component : selection) {
        component.addSelectionActions(myDesigner, group, getShortcuts(), Collections.<RadComponent>singletonList(component));
      }
    }
  }

  private DefaultActionGroup myPopupGroup;
  private final DefaultActionGroup myDynamicBeforeGroup = new DefaultActionGroup();
  private final DefaultActionGroup myDynamicAfterGroup = new DefaultActionGroup();

  @Override
  public ActionGroup getPopupActions(EditableArea area) {
    ActionGroup superActions = super.getPopupActions(area);

    if (myPopupGroup == null) {
      myPopupGroup = new DefaultActionGroup();
      myPopupGroup.add(myDynamicBeforeGroup);
      myPopupGroup.add(superActions);
      myPopupGroup.add(myDynamicAfterGroup);
    }

    if (myDynamicBeforeGroup.getChildrenCount() > 0) {
      myDynamicBeforeGroup.removeAll();
    }
    if (myDynamicAfterGroup.getChildrenCount() > 0) {
      myDynamicAfterGroup.removeAll();
    }

    // Insert selection actions
    List<RadComponent> selection = area.getSelection();
    if (selection.size() == 1) {
      // Add specific actions
      RadComponent selected = selection.get(0);
      if (selected instanceof RadViewComponent) {
        RadViewComponent viewComponent = (RadViewComponent)selected;
        AndroidDesignerEditorPanel designer = (AndroidDesignerEditorPanel)myDesigner;
        viewComponent.addPopupActions(designer, myDynamicBeforeGroup, myDynamicAfterGroup, designer, selection);

        // Add in actions from the parents
        RadComponent current = selected.getParent();
        while (current != null && current instanceof RadViewComponent) {
          DefaultActionGroup container = new DefaultActionGroup(((RadViewComponent)current).getId(), true);
          if (((RadViewComponent)current).addPopupActions(designer, container, container, designer, selection)) {
            myDynamicAfterGroup.add(container);
          }
          current = current.getParent();
        }
      }
    }

    return myPopupGroup;
  }
}
