/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.ui.problems;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.BaseNavigateToSourceAction;
import com.intellij.ide.actions.NextOccurenceToolbarAction;
import com.intellij.ide.actions.PreviousOccurenceToolbarAction;
import com.intellij.ide.errorTreeView.ErrorTreeElement;
import com.intellij.ide.errorTreeView.NavigatableMessageElement;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.ide.errorTreeView.impl.ErrorTreeViewConfiguration;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.pom.Navigatable;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.OpenSourceUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;
import javax.swing.JComponent;

/** A custom error tree view panel for Blaze invocation errors. */
class BlazeProblemsViewPanel extends NewErrorTreeViewPanel {
  private final Project myProject;

  private static final DataKey<Navigatable> BLAZE_CONSOLE_NAVIGATABLE_DATA_KEY =
      DataKey.create("blaze.console.navigatable");

  private final ProblemsViewConfiguration configuration;
  private final AutoScrollToSourceHandler autoScrollToConsoleHandler;

  BlazeProblemsViewPanel(Project project) {
    super(project, "reference.problems.tool.window", false, false, null);
    myProject = project;
    myTree.getEmptyText().setText("No problems found");
    configuration = ProblemsViewConfiguration.getInstance(project);
    autoScrollToConsoleHandler =
        new AutoScrollToSourceHandler() {
          @Override
          protected boolean isAutoScrollMode() {
            return configuration.getAutoscrollToConsole();
          }

          @Override
          protected void setAutoScrollMode(boolean state) {
            configuration.setAutoscrollToConsole(state);
          }

          @Override
          protected void scrollToSource(Component tree) {
            BlazeProblemsViewPanel.this.scrollToSource(tree);
          }
        };
    autoScrollToConsoleHandler.install(myTree);
    add(createToolbarPanel(), BorderLayout.WEST);
  }

  /** A custom toolbar panel, without most of the irrelevant built-in items. */
  private JComponent createToolbarPanel() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new PreviousOccurenceToolbarAction(this)); // NOTYPO
    group.add(new NextOccurenceToolbarAction(this)); // NOTYPO
    fillRightToolbarGroup(group);
    ActionToolbar toolbar =
        ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.COMPILER_MESSAGES_TOOLBAR, group, false);
    // As target for the toolbar, we'd ideally use myMessageComponent of NewErrorTreeViewPanel as
    // the upstream implementation does in NewErrorTreeViewPanel#createToolbarPanel. However,
    // myMessageComponent is private. As next best option, we can only use the panel which
    // encompasses myMessageComponent, which is the whole BlazeProblemsViewPanel.
    toolbar.setTargetComponent(this);
    return toolbar.getComponent();
  }

  @Override
  protected void fillRightToolbarGroup(DefaultActionGroup group) {
    super.fillRightToolbarGroup(group);
    group.add(new AutoscrollToConsoleAction());
    group.add(new ShowWarningsAction());
    group.add(new ExpandAllAction(myTree));
    group.add(new CollapseAllAction(myTree));
  }

  @Override
  protected void addExtraPopupMenuActions(DefaultActionGroup group) {
    group.add(new OpenInConsoleAction());
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    if (BLAZE_CONSOLE_NAVIGATABLE_DATA_KEY.is(dataId)) {
      ErrorTreeElement selectedElement = getSelectedErrorTreeElement();
      if (selectedElement instanceof ProblemsViewMessageElement) {
        return ((ProblemsViewMessageElement) selectedElement).getBlazeConsoleNavigatable();
      }
    }
    return super.getData(dataId);
  }

  @Override
  protected boolean shouldShowFirstErrorInEditor() {
    return false;
  }

  @Override
  protected boolean canHideWarnings() {
    return true;
  }

  void addNavigableMessageElement(String groupName, NavigatableMessageElement element) {
    getErrorViewStructure().addNavigatableMessage(groupName, element);
    updateAddedElement(element);
    updateTree();
  }

  private void scrollToSource(Component tree) {
    DataContext dataContext = DataManager.getInstance().getDataContext(tree);
    getReady(dataContext)
        .doWhenDone(
            () ->
                TransactionGuard.submitTransaction(
                    ApplicationManager.getApplication(),
                    () -> {
                      DataContext context = DataManager.getInstance().getDataContext(tree);
                      Navigatable navigatable = BLAZE_CONSOLE_NAVIGATABLE_DATA_KEY.getData(context);
                      if (navigatable != null) {
                        OpenSourceUtil.navigate(false, true, navigatable);
                      }
                    }));
  }

  private ActionCallback getReady(DataContext context) {
    ToolWindow toolWindow = PlatformDataKeys.TOOL_WINDOW.getData(context);
    return toolWindow != null
        ? toolWindow.getReady(autoScrollToConsoleHandler)
        : ActionCallback.DONE;
  }

  private class AutoscrollToConsoleAction extends ToggleAction implements DumbAware {
    public AutoscrollToConsoleAction() {
      super("Autoscroll to console", "Autoscroll to console", AllIcons.Debugger.Console);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return configuration.getAutoscrollToConsole();
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      configuration.setAutoscrollToConsole(flag);
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private static class OpenInConsoleAction extends BaseNavigateToSourceAction {

    protected OpenInConsoleAction() {
      super(true);
      Presentation presentation = getTemplatePresentation();
      presentation.setText("Jump to console");
      presentation.setDescription("Open console view and navigate to the selected problem");
      presentation.setIcon(AllIcons.Actions.EditSource);
    }

    @Nullable
    @Override
    protected Navigatable[] getNavigatables(DataContext context) {
      Navigatable nav = BLAZE_CONSOLE_NAVIGATABLE_DATA_KEY.getData(context);
      return nav != null ? new Navigatable[] {nav} : null;
    }
  }

  private class ShowWarningsAction extends ToggleAction implements DumbAware {
    private final ErrorTreeViewConfiguration configuration;

    ShowWarningsAction() {
      super(IdeBundle.message("action.show.warnings"), null, AllIcons.General.ShowWarning);
      configuration = ErrorTreeViewConfiguration.getInstance(myProject);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return !configuration.isHideWarnings();
    }

    @Override
    public void setSelected(AnActionEvent event, boolean showWarnings) {
      if (showWarnings == isHideWarnings()) {
        configuration.setHideWarnings(!showWarnings);
        reload();
      }
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  @Override
  public boolean hasNextOccurence() {
    // workaround for NPE on empty trees
    // TODO: remove if/when https://youtrack.jetbrains.com/issue/IDEA-215994 is fixed
    Object root = myTree.getModel().getRoot();
    if (root == null) {
      return false;
    }
    return super.hasNextOccurence();
  }

  @Override
  public boolean hasPreviousOccurence() {
    // workaround for NPE on empty trees
    // TODO: remove if/when https://youtrack.jetbrains.com/issue/IDEA-215994 is fixed
    Object root = myTree.getModel().getRoot();
    if (root == null) {
      return false;
    }
    try {
      return super.hasPreviousOccurence();
    } catch (NoSuchElementException e) {
      return false;
    }
  }
}
