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

import com.android.tools.idea.uibuilder.actions.*;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.actions.*;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.InteractionManager;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides and handles actions in the layout editor
 */
public class NlActionManager {
  private final DesignSurface mySurface;
  private AnAction mySelectAllAction;
  private AnAction mySelectParent;

  public NlActionManager(@NotNull DesignSurface surface) {
    mySurface = surface;
  }

  public void registerActions(@NotNull JComponent component) {
    assert mySelectAllAction == null; // should only be called once!
    mySelectAllAction = new SelectAllAction(mySurface);
    registerAction(mySelectAllAction, "$SelectAll", component);

    mySelectParent = new SelectParentAction(mySurface);
    mySelectParent.registerCustomShortcutSet(KeyEvent.VK_ESCAPE, 0, null);
  }

  private static void registerAction(@NotNull AnAction action, @NonNls String actionId, @NotNull JComponent component) {
    action.registerCustomShortcutSet(
      ActionManager.getInstance().getAction(actionId).getShortcutSet(),
      component
    );
  }

  @NotNull
  public JComponent createToolbar(@NotNull NlModel model) {
    NlActionsToolbar actionsToolbar = new NlActionsToolbar(mySurface);
    actionsToolbar.setModel(model);
    return actionsToolbar.getToolbarComponent();
  }

  public void showPopup(@NotNull MouseEvent event) {
    NlComponent component = null;
    int x = event.getX();
    int y = event.getY();
    ScreenView screenView = mySurface.getScreenView(x, y);
    if (screenView == null) {
      screenView = mySurface.getCurrentScreenView();
    }
    if (screenView != null) {
      component = Coordinates.findComponent(screenView, x, y);
    }
    showPopup(event, screenView, component);
  }

  public void showPopup(@NotNull MouseEvent event, @Nullable ScreenView screenView, @Nullable NlComponent leafComponent) {
    ActionManager actionManager = ActionManager.getInstance();

    DefaultActionGroup group = createPopupMenu(actionManager, screenView, leafComponent);
    ActionPopupMenu popupMenu = actionManager.createActionPopupMenu("LayoutEditor", group);
    Component invoker = event.getSource() instanceof Component ? (Component)event.getSource() : mySurface;
    popupMenu.getComponent().show(invoker, event.getX(), event.getY());
  }

  @NotNull
  private DefaultActionGroup createPopupMenu(@NotNull ActionManager actionManager,
                                             @Nullable ScreenView screenView,
                                             @Nullable NlComponent leafComponent) {
    DefaultActionGroup group = new DefaultActionGroup();

    if (screenView != null) {
      if (leafComponent != null) {
        addViewHandlerActions(group, leafComponent, screenView.getSelectionModel().getSelection());
      }

      group.add(createSelectActionGroup(screenView.getSelectionModel()));
      group.addSeparator();
    }

    group.add(actionManager.getAction(IdeActions.ACTION_CUT));
    group.add(actionManager.getAction(IdeActions.ACTION_COPY));
    group.add(actionManager.getAction(IdeActions.ACTION_PASTE));
    group.addSeparator();
    group.add(actionManager.getAction(IdeActions.ACTION_DELETE));
    group.addSeparator();

    return group;
  }

  private void addViewHandlerActions(@NotNull DefaultActionGroup group,
                                     @NotNull NlComponent component,
                                     @NotNull List<NlComponent> selection) {
    // Look up view handlers
    int prevCount = group.getChildrenCount();
    NlComponent parent = !component.isRoot() ? component.getParent() : null;
    addViewActions(group, component, parent, selection, false);
    if (group.getChildrenCount() > prevCount) {
      group.addSeparator();
    }
  }

  @NotNull
  private ActionGroup createSelectActionGroup(@NotNull SelectionModel model) {
    DefaultActionGroup group = new DefaultActionGroup("_Select", true);

    AnAction selectSiblings = new SelectSiblingsAction(model);
    AnAction selectSameType = new SelectSameTypeAction(model);
    AnAction deselectAllAction = new DeselectAllAction(model);

    group.add(mySelectParent);
    group.add(selectSiblings);
    group.add(selectSameType);
    group.addSeparator();
    group.add(mySelectAllAction);
    group.add(deselectAllAction);

    return group;
  }

  public void addViewActions(@NotNull DefaultActionGroup group, @Nullable NlComponent component, @Nullable NlComponent parent,
                             @NotNull List<NlComponent> newSelection, boolean toolbar) {
    ScreenView screenView = mySurface.getCurrentScreenView();
    if (screenView == null || (parent == null && component == null)) {
      return;
    }

    ViewEditor editor = new ViewEditorImpl(screenView);

    // TODO: Perform caching
    if (component != null) {
      ViewHandler handler = ViewHandlerManager.get(mySurface.getProject()).getHandler(component);
      addViewActionsForHandler(group, component, newSelection, editor, handler, toolbar);
    }
    if (parent != null) {
      ViewHandler handler = ViewHandlerManager.get(mySurface.getProject()).getHandler(parent);
      addViewActionsForHandler(group, parent, newSelection, editor, handler, toolbar);
    }
  }

  private void addViewActionsForHandler(@NotNull DefaultActionGroup group,
                                        @NotNull NlComponent component,
                                        @NotNull List<NlComponent> newSelection,
                                        @NotNull ViewEditor editor,
                                        @Nullable ViewHandler handler,
                                        boolean toolbar) {
    if (handler == null) {
      return;
    }

    List<ViewAction> viewActions = createViewActionList();
    if (toolbar) {
      viewActions.addAll(ViewHandlerManager.get(mySurface.getProject()).getToolbarActions(handler));
    } else {
      viewActions.addAll(ViewHandlerManager.get(mySurface.getProject()).getPopupMenuActions(handler));
    }
    Collections.sort(viewActions);

    group.removeAll();
    List<AnAction> target = Lists.newArrayList();
    NlActionManager actionManager = mySurface.getActionManager();
    for (ViewAction viewAction : viewActions) {
      actionManager.addActions(target, viewAction, mySurface.getProject(), editor, handler, component, newSelection);
    }
    boolean lastWasSeparator = false;
    for (AnAction action : target) {
      // Merge repeated separators
      boolean isSeparator = action instanceof Separator;
      if (isSeparator && lastWasSeparator) {
        continue;
      }

      group.add(action);
      lastWasSeparator = true;
    }
  }

  @NotNull
  private static List<ViewAction> createViewActionList() {
    return new ArrayList<ViewAction>() {
      @Override
      public boolean add(ViewAction viewAction) {
        // Ensure that if no rank is specified, we just sort in the insert order
        if (!isEmpty()) {
          ViewAction prev = get(size() - 1);
          if (viewAction.getRank() == prev.getRank() || viewAction.getRank() == -1) {
            viewAction.setRank(prev.getRank() + 5);
          }
        }
        else if (viewAction.getRank() == -1) {
          viewAction.setRank(0);
        }

        return super.add(viewAction);
      }
    };
  }

  /**
   * Adds one or more {@link AnAction} to the target list from a given {@link ViewAction}. This
   * is typically just one action, but in the case of a {@link ToggleViewActionGroup} it can add
   * a series of related actions.
   */
  void addActions(@NotNull List<AnAction> target,
                  @NotNull ViewAction viewAction,
                  @NotNull Project project,
                  @NotNull ViewEditor editor,
                  @NotNull ViewHandler handler,
                  @NotNull NlComponent parent,
                  @NotNull List<NlComponent> newSelection) {
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

  /**
   * Wrapper around a {@link DirectViewAction} which uses an IDE {@link AnAction} in the toolbar
   */
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
            myAction.perform(myEditor, myHandler, myComponent, mySelectedChildren, e.getModifiers());
          }
        }.execute();
      }
      else {
        // Catch missing write lock and diagnose as missing affectsRedo
        try {
          myAction.perform(myEditor, myHandler, myComponent, mySelectedChildren, e.getModifiers());
        }
        catch (Throwable t) {
          throw new IncorrectOperationException("View Action required write lock: should not specify affectsUndo=false");
        }
      }

      mySurface.repaint();
    }

    @Override
    public void update(AnActionEvent e) {
      // Unfortunately, the action event we're fed here does *not* have the correct
      // current modifier state; there are code paths which just feed in a value of 0
      // when manufacturing their own ActionEvents; for example, Utils#expandActionGroup
      // which is usually how the toolbar code is updated.
      //
      // So, instead we'll need to feed it the most recently known mask from the
      // InteractionManager which observes mouse and keyboard events in the design surface.
      // This misses pure keyboard events when the design surface does not have focus
      // (but moving the mouse over the design surface updates it immediately.)
      //
      // (Longer term we consider having a singleton Toolkit listener which listens
      // for AWT events globally and tracks the most recent global modifier key state.)
      int modifiers = InteractionManager.getLastModifiers();

      myCurrentPresentation = e.getPresentation();
      try {
        myAction.updatePresentation(this, myEditor, myHandler, myComponent, mySelectedChildren, modifiers);
      }
      finally {
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

  /**
   * Wrapper around a {@link ToggleViewAction} which uses an IDE {@link AnAction} in the toolbar
   */
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
      }
      else {
        try {
          applySelection(state);
        }
        catch (Throwable t) {
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
        myAction.updatePresentation(this, myEditor, myHandler, myComponent, mySelectedChildren, e.getModifiers(), selected);
      }
      finally {
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

  /**
   * Wrapper around a {@link ViewActionMenu} which uses an IDE {@link AnAction} in the toolbar
   */
  private class ViewActionMenuWrapper extends ActionGroup implements ViewActionPresentation {
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
      super(action.getLabel(), true);
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
        myAction.updatePresentation(this, myEditor, myHandler, myComponent, mySelectedChildren, e.getModifiers());
      }
      finally {
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
      List<AnAction> actions = Lists.newArrayList();
      for (ViewAction viewAction : myAction.getActions()) {
        addActions(actions, viewAction, mySurface.getProject(), myEditor, myHandler, myComponent, mySelectedChildren);
      }
      return actions.toArray(AnAction.EMPTY_ARRAY);
    }
  }
}
