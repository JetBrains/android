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

import static com.android.tools.idea.uibuilder.api.actions.ViewActionsKt.withRank;

import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.editor.ActionToolbarUtil;
import com.android.tools.idea.actions.MockupDeleteAction;
import com.android.tools.idea.actions.MockupEditAction;
import com.android.tools.idea.common.actions.GotoComponentAction;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.actions.ConvertToConstraintLayoutAction;
import com.android.tools.idea.uibuilder.actions.MorphComponentAction;
import com.android.tools.idea.uibuilder.actions.SelectAllAction;
import com.android.tools.idea.uibuilder.actions.SelectNextAction;
import com.android.tools.idea.uibuilder.actions.SelectParentAction;
import com.android.tools.idea.uibuilder.actions.SelectPreviousAction;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction;
import com.android.tools.idea.uibuilder.api.actions.NestedViewActionMenu;
import com.android.tools.idea.uibuilder.api.actions.ToggleViewAction;
import com.android.tools.idea.uibuilder.api.actions.ToggleViewActionGroup;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewActionMenu;
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation;
import com.android.tools.idea.uibuilder.api.actions.ViewActionSeparator;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.type.LayoutFileType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.IncorrectOperationException;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import org.jetbrains.android.refactoring.AndroidExtractAsIncludeAction;
import org.jetbrains.android.refactoring.AndroidExtractStyleAction;
import org.jetbrains.android.refactoring.AndroidInlineIncludeAction;
import org.jetbrains.android.refactoring.AndroidInlineStyleReferenceAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides and handles actions in the layout editor
 */
public class NlActionManager extends ActionManager<NlDesignSurface> {
  private AnAction mySelectAllAction;
  private AnAction mySelectParent;
  private GotoComponentAction myGotoComponentAction;
  private AnAction mySelectNextAction;
  private AnAction mySelectPreviousAction;

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
   * <li> {@link SelectNextAction}
   * <li> {@link SelectPreviousAction}
   * </ul>
   */
  @Override
  public void registerActionsShortcuts(@NotNull JComponent component) {
    if (mySelectAllAction == null) {
      mySelectAllAction = new SelectAllAction(mySurface);
      myGotoComponentAction = new GotoComponentAction(mySurface);
      mySelectParent = new SelectParentAction(mySurface);
      mySelectNextAction = new SelectNextAction(mySurface);
      mySelectPreviousAction = new SelectPreviousAction(mySurface);
    }
    registerAction(mySelectAllAction, IdeActions.ACTION_SELECT_ALL, component);
    registerAction(myGotoComponentAction, IdeActions.ACTION_GOTO_DECLARATION, component);
    registerAction(mySelectParent, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), component);
    registerAction(mySelectNextAction, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), mySurface);
    registerAction(mySelectPreviousAction, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), mySurface);
    registerAction(mySelectNextAction, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), mySurface);
    registerAction(mySelectPreviousAction, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), mySurface);
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

    private AndroidRefactoringActionWrapper(@NotNull String text, @NotNull AnAction refactoringAction) {
      super(text, null, null);
      myRefactoringAction = refactoringAction;
      getTemplatePresentation().setDescription(refactoringAction.getTemplatePresentation().getDescription());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myRefactoringAction.actionPerformed(e);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
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
  @VisibleForTesting
  public DefaultActionGroup getPopupMenuActions(@Nullable NlComponent leafComponent) {
    DefaultActionGroup group = new DefaultActionGroup();

    SceneView screenView = mySurface.getFocusedSceneView();
    if (screenView != null) {
      if (leafComponent != null) {
        addViewHandlerActions(group, leafComponent, screenView.getSelectionModel().getSelection());
      }

      group.addSeparator();
    }

    if (mySurface.getLayoutType() == LayoutFileType.INSTANCE) {
      createLayoutOnlyActions(leafComponent, group);
    }

    // getRegisteredActionByName can return null if the action id does not exist. For these ones,
    // we know they are always present.
    //noinspection ConstantConditions
    group.add(getRegisteredActionByName(IdeActions.ACTION_CUT));
    //noinspection ConstantConditions
    group.add(getRegisteredActionByName(IdeActions.ACTION_COPY));
    //noinspection ConstantConditions
    group.add(getRegisteredActionByName(IdeActions.ACTION_PASTE));
    group.addSeparator();
    //noinspection ConstantConditions
    group.add(getRegisteredActionByName(IdeActions.ACTION_DELETE));
    group.addSeparator();
    group.add(myGotoComponentAction);

    return group;
  }

  private void createLayoutOnlyActions(@Nullable NlComponent leafComponent, @NotNull DefaultActionGroup group) {
    if (leafComponent != null && StudioFlags.NELE_CONVERT_VIEW.get()) {
      group.add(new MorphComponentAction(leafComponent));
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
    addPopupMenuActions(group, component, selection);
    if (group.getChildrenCount() > prevCount) {
      group.addSeparator();
    }
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
      }
      else if (parent != selected.getParent()) {
        parent = null;
        break;
      }
    }
    return parent;
  }

  private void addActions(@NotNull DefaultActionGroup group, @Nullable NlComponent component,
                          @NotNull List<NlComponent> newSelection, boolean toolbar) {
    NlComponent parent;
    if (component == null) {
      parent = findSharedParent(newSelection);
      if (parent == null) {
        return;
      }
    }
    else {
      parent = component.getParent();
    }

    SceneView screenView = mySurface.getFocusedSceneView();
    if (screenView == null) {
      return;
    }

    ViewEditor editor = new ViewEditorImpl(screenView);

    // TODO: Perform caching
    List<AnAction> actions = new ArrayList<>();
    if (component != null) {
      ViewHandler handler = ViewHandlerManager.get(mySurface.getProject()).getHandler(component);
      actions.addAll(getViewActionsForHandler(component, newSelection, editor, handler, toolbar));
    }
    if (parent != null) {
      ViewHandler handler = ViewHandlerManager.get(mySurface.getProject()).getHandler(parent);
      List<NlComponent> selectedChildren = Lists.newArrayListWithCapacity(newSelection.size());
      for (NlComponent selected : newSelection) {
        if (selected.getParent() == parent) {
          selectedChildren.add(selected);
        }
      }
      actions.addAll(getViewActionsForHandler(parent, selectedChildren, editor, handler, toolbar));
    }

    boolean lastWasSeparator = false;
    for (AnAction action : actions) {
      // Merge repeated separators
      boolean isSeparator = action instanceof Separator;
      if (isSeparator && lastWasSeparator) {
        continue;
      }

      group.add(action);
      lastWasSeparator = isSeparator;
    }
  }

  private void addPopupMenuActions(@NotNull DefaultActionGroup group,
                                     @Nullable NlComponent component,
                                     @NotNull List<NlComponent> newSelection) {
    addActions(group, component, newSelection, false);
  }

  @Override
  @NotNull
  public DefaultActionGroup getToolbarActions(@Nullable NlComponent component,
                                @NotNull List<NlComponent> newSelection) {
    DefaultActionGroup group = new DefaultActionGroup();
    addActions(group, component, newSelection, true);

    return group;
  }

  @NotNull
  private List<AnAction> getViewActionsForHandler(@NotNull NlComponent component,
                                        @NotNull List<NlComponent> newSelection,
                                        @NotNull ViewEditor editor,
                                        @Nullable ViewHandler handler,
                                        boolean toolbar) {
    if (handler == null) {
      return Collections.emptyList();
    }

    List<ViewAction> viewActions = createViewActionList();
    if (toolbar) {
      viewActions.addAll(ViewHandlerManager.get(mySurface.getProject()).getToolbarActions(handler));
    }
    else {
      SceneComponent sceneComponent = editor.getScene().getSceneComponent(component);
      if (sceneComponent != null) {
        viewActions.addAll(ViewHandlerManager.get(mySurface.getProject()).getPopupMenuActions(sceneComponent, handler));
      }
    }

    Collections.sort(viewActions);

    List<AnAction> target = Lists.newArrayList();
    for (ViewAction viewAction : viewActions) {
      addActions(target, toolbar, viewAction, editor, handler, component, newSelection);
    }

    return target;
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
            viewAction = withRank(viewAction, prev.getRank() + 5);
          }
        }
        else if (viewAction.getRank() == -1) {
          viewAction =  withRank(viewAction, 0);
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
                  @NotNull ViewEditor editor,
                  @NotNull ViewHandler handler,
                  @NotNull NlComponent parent,
                  @NotNull List<NlComponent> newSelection) {
    if (viewAction instanceof DirectViewAction) {
      target.add(new DirectViewActionWrapper((DirectViewAction)viewAction, editor, handler, parent, newSelection));
    }
    else if (viewAction instanceof ViewActionSeparator) {
      if (((ViewActionSeparator)viewAction).isVisible(editor, handler, parent, newSelection)) {
        target.add(Separator.getInstance());
      }
    }
    else if (viewAction instanceof ToggleViewAction) {
      target.add(new ToggleViewActionWrapper((ToggleViewAction)viewAction, editor, handler, parent, newSelection));
    }
    else if (viewAction instanceof ToggleViewActionGroup) {
      List<ToggleViewActionWrapper> actions = Lists.newArrayList();
      for (ToggleViewAction action : ((ToggleViewActionGroup)viewAction).getActions()) {
        actions.add(new ToggleViewActionWrapper(action, editor, handler, parent, newSelection));
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
    private final DirectViewAction myAction;
    private final ViewHandler myHandler;
    private final ViewEditor myEditor;
    private final NlComponent myComponent;
    private final List<NlComponent> mySelectedChildren;
    private Presentation myCurrentPresentation;

    private DirectViewActionWrapper(@NotNull DirectViewAction action,
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
      presentation.setIcon(action.getIcon());
      presentation.setText(action.getLabel());
    }

    @Override
    public final void actionPerformed(@NotNull AnActionEvent e) {
      // TODO: refactor this function to remove getConfirmationMessage and affectsUndo.
      String confirmationMessage = myAction.getConfirmationMessage();
      if (confirmationMessage != null
          && Messages.showYesNoDialog(mySurface, confirmationMessage, myAction.getLabel(), myAction.getIcon()) != Messages.YES) {
          // User refused the action.
          return;
      }
      if (myAction.affectsUndo()) {
        NlWriteCommandActionUtil.run(myComponent, Strings.nullToEmpty(e.getPresentation().getText()), () ->
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
    public void update(@NotNull AnActionEvent e) {
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
      int modifiersEx = mySurface.getInteractionManager().getLastModifiersEx();

      myCurrentPresentation = e.getPresentation();
      try {
        myAction.updatePresentation(this, myEditor, myHandler, myComponent, mySelectedChildren, modifiersEx);
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
    private final ToggleViewAction myAction;
    private final ViewEditor myEditor;
    private final ViewHandler myHandler;
    private final NlComponent myComponent;
    private final List<NlComponent> mySelectedChildren;
    private Presentation myCurrentPresentation;
    private ToggleViewActionWrapper myGroupSibling;

    private ToggleViewActionWrapper(@NotNull ToggleViewAction action,
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
      presentation.setText(action.getUnselectedLabel());
      presentation.setIcon(action.getUnselectedIcon());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      boolean newState = !myAction.isSelected(myEditor, myHandler, myComponent, mySelectedChildren);
      if (myAction.affectsUndo()) {
        NlWriteCommandActionUtil.run(myComponent, Strings.nullToEmpty(e.getPresentation().getText()), () -> applySelection(newState));
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

    private ViewActionMenuWrapper(@NotNull ViewActionMenu action,
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
      presentation.setIcon(action.getIcon());
      presentation.setText(action.getLabel());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
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
        addActions(actions, false, viewAction, myEditor, myHandler, myComponent, mySelectedChildren);
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

    private ViewActionToolbarMenuWrapper(@NotNull NestedViewActionMenu action,
                                         @NotNull ViewEditor editor,
                                         @NotNull ViewHandler handler,
                                         @NotNull NlComponent component,
                                         @NotNull List<NlComponent> selectedChildren) {
      super(null, action.getLabel(), action.getIcon());
      myAction = action;
      myEditor = editor;
      myHandler = handler;
      myComponent = component;
      mySelectedChildren = selectedChildren;
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(action.getIcon());
      presentation.setDescription(action.getLabel());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
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
          addActions(actions, false, viewAction, myEditor, myHandler, myComponent, mySelectedChildren);
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
          addActions(actions, false, viewAction, myEditor, myHandler, myComponent, mySelectedChildren);
        }
        ActionGroup group = new DefaultActionGroup(actions);
        ActionToolbar toolbar = actionManager.createActionToolbar("DynamicToolbar", group, true);
        ActionToolbarUtil.makeToolbarNavigable(toolbar);
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
