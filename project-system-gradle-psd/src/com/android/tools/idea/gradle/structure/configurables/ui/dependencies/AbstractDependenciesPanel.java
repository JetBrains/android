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

import static com.intellij.icons.AllIcons.Nodes.Module;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.PlatformIcons.JAR_ICON;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.dependencies.details.DependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.configurables.ui.ChooseModuleDialog;
import com.android.tools.idea.gradle.structure.configurables.ui.EmptyPanel;
import com.android.tools.idea.gradle.structure.dependencies.AddJarDependencyDialog;
import com.android.tools.idea.gradle.structure.dependencies.AddJarDependencyDialogKt;
import com.android.tools.idea.gradle.structure.dependencies.AddLibraryDependencyDialog;
import com.android.tools.idea.gradle.structure.dependencies.AddLibraryDependencyDialogKt;
import com.android.tools.idea.gradle.structure.dependencies.AddModuleDependencyDialog;
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsPath;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.structure.dialog.Header;
import com.android.tools.idea.structure.dialog.TrackedConfigurableKt;
import com.android.tools.idea.structure.dialog.VersionCatalogWarningHeader;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
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
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractDependenciesPanel extends JPanel implements Place.Navigator, Disposable {
  @NotNull private final PsContext myContext;
  @NotNull private final EmptyPanel myEmptyDetailsPanel;
  @NotNull private final DependencyInfoPanel myInfoPanel;
  @NotNull private final JScrollPane myInfoScrollPane;
  @NotNull private final Header myHeader;
  @NotNull private final JPanel myContentsPanel;
  @NotNull private final String myEmptyText;

  @NotNull private final List<DependencyDetails> myDependencyDetails = new ArrayList<>();

  @Nullable private final PsModule myModule;

  private DependencyDetails myCurrentDependencyDetails;
  private History myHistory;
  private IssuesViewer myIssuesViewer;
  private AddLibraryDependencyAction myAddLibraryDependencyAction;
  private AddJarDependencyAction myAddJarDependencyAction;

  protected AbstractDependenciesPanel(@NotNull String title, @NotNull PsContext context, @Nullable PsModule module) {
    super();
    this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    myContext = context;
    myModule = module;

    myEmptyText = String.format("Please select a dependency from the '%1$s' view", title);

    myEmptyDetailsPanel = new EmptyPanel(myEmptyText);
    myInfoPanel = new DependencyInfoPanel();

    myInfoScrollPane = createScrollPane(myEmptyDetailsPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    myInfoScrollPane.setBorder(JBUI.Borders.empty());

    myHeader = new Header(title);
    add(myHeader);

    Project project = context.getProject().getIdeProject();
    boolean projectUsesVersionCatalogs = GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject();
    if (projectUsesVersionCatalogs) {
      if (StudioFlags.GRADLE_VERSION_CATALOG_DISPLAY_CAVEATS.get()) {
        add(new VersionCatalogWarningHeader());
      }
    }

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
    myInfoPanel.setIssuesViewer(myIssuesViewer);
  }

  protected void displayIssues(@NotNull Collection<PsIssue> issues, @Nullable PsPath scope) {
    assert myIssuesViewer != null;
    myIssuesViewer.display(issues, scope);
    myInfoPanel.revalidateAndRepaintPanel();
  }

  protected void updateDetails(@Nullable PsBaseDependency selected) {
    if (selected != null) {
      DependencyDetails newDetails = findDetails(selected);
      if (myCurrentDependencyDetails != newDetails) {
        myCurrentDependencyDetails = newDetails;
        if (myCurrentDependencyDetails != null) {
          myInfoPanel.setDependencyDetails(myCurrentDependencyDetails);
        }
        myInfoScrollPane.setViewportView(myCurrentDependencyDetails == null ? myEmptyDetailsPanel : myInfoPanel.getPanel());
      }
      if (myCurrentDependencyDetails != null) {
        myCurrentDependencyDetails.display(selected);
      }
    } else {
      myCurrentDependencyDetails = null;
      myInfoScrollPane.setViewportView(myEmptyDetailsPanel);
    }
  }

  @Nullable
  private DependencyDetails findDetails(@NotNull PsBaseDependency selected) {
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
      public void actionPerformed(@NotNull AnActionEvent e) {
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
    addDependencyAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_NEW_ELEMENT).getShortcutSet(), myContentsPanel);

    actions.add(addDependencyAction);
    List<AnAction> extraToolbarActions = getExtraToolbarActions(myContentsPanel);
    if (!extraToolbarActions.isEmpty()) {
      actions.addSeparator();
      actions.addAll(extraToolbarActions);
    }

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TOP", actions, true);
    toolbar.setTargetComponent(null);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    actionsPanel.add(toolbarComponent, BorderLayout.CENTER);

    return actionsPanel;
  }

  @NotNull
  protected List<AnAction> getExtraToolbarActions(@NotNull JComponent focusComponent) {
    return Collections.emptyList();
  }

  @NotNull
  private List<AbstractPopupAction> getPopupActions() {
    if (myAddLibraryDependencyAction == null) {
      myAddLibraryDependencyAction = new AddLibraryDependencyAction();
    }
    if (myAddJarDependencyAction == null) {
      myAddJarDependencyAction = new AddJarDependencyAction();
    }

    List<AbstractPopupAction> actions = Lists.newArrayList(myAddLibraryDependencyAction, myAddJarDependencyAction);

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
        dependency = model.toText();
      }
    }
    putPath(place, dependency);
  }

  public void putPath(@NotNull Place place, @NotNull String dependency) {
    place.putPath(getPlaceName(), dependency);
  }

  @NotNull
  protected abstract String getPlaceName();

  private class AddLibraryDependencyAction extends AbstractAddDependencyAction {
    AddLibraryDependencyAction() {
      super(AddLibraryDependencyDialogKt.ADD_LIBRARY_DEPENDENCY_DIALOG_TITLE, "Library Dependency", LIBRARY_ICON, 1);
    }

    @Override
    protected void showAddDependencyDialog(@NotNull PsModule module) {
      AddLibraryDependencyDialog dialog = new AddLibraryDependencyDialog(myContext, module);
      if (dialog.showAndGet()) {
        TrackedConfigurableKt.logUsagePsdAction(
          module.getParent().getIdeProject(),
          AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_DEPENDENCIES_ADD_LIBRARY);
        dialog.addNewDependencies();
      }
    }
  }

  private class AddJarDependencyAction extends AbstractAddDependencyAction {
    AddJarDependencyAction() {
      super(AddJarDependencyDialogKt.ADD_JAR_DEPENDENCY_DIALOG_TITLE, "JAR/AAR Dependency", JAR_ICON, 2);
    }

    @Override
    protected void showAddDependencyDialog(@NotNull PsModule module) {
      AddJarDependencyDialog dialog = new AddJarDependencyDialog(module);
      if (dialog.showAndGet()) {
        TrackedConfigurableKt.logUsagePsdAction(
          module.getParent().getIdeProject(),
          AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_DEPENDENCIES_ADD_JAR);
        dialog.addNewDependencies();
      }
    }
  }

  private class AddModuleDependencyAction extends AbstractAddDependencyAction {
    AddModuleDependencyAction() {
      super(AddModuleDependencyDialog.TITLE, "Module Dependency", Module, 3);
    }

    @Override
    protected void showAddDependencyDialog(@NotNull PsModule module) {
      AddModuleDependencyDialog dialog = new AddModuleDependencyDialog(module);
      if (dialog.showAndGet()) {
        TrackedConfigurableKt.logUsagePsdAction(
          module.getParent().getIdeProject(),
          AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_DEPENDENCIES_ADD_MODULE);
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
