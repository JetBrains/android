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

import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.actions.MockupDeleteAction;
import com.android.tools.idea.actions.MockupEditAction;
import com.android.tools.idea.common.actions.GotoComponentAction;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.InteractionManager;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.actions.ConvertToConstraintLayoutAction;
import com.android.tools.idea.uibuilder.actions.MorphComponentAction;
import com.android.tools.idea.uibuilder.actions.SelectAllAction;
import com.android.tools.idea.uibuilder.actions.SelectParentAction;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.actions.*;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.refactoring.AndroidExtractAsIncludeAction;
import org.jetbrains.android.refactoring.AndroidExtractStyleAction;
import org.jetbrains.android.refactoring.AndroidInlineIncludeAction;
import org.jetbrains.android.refactoring.AndroidInlineStyleReferenceAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides and handles actions in the layout editor
 */
public class NlActionManager extends ActionManager<NlDesignSurface> {
  private AnAction mySelectAllAction;
  private AnAction mySelectParent;
  private GotoComponentAction myGotoComponentAction;

  public NlActionManager(@NotNull NlDesignSurface surface) {
    super(surface);
  }

  /**
   * Create actions if needed and register the shortcuts on the provided component.
   * <p>
   * The registered actions are:
   * <ul>
   * <li> {@link SelectAllAction}
   * <li> {@link GotoComponentAction}
   * <li> {@link SelectParentAction}
   * </ul>
   */
  @Override
  public void registerActionsShortcuts(@NotNull JComponent component) {
    if (mySelectAllAction == null) {
      mySelectAllAction = new SelectAllAction(mySurface);
      myGotoComponentAction = new GotoComponentAction(mySurface);
      mySelectParent = new SelectParentAction(mySurface);
    }
    registerAction(mySelectAllAction, "$SelectAll", component);
    registerAction(myGotoComponentAction, IdeActions.ACTION_GOTO_DECLARATION, component);
    mySelectParent.registerCustomShortcutSet(KeyEvent.VK_ESCAPE, 0, component);
  }

  @NotNull
  private static ActionGroup createRefactoringMenu() {
    DefaultActionGroup group = new DefaultActionGroup("_Refactor", true);
    com.intellij.openapi.actionSystem.ActionManager manager =
      com.intellij.openapi.actionSystem.ActionManager.getInstance();

    AnAction action = manager.getAction(AndroidExtractStyleAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("_Extract Style...", action));

    action = manager.getAction(AndroidInlineStyleReferenceAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("_Inline Style...", action));

    action = manager.getAction(AndroidExtractAsIncludeAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("E_xtract Layout...", action));

    action = manager.getAction(AndroidInlineIncludeAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("I_nline Layout...", action));

    return group;
  }

  /**
   * Exposes android refactoring actions in layout editor context menu: customizes
   * label for menu usage (e.g. with mnemonics) and makes hidden action visible but
   * disabled instead
   */
  private static class AndroidRefactoringActionWrapper extends AnAction {
    private final AnAction myRefactoringAction;

    public AndroidRefactoringActionWrapper(@NotNull String text, @NotNull AnAction refactoringAction) {
      super(text, null, null);
      myRefactoringAction = refactoringAction;
      getTemplatePresentation().setDescription(refactoringAction.getTemplatePresentation().getDescription());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myRefactoringAction.actionPerformed(e);
    }

    @Override
    public void update(AnActionEvent e) {
      myRefactoringAction.update(e);
      Presentation p = e.getPresentation();
      if (!p.isVisible()) {
        p.setEnabled(false);
        p.setVisible(true);
      }
    }
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupMenu(@NotNull com.intellij.openapi.actionSystem.ActionManager actionManager,
                                               @Nullable NlComponent leafComponent) {
    DefaultActionGroup group = new DefaultActionGroup();

    SceneView screenView = mySurface.getCurrentSceneView();
    if (screenView != null) {
      if (leafComponent != null) {
        addViewHandlerActions(group, leafComponent, screenView.getSelectionModel().getSelection());
      }

      group.addSeparator();
    }

    if (mySurface.getLayoutType().isLayout()) {
      createLayoutOnlyActions(leafComponent, group);
    }

    group.add(actionManager.getAction(IdeActions.ACTION_CUT));
    group.add(actionManager.getAction(IdeActions.ACTION_COPY));
    group.add(actionManager.getAction(IdeActions.ACTION_PASTE));
    group.addSeparator();
    group.add(actionManager.getAction(IdeActions.ACTION_DELETE));
    group.addSeparator();
    group.add(myGotoComponentAction);

    return group;
  }

  private void createLayoutOnlyActions(@Nullable NlComponent leafComponent, @NotNull DefaultActionGroup group) {
    if (leafComponent != null && StudioFlags.NELE_CONVERT_VIEW.get()) {
      group.add(new MorphComponentAction(leafComponent, mySurface));
    }
    if (ConvertToConstraintLayoutAction.ENABLED) {
      group.add(new ConvertToConstraintLayoutAction(mySurface));
    }
    group.add(createRefactoringMenu());

    group.add(new MockupEditAction(mySurface));
    if (leafComponent != null && StudioFlags.NELE_MOCKUP_EDITOR.get() && Mockup.hasMockupAttribute(leafComponent)) {
      group.add(new MockupDeleteAction(leafComponent));
    }
    group.addSeparator();
  }

  private void addViewHandlerActions(@NotNull DefaultActionGroup group,
                                     @NotNull NlComponent component,
                                     @NotNull List<NlComponent> selection) {
    // Look up view handlers
    int prevCount = group.getChildrenCount();
    NlComponent parent = !component.isRoot() ? component.getParent() : null;
    addActions(group, component, parent, selection, false);
    if (group.getChildrenCount() > prevCount) {
      group.addSeparator();
    }
  }

  @Override
  public void addActions(@NotNull DefaultActionGroup group, @Nullable NlComponent component, @Nullable NlComponent parent,
                         @NotNull List<NlComponent> newSelection, boolean toolbar) {
    SceneView screenView = mySurface.getCurrentSceneView();
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
      List<NlComponent> selectedChildren = Lists.newArrayListWithCapacity(newSelection.size());
      for (NlComponent selected : newSelection) {
        if (selected.getParent() == parent) {
          selectedChildren.add(selected);
        }
      }
      addViewActionsForHandler(group, parent, selectedChildren, editor, handler, toolbar);
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
    }
    else {
      viewActions.addAll(ViewHandlerManager.get(mySurface.getProject()).getPopupMenuActions(handler));
    }
    Collections.sort(viewActions);

    group.removeAll();
    List<AnAction> target = Lists.newArrayList();
    for (ViewAction viewAction : viewActions) {
      addActions(target, toolbar, viewAction, mySurface.getProject(), editor, handler, component, newSelection);
    }
    boolean lastWasSeparator = false;
    for (AnAction action : target) {
      // Merge repeated separators
      boolean isSeparator = action instanceof Separator;
      if (isSeparator && lastWasSeparator) {
        continue;
      }

      group.add(action);
      lastWasSeparator = isSeparator;
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
                  boolean toolbar,
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
      if (((ViewActionSeparator)viewAction).isVisible(editor, handler, parent, newSelection)) {
        target.add(Separator.getInstance());
      }
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
    else if (viewAction instanceof NestedViewActionMenu) {
      // Can't place toolbar popups in menus
      if (toolbar) {
        target.add(new ViewActionToolbarMenuWrapper((NestedViewActionMenu)viewAction, editor, handler, parent, newSelection));
      }
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
      if (myAction.affectsUndo()) {
        NlWriteCommandAction.run(myComponent, Strings.nullToEmpty(e.getPresentation().getText()), () ->
          myAction.perform(myEditor, myHandler, myComponent, mySelectedChildren, e.getModifiers()));
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
  private class ToggleViewActionWrapper extends AnAction implements ViewActionPresentation {
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
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      boolean newState = !myAction.isSelected(myEditor, myHandler, myComponent, mySelectedChildren);
      if (myAction.affectsUndo()) {
        NlWriteCommandAction.run(myComponent, Strings.nullToEmpty(e.getPresentation().getText()), () -> applySelection(newState));
      }
      else {
        try {
          applySelection(newState);
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
        addActions(actions, false, viewAction, mySurface.getProject(), myEditor, myHandler, myComponent, mySelectedChildren);
      }
      return actions.toArray(AnAction.EMPTY_ARRAY);
    }
  }

  private class ViewActionToolbarMenuWrapper extends DropDownAction implements ViewActionPresentation {
    private final NestedViewActionMenu myAction;
    private final ViewEditor myEditor;
    private final ViewHandler myHandler;
    private final NlComponent myComponent;
    private final List<NlComponent> mySelectedChildren;
    private Presentation myCurrentPresentation;

    public ViewActionToolbarMenuWrapper(@NotNull NestedViewActionMenu action,
                                        @NotNull ViewEditor editor,
                                        @NotNull ViewHandler handler,
                                        @NotNull NlComponent component,
                                        @NotNull List<NlComponent> selectedChildren) {
      super("", action.getLabel(), action.getDefaultIcon());
      myAction = action;
      myEditor = editor;
      myHandler = handler;
      myComponent = component;
      mySelectedChildren = selectedChildren;
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(action.getDefaultIcon());
      presentation.setDescription(action.getLabel());
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

    @Override
    protected boolean updateActions() {
      removeAll();
      List<List<ViewAction>> rows = myAction.getActions();
      if (rows.size() == 1) {
        List<AnAction> actions = Lists.newArrayList();
        for (ViewAction viewAction : rows.get(0)) {
          addActions(actions, false, viewAction, mySurface.getProject(), myEditor, myHandler, myComponent, mySelectedChildren);
        }
        addAll(actions);
      }
      return true;
    }

    @Override
    protected JPanel createCustomComponentPopup() {
      List<List<ViewAction>> rows = myAction.getActions();
      if (rows.size() == 1) {
        return null;
      }

      com.intellij.openapi.actionSystem.ActionManager actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance();
      JPanel panel = new JPanel(new VerticalLayout(0));
      for (List<ViewAction> row : rows) {
        if (row.size() == 1 && row.get(0) instanceof ViewActionSeparator) {
          if (((ViewActionSeparator)row.get(0)).isVisible(myEditor, myHandler, myComponent, mySelectedChildren)) {
            panel.add(new JSeparator());
          }
          continue;
        }
        List<AnAction> actions = Lists.newArrayList();
        for (ViewAction viewAction : row) {
          addActions(actions, false, viewAction, mySurface.getProject(), myEditor, myHandler, myComponent, mySelectedChildren);
        }
        ActionGroup group = new DefaultActionGroup(actions);
        ActionToolbar toolbar = actionManager.createActionToolbar("DynamicToolbar", group, true);
        panel.add(toolbar.getComponent());
      }
      return panel;
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
}
