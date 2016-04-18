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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.DependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.configurables.ui.EmptyPanel;
import com.android.tools.idea.gradle.structure.dependencies.AddLibraryDependencyDialog;
import com.android.tools.idea.gradle.structure.configurables.ui.ChooseModuleDialog;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.structure.dialog.Header;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.intellij.ui.IdeBorderFactory.createEmptyBorder;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public abstract class AbstractDeclaredDependenciesPanel extends JPanel implements Place.Navigator, Disposable {
  @NotNull private final PsContext myContext;
  @NotNull private final EmptyPanel myEmptyDetailsPanel;
  @NotNull private final DependencyInfoPanel myInfoPanel;
  @NotNull private final JScrollPane myInfoScrollPane;
  @NotNull private final JPanel myContentsPanel;
  @NotNull private final String myEmptyText;

  @NotNull private final Map<Class<?>, DependencyDetails> myDependencyDetails = Maps.newHashMap();

  @Nullable private final PsAndroidModule myModule;

  private List<AbstractPopupAction> myPopupActions;
  private DependencyDetails myCurrentDependencyDetails;
  private History myHistory;

  protected AbstractDeclaredDependenciesPanel(@NotNull String title, @NotNull PsContext context, @Nullable PsAndroidModule module) {
    super(new BorderLayout());
    myContext = context;
    myModule = module;

    myEmptyText = String.format("Please select a dependency from the '%1$s' view", title);

    myEmptyDetailsPanel = new EmptyPanel(myEmptyText);
    myInfoPanel = new DependencyInfoPanel();

    myInfoScrollPane = createScrollPane(myEmptyDetailsPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    myInfoScrollPane.setBorder(createEmptyBorder());

    Header header = new Header(title);
    add(header, BorderLayout.NORTH);

    JBSplitter splitter = new JBSplitter(true, "psd.editable.dependencies.main.horizontal.splitter.proportion", 0.55f);

    myContentsPanel = new JPanel(new BorderLayout());
    myContentsPanel.setBorder(new SideBorder(OnePixelDivider.BACKGROUND, SideBorder.BOTTOM));

    splitter.setFirstComponent(myContentsPanel);
    splitter.setSecondComponent(myInfoScrollPane);

    add(splitter, BorderLayout.CENTER);
  }

  protected void addDetails(@NotNull DependencyDetails<?> details) {
    myDependencyDetails.put(details.getSupportedModelType(), details);
  }

  protected void setIssuesViewer(@NotNull IssuesViewer issuesViewer) {
    myInfoPanel.setIssuesViewer(issuesViewer);
  }

  protected void updateDetails(@Nullable PsAndroidDependency selected) {
    if (selected != null) {
      myCurrentDependencyDetails = myDependencyDetails.get(selected.getClass());
      if (myCurrentDependencyDetails != null) {
        myInfoPanel.setDependencyDetails(myCurrentDependencyDetails);
        myInfoScrollPane.setViewportView(myInfoPanel.getPanel());
        //noinspection unchecked
        myCurrentDependencyDetails.display(selected);
        return;
      }
    }
    myCurrentDependencyDetails = null;
    myInfoScrollPane.setViewportView(myEmptyDetailsPanel);
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
        initPopupActions();
        JBPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<AbstractPopupAction>(null, myPopupActions) {
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

  private void initPopupActions() {
    if (myPopupActions == null) {
      List<AbstractPopupAction> actions = Lists.newArrayList();
      actions.add(new AddDependencyAction());
      myPopupActions = actions;
    }
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

  protected void beforeAddingDependency() {
  }

  private class AddDependencyAction extends AbstractPopupAction {
    AddDependencyAction() {
      super("Artifact Dependency", LIBRARY_ICON, 1);
    }

    @Override
    void execute() {
      if (myModule == null) {
        Consumer<PsModule> onOkTask = this::showAddLibraryDependencyDialog;
        ChooseModuleDialog dialog = new ChooseModuleDialog(myContext.getProject(), onOkTask, "Add Library Dependency");
        dialog.showAndGet();
        return;
      }
      beforeAddingDependency();
      showAddLibraryDependencyDialog(myModule);
    }

    private void showAddLibraryDependencyDialog(@NotNull PsModule module) {
      AddLibraryDependencyDialog dialog = new AddLibraryDependencyDialog(module);
      if (dialog.showAndGet()) {
        dialog.addNewDependency();
      }
    }
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
