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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.idea.configurations.*;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.surface.ZoomType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

/**
 * The actions toolbar updates dynamically based on the component selection, their
 * parents (and if no selection, the root layout)
 */
public class NlActionsToolbar implements DesignSurfaceListener, ModelListener {
  private final DesignSurface mySurface;
  private NlModel myModel;
  private JComponent myToolbarComponent;
  private final DefaultActionGroup myDynamicGroup = new DefaultActionGroup();
  private ActionToolbar myActionToolbar;

  public NlActionsToolbar(@NotNull DesignSurface surface) {
    mySurface = surface;
  }

  @NotNull
  public JComponent getToolbarComponent() {
    if (myToolbarComponent == null) {
      myToolbarComponent = createToolbarComponent();
    }

    return myToolbarComponent;
  }

  private JComponent createToolbarComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    // Create a layout where there are three toolbars:
    // +----------------------------------------------------------------------------+
    // | Normal toolbar, minus dynamic actions                                      |
    // +---------------------------------------------+------------------------------+
    // | Dynamic layout actions                      | Zoom actions and file status |
    // +---------------------------------------------+------------------------------+
    ConfigurationHolder context = new NlEditorPanel.NlConfigurationHolder(mySurface);
    ActionGroup configGroup = createConfigActions(context, mySurface);

    ActionManager actionManager = ActionManager.getInstance();
    myActionToolbar = actionManager.createActionToolbar("NlConfigToolbar", configGroup, true);
    myActionToolbar.getComponent().setName("NlConfigToolbar");
    myActionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    JComponent actionToolbarComponent = myActionToolbar.getComponent();
    actionToolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    panel.add(actionToolbarComponent, BorderLayout.NORTH);

    final ActionToolbar layoutToolBar = actionManager.createActionToolbar("NlLayoutToolbar", myDynamicGroup, true);
    layoutToolBar.getComponent().setName("NlLayoutToolbar");
    layoutToolBar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);

    // The default toolbar layout adds too much spacing between the buttons. Switch to mini mode,
    // but also set a minimum size which will add *some* padding for our 16x16 icons.
    layoutToolBar.setMiniMode(true);
    layoutToolBar.setMinimumButtonSize(JBUI.size(26, 24));

    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(layoutToolBar.getComponent(), BorderLayout.WEST);

    JPanel combined = new JPanel(new BorderLayout());
    ActionToolbar zoomToolBar = actionManager.createActionToolbar("NlRhsToolbar", getRhsActions(mySurface), true);
    combined.add(zoomToolBar.getComponent(), BorderLayout.WEST);
    bottom.add(combined, BorderLayout.EAST);

    panel.add(bottom, BorderLayout.SOUTH);

    mySurface.addListener(this);

    updateViewActions();

    return panel;
  }

  private static ActionGroup getRhsActions(DesignSurface surface) {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new SetZoomAction(surface, ZoomType.OUT));
    group.add(new ZoomLabelAction(surface));
    group.add(new SetZoomAction(surface, ZoomType.IN));
    group.add(new SetZoomAction(surface, ZoomType.FIT));

    return group;
  }

  // We're using a FlatComboAction because a plain AnAction does not
  // get its text presentation painted as a label...
  public static class ZoomLabelAction extends AnAction implements CustomComponentAction {
    @NotNull private final DesignSurface mySurface;

    public ZoomLabelAction(@NotNull DesignSurface surface) {
      mySurface = surface;
      Presentation presentation = getTemplatePresentation();
      presentation.setDescription("Current Zoom Level");
      updatePresentation(presentation);
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      updatePresentation(e.getPresentation());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      // No-op: only label matters
    }

    private void updatePresentation(Presentation presentation) {
      double scale = mySurface.getScale();
      if (SystemInfo.isMac && UIUtil.isRetina()) {
        scale *= 2;
      }

      String label = String.format("%d%% ", (int)(100 * scale));
      presentation.setText(label);
    }

    @Override
    public JComponent createCustomComponent(Presentation presentation) {
      JBLabel label = new JBLabel() {
        private PropertyChangeListener myPresentationSyncer;
        private Presentation myPresentation = presentation;

        @Override
        public void addNotify() {
          super.addNotify();
          if (myPresentationSyncer == null) {
            myPresentationSyncer = new PresentationSyncer();
            myPresentation.addPropertyChangeListener(myPresentationSyncer);
          }
          setText(myPresentation.getText());
        }

        @Override
        public void removeNotify() {
          if (myPresentationSyncer != null) {
            myPresentation.removePropertyChangeListener(myPresentationSyncer);
            myPresentationSyncer = null;
          }
          super.removeNotify();
        }

        class PresentationSyncer implements PropertyChangeListener {
          @Override
          public void propertyChange(PropertyChangeEvent evt) {
            String propertyName = evt.getPropertyName();
            if (Presentation.PROP_TEXT.equals(propertyName)) {
              setText((String)evt.getNewValue());
              invalidate();
              repaint();
            }
          }
        }
      };
      label.setFont(UIUtil.getToolTipFont());
      return label;
    }
  }

  private static class SetZoomAction extends AnAction {
    @NotNull private final DesignSurface mySurface;
    @NotNull private final ZoomType myType;

    public SetZoomAction(@NotNull DesignSurface surface, @NotNull ZoomType type) {
      super(type.getLabel());
      myType = type;
      mySurface = surface;
      getTemplatePresentation().setIcon(type.getIcon());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      mySurface.zoom(myType);
    }
  }

  private static DefaultActionGroup createConfigActions(ConfigurationHolder configurationHolder, DesignSurface surface) {
    DefaultActionGroup group = new DefaultActionGroup();

    OrientationMenuAction orientationAction = new OrientationMenuAction(configurationHolder);
    group.add(orientationAction);
    group.addSeparator();

    DeviceMenuAction deviceAction = new DeviceMenuAction(configurationHolder);
    group.add(deviceAction);

    TargetMenuAction targetMenuAction = new TargetMenuAction(configurationHolder);
    group.add(targetMenuAction);

    ThemeMenuAction themeAction = new ThemeMenuAction(configurationHolder);
    group.add(themeAction);

    LocaleMenuAction localeAction = new LocaleMenuAction(configurationHolder);
    group.add(localeAction);

    group.addSeparator();
    group.add(new DesignModeAction(surface));
    group.add(new BlueprintModeAction(surface));

    ConfigurationMenuAction configAction = new ConfigurationMenuAction(surface);
    group.add(configAction);

    return group;
  }

  @Nullable
  private static NlComponent findSharedParent(@NotNull List<NlComponent> newSelection) {
    NlComponent parent = null;
    for (NlComponent selected : newSelection) {
      if (parent == null) {
        parent = selected.getParent();
        if (newSelection.size() == 1 && selected.isRoot() && (parent == null || parent.isRoot())) {
          // If you select a root layout, offer selection actions on it as well
          return selected;
        }
      } else if (parent != selected.getParent()) {
        parent = null;
        break;
      }
    }
    return parent;
  }

  public void updateViewActions() {
    ScreenView view = mySurface.getCurrentScreenView();
    if (view != null) {
      SelectionModel selectionModel = view.getSelectionModel();
      List<NlComponent> selection = selectionModel.getSelection();
      if (selection.isEmpty()) {
        List<NlComponent> roots = view.getModel().getComponents();
        if (roots.size() == 1) {
          selection = Collections.singletonList(roots.get(0));
        } else {
          // Model not yet rendered: when it's done, update. Listener is removed as soon as palette fires from listener callback.
          myModel.addListener(this);
          return;
        }
      }
      updateViewActions(selection);
    }
  }

  private void updateViewActions(@NotNull List<NlComponent> newSelection) {
    ScreenView screenView = mySurface.getCurrentScreenView();
    if (screenView == null) {
      return;
    }

    // TODO: Perform caching
    myDynamicGroup.removeAll();
    NlComponent parent = findSharedParent(newSelection);
    if (parent != null) {
      mySurface.getActionManager().addViewActions(myDynamicGroup, null, parent, newSelection, true);
    }
  }

  public void setModel(NlModel model) {
    myModel = model;
  }

  // ---- Implements DesignSurfaceListener ----

  @Override
  public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection) {
    assert surface == mySurface;
    if (!newSelection.isEmpty()) {
      updateViewActions(newSelection);
    } else {
      updateViewActions();
    }
  }

  @Override
  public void screenChanged(@NotNull DesignSurface surface, @Nullable ScreenView screenView) {
    // The toolbar depends on the current ScreenView for its content,
    // so reload when the ScreenView changes.
    myActionToolbar.updateActionsImmediately();
    updateViewActions();
  }

  @Override
  public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
    if (myDynamicGroup.getChildrenCount() == 0) {
      myModel = model;
      updateViewActions();
    }
  }

  // ---- Implements ModelListener ----

  @Override
  public void modelChanged(@NotNull NlModel model) {
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
    // Ensure that the toolbar is populated initially
    updateViewActions();
    model.removeListener(this);
  }
}
