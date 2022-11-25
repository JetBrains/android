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
import com.android.tools.adtui.util.ActionToolbarUtil;
import com.android.tools.idea.common.actions.GotoComponentAction;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.ui.designer.overlays.OverlayConfiguration;
import com.android.tools.idea.ui.designer.overlays.OverlayMenuAction;
import com.android.tools.idea.uibuilder.actions.ConvertToConstraintLayoutAction;
import com.android.tools.idea.uibuilder.actions.DisableToolsVisibilityAndPositionInPreviewAction;
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
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.type.LayoutFileType;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.JBUI;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
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
  /**
   * Data key for the actions work in Layout Editor. This includes NlDesignSurface and ActionToolBar, but **exclude** all attached
   * TODO: Try to make all actions work for all design tools, so we can remove this data key.
   */
  public static final DataKey<NlDesignSurface> LAYOUT_EDITOR = DataKey.create(NlDesignSurface.class.getName() + "_LayoutEditor");

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
    JComponent focusablePane = mySurface.getLayeredPane();
    registerAction(mySelectNextAction, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), focusablePane);
    registerAction(mySelectPreviousAction, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), focusablePane);
    registerAction(mySelectNextAction, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), focusablePane);
    registerAction(mySelectPreviousAction, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), focusablePane);
  }

  @NotNull
  private static ActionGroup createRefactoringMenu() {
    DefaultActionGroup group = DefaultActionGroup.createPopupGroup(() -> "_Refactor");
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
      super(text, refactoringAction.getTemplatePresentation().getDescription(), null);
      myRefactoringAction = refactoringAction;
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
  public DefaultActionGroup getPopupMenuActions(@Nullable NlComponent leafComponent) {
    DefaultActionGroup group = new DefaultActionGroup();

    SceneView screenView = mySurface.getFocusedSceneView();
    if (screenView != null) {
      if (leafComponent != null) {
        // Look up view handlers
        int prevCount = group.getChildrenCount();
        // Add popup menu action
        addActions(group, leafComponent.getParent(), leafComponent, screenView.getSelectionModel().getSelection(), false);
        if (group.getChildrenCount() > prevCount) {
          group.addSeparator();
        }
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
    if (leafComponent != null) {
      group.add(new MorphComponentAction(leafComponent));
    }
    if (ConvertToConstraintLayoutAction.ENABLED) {
      group.add(new ConvertToConstraintLayoutAction(mySurface));
    }
    group.add(createRefactoringMenu());

    group.addSeparator();
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

  /**
   * Adds the actions for the given leafComponent and selection. The actions of leafComponent (if any) are followed by the actions of
   * selection.
   *
   * @param group         The group to collect the added view actions.
   * @param parent        The shared parent of selection. Can be null (e.g. there is no selection).
   * @param leafComponent The target {@link NlComponent} which ask for actions. For example, the right-clicked component when opening
   *                      context menu.
   * @param selection     The current selected NlComponent
   * @param toolbar       Indicate the added actions are for toolbar or context menu.
   */
  private void addActions(@NotNull DefaultActionGroup group,
                          @Nullable NlComponent parent,
                          @Nullable NlComponent leafComponent,
                          @NotNull List<NlComponent> selection,
                          boolean toolbar) {
    SceneView screenView = mySurface.getFocusedSceneView();
    if (screenView == null) {
      return;
    }

    Project project = mySurface.getProject();
    if (project.isDisposed()) {
      return;
    }

    ViewEditor editor = new ViewEditorImpl(screenView);

    // TODO: Perform caching
    List<AnAction> actions = new ArrayList<>();
    ViewHandler leafHandler = null;
    if (leafComponent != null) {
      leafHandler = ViewHandlerManager.get(project).getHandler(leafComponent);
      if (leafHandler != null) {
        actions.addAll(getViewActionsForHandler(leafComponent, selection, editor, leafHandler, toolbar));
      }
    }
    if (parent != null) {
      ViewHandler handler = ViewHandlerManager.get(project).getHandler(parent);
      if (handler != null && leafHandler != handler) {
        List<NlComponent> selectedChildren = Lists.newArrayListWithCapacity(selection.size());
        // TODO(b/150297043): If the selected components have different parents, do we need to provide the view action from parents?
        for (NlComponent selected : selection) {
          if (selected.getParent() == parent) {
            selectedChildren.add(selected);
          }
        }
        actions.addAll(getViewActionsForHandler(parent, selectedChildren, editor, handler, toolbar));
      }
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

  @Override
  @NotNull
  public DefaultActionGroup getToolbarActions(@NotNull List<NlComponent> selection) {
    DefaultActionGroup group = new DefaultActionGroup();
    NlComponent sharedParent = findSharedParent(selection);
    addActions(group, sharedParent, null, selection, true);
    return group;
  }

  @Nullable
  @Override
  public JComponent getSceneViewContextToolbar(@NotNull SceneView sceneView) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(DisableToolsVisibilityAndPositionInPreviewAction.INSTANCE);
    ActionToolbar actionToolbar =
      com.intellij.openapi.actionSystem.ActionManager.getInstance().createActionToolbar("SceneView", group, true);
    actionToolbar.setTargetComponent(mySurface);
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    JComponent toolbarComponent = actionToolbar.getComponent();
    toolbarComponent.setOpaque(false);
    toolbarComponent.setBorder(JBUI.Borders.empty());
    return toolbarComponent;
  }

  @Nullable
  @Override
  public JComponent getSceneViewLeftBar(@NotNull SceneView sceneView) {
    if (OverlayConfiguration.EP_NAME.hasAnyExtensions()) {
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new OverlayMenuAction.ToggleCachedOverlayAction(sceneView.getSurface()));
      group.add(new OverlayMenuAction.UpdateOverlayAction(sceneView.getSurface()));
      group.add(new OverlayMenuAction.CancelOverlayAction(sceneView.getSurface()));

      ActionToolbar actionToolbar = com.intellij.openapi.actionSystem.ActionManager.getInstance()
        .createActionToolbar("SceneView", group, false);
      actionToolbar.setTargetComponent(mySurface);
      actionToolbar.setReservePlaceAutoPopupIcon(false);
      JComponent toolbarComponent = actionToolbar.getComponent();
      toolbarComponent.setOpaque(false);
      toolbarComponent.setBorder(JBUI.Borders.empty());
      return toolbarComponent;
    }

    return null;
  }

  @NotNull
  private List<AnAction> getViewActionsForHandler(@NotNull NlComponent component,
                                        @NotNull List<NlComponent> newSelection,
                                        @NotNull ViewEditor editor,
                                        @NotNull ViewHandler handler,
                                        boolean toolbar) {
    List<ViewAction> viewActions = new ArrayList<>();
    if (toolbar) {
      viewActions.addAll(ViewHandlerManager.get(mySurface.getProject()).getToolbarActions(handler));
    }
    else {
      SceneComponent sceneComponent = editor.getScene().getSceneComponent(component);
      if (sceneComponent != null) {
        viewActions.addAll(ViewHandlerManager.get(mySurface.getProject()).getPopupMenuActions(sceneComponent, handler));
      }
    }

    List<AnAction> target = new ArrayList<>();
    for (ViewAction viewAction : viewActions) {
      addActions(target, toolbar, viewAction, editor, handler, component, newSelection);
    }

    return target;
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
      List<ToggleViewActionWrapper> actions = new ArrayList<>();
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
      super(action.getLabel(), action.getLabel(), action.getIcon());
      myAction = action;
      myEditor = editor;
      myHandler = handler;
      myComponent = component;
      mySelectedChildren = selectedChildren;
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
      // GuiInputHandler which observes mouse and keyboard events in the design surface.
      // This misses pure keyboard events when the design surface does not have focus
      // (but moving the mouse over the design surface updates it immediately.)
      //
      // (Longer term we consider having a singleton Toolkit listener which listens
      // for AWT events globally and tracks the most recent global modifier key state.)
      int modifiersEx = mySurface.getGuiInputHandler().getLastModifiersEx();

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
      super(action.getUnselectedLabel(), action.getUnselectedLabel(), action.getUnselectedIcon());
      myAction = action;
      myEditor = editor;
      myHandler = handler;
      myComponent = component;
      mySelectedChildren = selectedChildren;
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
      super(action.getLabel(), action.getLabel(), action.getIcon());
      myAction = action;
      myEditor = editor;
      myHandler = handler;
      myComponent = component;
      mySelectedChildren = selectedChildren;
      setPopup(true);
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
      List<AnAction> actions = new ArrayList<>();
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
      super(action.getLabel(), action.getLabel(), action.getIcon());
      myAction = action;
      myEditor = editor;
      myHandler = handler;
      myComponent = component;
      mySelectedChildren = selectedChildren;
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
    protected boolean updateActions(@NotNull DataContext context) {
      removeAll();
      List<List<ViewAction>> rows = myAction.getActions();
      if (rows.size() == 1) {
        List<AnAction> actions = new ArrayList<>();
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
        List<AnAction> actions = new ArrayList<>();
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
