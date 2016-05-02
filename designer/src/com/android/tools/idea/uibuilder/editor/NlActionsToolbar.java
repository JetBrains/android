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
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.actions.*;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
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

    /* TODO: Add the RHS toolbar here
    RenderOptionsMenuBuilder optionsMenuBuilder = RenderOptionsMenuBuilder.create(context);
    ActionToolbar optionsToolBar = optionsMenuBuilder.addPreferXmlOption().addDeviceFrameOption().addRetinaOption().build();
    JPanel combined = new JPanel(new BorderLayout());
    ActionToolbar zoomToolBar = actionManager.createActionToolbar("NlActionsToolbar", getRhsActions(mySurface), true);
    combined.add(zoomToolBar.getComponent(), BorderLayout.WEST);
    combined.add(optionsToolBar.getComponent(), BorderLayout.EAST);
    bottom.add(combined, BorderLayout.EAST);
    */

    panel.add(bottom, BorderLayout.SOUTH);

    mySurface.addListener(this);

    updateViewActions();

    return panel;
  }

  private static ActionGroup getRhsActions(DesignSurface surface) {
    DefaultActionGroup group = new DefaultActionGroup();

    /*
    TODO: Add the RHS toolbar actions here
    ZoomMenuAction zoomAction = new ZoomMenuAction(surface);
    group.add(zoomAction);
    */

    return group;
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

    NlComponent parent = findSharedParent(newSelection);

    if (parent != null) {
      Project project = mySurface.getProject();
      ViewHandler handler = ViewHandlerManager.get(project).getHandler(parent);
      if (handler != null) {
        List<ViewAction> viewActions = new ArrayList<ViewAction>() {
          @Override
          public boolean add(ViewAction viewAction) {
            // Ensure that if no rank is specified, we just sort in the insert order
            if (!isEmpty()) {
              ViewAction prev = get(size() - 1);
              if (viewAction.getRank() == prev.getRank() || viewAction.getRank() == -1) {
                viewAction.setRank(prev.getRank() + 5);
              }
            } else if (viewAction.getRank() == -1) {
              viewAction.setRank(0);
            }

            return super.add(viewAction);
          }
        };
        handler.addViewActions(viewActions);
        Collections.sort(viewActions);

        // TODO: Perform caching
        myDynamicGroup.removeAll();
        List<AnAction> target = Lists.newArrayList();
        ViewEditor editor = new ViewEditorImpl(screenView);
        for (ViewAction viewAction : viewActions) {
          addActions(target, viewAction, project, editor, handler, parent, newSelection);
        }
        boolean lastWasSeparator = false;
        for (AnAction action : target) {
          // Merge repeated separators
          boolean isSeparator = action instanceof Separator;
          if (isSeparator && lastWasSeparator) {
            continue;
          }

          myDynamicGroup.add(action);
          lastWasSeparator = true;
        }
      }
    }
    else {
      myDynamicGroup.removeAll();
    }
  }

  /**
   * Adds one or more {@link AnAction} to the target list from a given {@link ViewAction}. This
   * is typically just one action, but in the case of a {@link ToggleViewActionGroup} it can add
   * a series of related actions.
   */
  private void addActions(@NotNull List<AnAction> target, @NotNull ViewAction viewAction,
                          @NotNull Project project, @NotNull ViewEditor editor, @NotNull ViewHandler handler,
                          @NotNull NlComponent parent, @NotNull List<NlComponent> newSelection) {
    if (viewAction instanceof DirectViewAction) {
      target.add(new DirectViewActionWrapper(project, (DirectViewAction)viewAction, editor, handler, parent, newSelection));
    }
    else if (viewAction instanceof ViewActionSeparator) {
      target.add(Separator.getInstance());
    }
    else if (viewAction instanceof ToggleViewAction) {
      target.add(new ToggleViewActionWrapper(project, (ToggleViewAction)viewAction, editor, handler, parent, newSelection));
    }
    else if (viewAction instanceof ToggleViewActionGroup) {
      List<ToggleViewActionWrapper> actions = Lists.newArrayList();
      for (ToggleViewAction action : ((ToggleViewActionGroup)viewAction).getActions()) {
        actions.add(new ToggleViewActionWrapper(project, action, editor, handler, parent, newSelection));
      }
      if (!actions.isEmpty()) {
        ToggleViewActionWrapper prev = null;
        for (ToggleViewActionWrapper action : actions) {
          target.add(action);

          if (prev != null) {
            prev.myGroupSibling = action;
          }
          prev = action;
        }
        if (prev != null) { // last link back to first
          prev.myGroupSibling = actions.get(0);
        }
      }
    }
    else if (viewAction instanceof ViewActionMenu) {
      target.add(new ViewActionMenuWrapper((ViewActionMenu)viewAction, editor, handler, parent, newSelection));
    }
    else {
      throw new UnsupportedOperationException(viewAction.getClass().getName());
    }
  }

  public void setModel(NlModel model) {
    myModel = model;
  }

  /** Wrapper around a {@link DirectViewAction} which uses an IDE {@link AnAction} in the toolbar */
  private class DirectViewActionWrapper extends AnAction implements ViewActionPresentation {
    private final Project myProject;
    private final DirectViewAction myAction;
    private final ViewHandler myHandler;
    private final ViewEditor myEditor;
    private final NlComponent myComponent;
    private final List<NlComponent> mySelectedChildren;
    private Presentation myCurrentPresentation;

    public DirectViewActionWrapper(@NotNull Project project,
                                   @NotNull DirectViewAction action,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren) {
      myProject = project;
      myAction = action;
      myEditor = editor;
      myHandler = handler;
      myComponent = component;
      mySelectedChildren = selectedChildren;
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(action.getDefaultIcon());
      presentation.setText(action.getLabel());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      String description = e.getPresentation().getText();
      PsiFile file = myComponent.getTag().getContainingFile();
      if (myAction.affectsUndo()) {
        new WriteCommandAction<Void>(myProject, description, null, new PsiFile[]{file}) {
          @Override
          protected void run(@NotNull Result<Void> result) throws Throwable {
            myAction.perform(myEditor, myHandler, myComponent, mySelectedChildren);
          }
        }.execute();
      } else {
        // Catch missing write lock and diagnose as missing affectsRedo
        try {
          myAction.perform(myEditor, myHandler, myComponent, mySelectedChildren);
        } catch (Throwable t) {
          throw new IncorrectOperationException("View Action required write lock: should not specify affectsUndo=false");
        }
      }

      mySurface.repaint();
    }

    @Override
    public void update(AnActionEvent e) {
      myCurrentPresentation = e.getPresentation();
      try {
        myAction.updatePresentation(this, myEditor, myHandler, myComponent, mySelectedChildren);
      } finally {
        myCurrentPresentation = null;
      }
    }

    // ---- Implements ViewActionPresentation ----

    @Override
    public void setLabel(@NotNull String label) {
      myCurrentPresentation.setText(label);
    }

    @Override
    public void setEnabled(boolean enabled) {
      myCurrentPresentation.setEnabled(enabled);
    }

    @Override
    public void setVisible(boolean visible) {
      myCurrentPresentation.setVisible(visible);
    }

    @Override
    public void setIcon(@Nullable Icon icon) {
      myCurrentPresentation.setIcon(icon);
    }
  }

  /** Wrapper around a {@link ToggleViewAction} which uses an IDE {@link AnAction} in the toolbar */
  private class ToggleViewActionWrapper extends ToggleAction implements ViewActionPresentation {
    private final Project myProject;
    private final ToggleViewAction myAction;
    private final ViewEditor myEditor;
    private final ViewHandler myHandler;
    private final NlComponent myComponent;
    private final List<NlComponent> mySelectedChildren;
    private Presentation myCurrentPresentation;
    private ToggleViewActionWrapper myGroupSibling;

    public ToggleViewActionWrapper(@NotNull Project project,
                                   @NotNull ToggleViewAction action,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren) {
      myProject = project;
      myAction = action;
      myEditor = editor;
      myHandler = handler;
      myComponent = component;
      mySelectedChildren = selectedChildren;
      Presentation presentation = getTemplatePresentation();
      presentation.setText(action.getUnselectedLabel());
      presentation.setIcon(action.getUnselectedIcon());
      presentation.setSelectedIcon(action.getSelectedIcon());
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myAction.isSelected(myEditor, myHandler, myComponent, mySelectedChildren);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      String description = e.getPresentation().getText();
      PsiFile file = myComponent.getTag().getContainingFile();

      if (myAction.affectsUndo()) {
        new WriteCommandAction<Void>(myProject, description, null, new PsiFile[]{file}) {
          @Override
          protected void run(@NotNull Result<Void> result) throws Throwable {
            applySelection(state);
          }
        }.execute();
      } else {
        try {
          applySelection(state);
        } catch (Throwable t) {
          throw new IncorrectOperationException("View Action required write lock: should not specify affectsUndo=false");
        }
      }
    }

    private void applySelection(boolean state) {
      myAction.setSelected(myEditor, myHandler, myComponent, mySelectedChildren, state);

      // Also clear any in the group (if any)
      if (state) {
        ToggleViewActionWrapper groupSibling = myGroupSibling;
        while (groupSibling != null && groupSibling != this) { // This is a circular list.
          groupSibling.myAction.setSelected(myEditor, myHandler, myComponent, mySelectedChildren, false);
          groupSibling = groupSibling.myGroupSibling;
        }
      }

      mySurface.repaint();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      myCurrentPresentation = e.getPresentation();
      try {
        boolean selected = myAction.isSelected(myEditor, myHandler, myComponent, mySelectedChildren);
        if (myAction.getSelectedLabel() != null) {
          myCurrentPresentation.setText(selected ? myAction.getSelectedLabel() : myAction.getUnselectedLabel());
        }
        myAction.updatePresentation(this, myEditor, myHandler, myComponent, mySelectedChildren, selected);
      } finally {
        myCurrentPresentation = null;
      }
    }

    // ---- Implements ViewActionPresentation ----

    @Override
    public void setLabel(@NotNull String label) {
      myCurrentPresentation.setText(label);
    }

    @Override
    public void setEnabled(boolean enabled) {
      myCurrentPresentation.setEnabled(enabled);
    }

    @Override
    public void setVisible(boolean visible) {
      myCurrentPresentation.setVisible(visible);
    }

    @Override
    public void setIcon(@Nullable Icon icon) {
      myCurrentPresentation.setIcon(icon);
    }
  }

  /** Wrapper around a {@link ViewActionMenu} which uses an IDE {@link AnAction} in the toolbar */
  private static class ViewActionMenuWrapper extends ActionGroup implements ViewActionPresentation {
    private final ViewActionMenu myAction;
    private final ViewEditor myEditor;
    private final ViewHandler myHandler;
    private final NlComponent myComponent;
    private final List<NlComponent> mySelectedChildren;
    private Presentation myCurrentPresentation;

    public ViewActionMenuWrapper(@NotNull ViewActionMenu action,
                                 @NotNull ViewEditor editor,
                                 @NotNull ViewHandler handler,
                                 @NotNull NlComponent component,
                                 @NotNull List<NlComponent> selectedChildren) {
      myAction = action;
      myEditor = editor;
      myHandler = handler;
      myComponent = component;
      mySelectedChildren = selectedChildren;
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(action.getDefaultIcon());
      presentation.setText(action.getLabel());
    }

    @Override
    public void update(AnActionEvent e) {
      myCurrentPresentation = e.getPresentation();
      try {
        myAction.updatePresentation(this, myEditor, myHandler, myComponent, mySelectedChildren);
      } finally {
        myCurrentPresentation = null;
      }
    }

    // ---- Implements ViewActionPresentation ----

    @Override
    public void setLabel(@NotNull String label) {
      myCurrentPresentation.setText(label);
    }

    @Override
    public void setEnabled(boolean enabled) {
      myCurrentPresentation.setEnabled(enabled);
    }

    @Override
    public void setVisible(boolean visible) {
      myCurrentPresentation.setVisible(visible);
    }

    @Override
    public void setIcon(@Nullable Icon icon) {
      myCurrentPresentation.setIcon(icon);
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[0];
    }
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
