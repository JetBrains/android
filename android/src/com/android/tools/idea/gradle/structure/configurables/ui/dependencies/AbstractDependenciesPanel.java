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
package com.android.tools.idea.gradle.structure.configurables.ui.dependencies;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.DependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.configurables.ui.ChooseModuleDialog;
import com.android.tools.idea.gradle.structure.configurables.ui.EmptyPanel;
import com.android.tools.idea.gradle.structure.dependencies.AddLibraryDependencyDialog;
import com.android.tools.idea.gradle.structure.dependencies.AddModuleDependencyDialog;
import com.android.tools.idea.gradle.structure.model.*;
import com.android.tools.idea.structure.dialog.Header;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SideBorder;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.android.tools.idea.gradle.structure.model.PsDependency.TextType.FOR_NAVIGATION;
import static com.intellij.icons.AllIcons.Nodes.Module;
import static com.intellij.ui.IdeBorderFactory.createEmptyBorder;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public abstract class AbstractDependenciesPanel extends JPanel implements Place.Navigator, Disposable {
  @NotNull private final PsContext myContext;
  @NotNull private final EmptyPanel myEmptyDetailsPanel;
  @NotNull private final DependencyInfoPanel myInfoPanel;
  @NotNull private final JScrollPane myInfoScrollPane;
  @NotNull private final Header myHeader;
  @NotNull private final JPanel myContentsPanel;
  @NotNull private final String myEmptyText;

  @NotNull private final List<DependencyDetails> myDependencyDetails = Lists.newArrayList();

  @Nullable private final PsModule myModule;

  private DependencyDetails myCurrentDependencyDetails;
  private History myHistory;
  private IssuesViewer myIssuesViewer;
  private AddLibraryDependencyAction myAddLibraryDependencyAction;

  protected AbstractDependenciesPanel(@NotNull String title, @NotNull PsContext context, @Nullable PsModule module) {
    super(new BorderLayout());
    myContext = context;
    myModule = module;

    myEmptyText = String.format("Please select a dependency from the '%1$s' view", title);

    myEmptyDetailsPanel = new EmptyPanel(myEmptyText);
    myInfoPanel = new DependencyInfoPanel();

    myInfoScrollPane = createScrollPane(myEmptyDetailsPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    myInfoScrollPane.setBorder(createEmptyBorder());

    myHeader = new Header(title);
    add(myHeader, BorderLayout.NORTH);

    JBSplitter splitter = new JBSplitter(true, "psd.editable.dependencies.main.horizontal.splitter.proportion", 0.55f);

    myContentsPanel = new JPanel(new BorderLayout());
    myContentsPanel.setBorder(new SideBorder(OnePixelDivider.BACKGROUND, SideBorder.BOTTOM));

    splitter.setFirstComponent(myContentsPanel);
    splitter.setSecondComponent(myInfoScrollPane);

    add(splitter, BorderLayout.CENTER);
  }

  @NotNull
  public abstract JComponent getPreferredFocusedComponent();

  protected void addDetails(@NotNull DependencyDetails details) {
    myDependencyDetails.add(details);
  }

  protected void setIssuesViewer(@NotNull IssuesViewer issuesViewer) {
    myIssuesViewer = issuesViewer;
    myIssuesViewer.setShowEmptyText(false);
    myInfoPanel.setIssuesViewer(myIssuesViewer);
  }

  protected void displayIssues(@NotNull Collection<PsIssue> issues) {
    assert myIssuesViewer != null;
    myIssuesViewer.display(issues);
    myInfoPanel.revalidateAndRepaintPanel();
    ApplicationManager.getApplication().invokeLater(() -> myInfoScrollPane.getVerticalScrollBar().setValue(0));
  }

  protected void updateDetails(@Nullable PsDependency selected) {
    String scope = selected != null ? selected.getJoinedConfigurationNames() : null;
    updateDetails(selected, scope);
  }

  protected void updateDetails(@Nullable PsDependency selected, @Nullable String configurationNames) {
    if (selected != null) {
      myCurrentDependencyDetails = findDetails(selected);
      if (myCurrentDependencyDetails != null) {
        myInfoPanel.setDependencyDetails(myCurrentDependencyDetails);
        myInfoScrollPane.setViewportView(myInfoPanel.getPanel());
        myCurrentDependencyDetails.display(selected, configurationNames);
        return;
      }
    }
    myCurrentDependencyDetails = null;
    myInfoScrollPane.setViewportView(myEmptyDetailsPanel);
  }

  @Nullable
  private DependencyDetails findDetails(@NotNull PsDependency selected) {
    for (DependencyDetails details : myDependencyDetails) {
      if (details.getSupportedModelType().isInstance(selected)) {
        return details;
      }
    }
    return null;
  }

  @Nullable
  protected DependencyDetails getCurrentDependencyDetails() {
    return myCurrentDependencyDetails;
  }

  @NotNull
  protected final JPanel createActionsPanel() {
    JPanel actionsPanel = new JPanel(new BorderLayout());

    DefaultActionGroup actions = new DefaultActionGroup();

    AnAction addDependencyAction = new DumbAwareAction("Add Dependency", "", IconUtil.getAddIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        JBPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<AbstractPopupAction>(null, getPopupActions()) {
          @Override
          public Icon getIconFor(AbstractPopupAction action) {
            return action.icon;
          }

          @Override
          public boolean isMnemonicsNavigationEnabled() {
            return true;
          }

          @Override
          public PopupStep onChosen(AbstractPopupAction action, boolean finalChoice) {
            return doFinalStep(action::execute);
          }

          @Override
          @NotNull
          public String getTextFor(AbstractPopupAction action) {
            return "&" + action.index + "  " + action.text;
          }
        });
        popup.show(new RelativePoint(actionsPanel, new Point(0, actionsPanel.getHeight() - 1)));
      }
    };

    actions.add(addDependencyAction);
    List<AnAction> extraToolbarActions = getExtraToolbarActions();
    if (!extraToolbarActions.isEmpty()) {
      actions.addSeparator();
      actions.addAll(extraToolbarActions);
    }

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TOP", actions, true);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    actionsPanel.add(toolbarComponent, BorderLayout.CENTER);

    return actionsPanel;
  }

  @NotNull
  protected List<AnAction> getExtraToolbarActions() {
    return Collections.emptyList();
  }

  @NotNull
  private List<AbstractPopupAction> getPopupActions() {
    if (myAddLibraryDependencyAction == null) {
      myAddLibraryDependencyAction = new AddLibraryDependencyAction();
    }

    List<AbstractPopupAction> actions = Lists.newArrayList(myAddLibraryDependencyAction);

    PsProject project = myContext.getProject();
    if (project.getModelCount() > 1) {
      // Only show the "Add Module Dependency" action if there is more than one module in the project.
      actions.add(new AddModuleDependencyAction());
    }

    return actions;
  }

  @NotNull
  protected JPanel getContentsPanel() {
    return myContentsPanel;
  }

  @NotNull
  protected PsContext getContext() {
    return myContext;
  }

  @NotNull
  public Header getHeader() {
    return myHeader;
  }

  @NotNull
  public String getEmptyText() {
    return myEmptyText;
  }

  @Override
  public void setHistory(History history) {
    myHistory = history;
  }

  @Nullable
  protected History getHistory() {
    return myHistory;
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    String dependency = "";
    DependencyDetails details = getCurrentDependencyDetails();
    if (details != null) {
      PsBaseDependency model = details.getModel();
      if (model != null) {
        dependency = model.toText(FOR_NAVIGATION);
      }
    }
    putPath(place, dependency);
  }

  public void putPath(@NotNull Place place, @NotNull String dependency) {
    place.putPath(getPlaceName(), dependency);
  }

  @NotNull
  protected abstract String getPlaceName();

  public abstract void selectDependency(@Nullable String dependency);

  private class AddLibraryDependencyAction extends AbstractAddDependencyAction {
    AddLibraryDependencyAction() {
      super(AddLibraryDependencyDialog.TITLE, "Library Dependency", LIBRARY_ICON, 1);
    }

    @Override
    protected void showAddDependencyDialog(@NotNull PsModule module) {
      AddLibraryDependencyDialog dialog = new AddLibraryDependencyDialog(module);
      if (dialog.showAndGet()) {
        dialog.addNewDependencies();
      }
    }
  }

  private class AddModuleDependencyAction extends AbstractAddDependencyAction {
    AddModuleDependencyAction() {
      super(AddModuleDependencyDialog.TITLE, "Module Dependency", Module, 2);
    }

    @Override
    protected void showAddDependencyDialog(@NotNull PsModule module) {
      AddModuleDependencyDialog dialog = new AddModuleDependencyDialog(module);
      if (dialog.showAndGet()) {
        dialog.addNewDependencies();
      }
    }
  }

  private abstract class AbstractAddDependencyAction extends AbstractPopupAction {
    @NotNull private final String myTitle;

    AbstractAddDependencyAction(@NotNull String title, @NotNull String text, @NotNull Icon icon, int index) {
      super(text, icon, index);
      myTitle = title;
    }

    @Override
    void execute() {
      if (myModule == null) {
        PsProject project = myContext.getProject();
        int modelCount = project.getModelCount();
        if (modelCount == 1) {
          // If there is only one module, select that one.
          Ref<PsModule> moduleRef = new Ref<>();
          project.forEachModule(moduleRef::set);
          PsModule module = moduleRef.get();
          assert module != null;

          showAddDependencyDialog(module);
          return;
        }
        Consumer<PsModule> onOkTask = this::showAddDependencyDialog;
        ChooseModuleDialog dialog = new ChooseModuleDialog(project, onOkTask, myTitle);
        dialog.showAndGet();
        return;
      }
      showAddDependencyDialog(myModule);
    }

    protected abstract void showAddDependencyDialog(@NotNull PsModule module);
  }

  private static abstract class AbstractPopupAction implements ActionListener {
    @NotNull final String text;
    @NotNull final Icon icon;
    final int index;

    AbstractPopupAction(@NotNull String text, @NotNull Icon icon, int index) {
      this.text = text;
      this.icon = icon;
      this.index = index;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      execute();
    }

    abstract void execute();
  }
}
