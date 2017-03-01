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
package com.android.tools.idea.profilers;

import com.android.tools.idea.profilers.actions.NavigateToCodeAction;
import com.android.tools.idea.profilers.stacktrace.IntelliJStackTraceView;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.common.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBLoadingPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class IntellijProfilerComponents implements IdeProfilerComponents {
  private static final String COMPONENT_CONTEXT_MENU = "ComponentContextMenu";

  @NotNull private final Project myProject;

  public IntellijProfilerComponents(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public LoadingPanel createLoadingPanel() {
    return new LoadingPanel() {
      private JBLoadingPanel myLoadingPanel = new JBLoadingPanel(new BorderLayout(), myProject);

      @NotNull
      @Override
      public JComponent getComponent() {
        return myLoadingPanel;
      }

      @Override
      public void setLoadingText(@NotNull String loadingText) {
        myLoadingPanel.setLoadingText(loadingText);
      }

      @Override
      public void startLoading() {
        myLoadingPanel.startLoading();
      }

      @Override
      public void stopLoading() {
        myLoadingPanel.stopLoading();
      }
    };
  }

  @NotNull
  @Override
  public TabsPanel createTabsPanel() {
    return new IntellijTabsPanel(myProject);
  }

  @NotNull
  @Override
  public StackTraceView createStackView(@NotNull StackTraceModel model) {
    return new IntelliJStackTraceView(myProject, model);
  }

  @Override
  public void installNavigationContextMenu(@NotNull JComponent component,
                                           @NotNull CodeNavigator navigator,
                                           @NotNull Supplier<CodeLocation> codeLocationSupplier) {

    DefaultActionGroup popupGroup = createOrGetActionGroup(component);
    popupGroup.add(new NavigateToCodeAction(codeLocationSupplier, navigator));
  }

  @Override
  public void installContextMenu(@NotNull JComponent component, @NotNull ContextMenuItem contextMenuItem) {
    DefaultActionGroup popupGroup = createOrGetActionGroup(component);
    popupGroup.add(new AnAction(null, null, contextMenuItem.getIcon()) {
      @Override
      public void update(AnActionEvent e) {
        super.update(e);

        Presentation presentation = e.getPresentation();
        presentation.setText(contextMenuItem.getText());
        presentation.setEnabled(contextMenuItem.isEnabled());
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        contextMenuItem.run();
      }
    });
  }

  @Override
  public void openExportDialog(@NotNull Supplier<String> dialogTitleSupplier,
                               @NotNull Supplier<String> extensionSupplier,
                               @NotNull Consumer<File> saveToFile) {
    ApplicationManager.getApplication().invokeLater(() -> {
      String extension = extensionSupplier.get();
      if (extension != null) {
        ExportDialog dialog = new ExportDialog(myProject, dialogTitleSupplier.get(), extension);
        if (dialog.showAndGet()) {
          saveToFile.accept(dialog.getFile());
        }
      }
    });
  }

  @NotNull
  private DefaultActionGroup createOrGetActionGroup(@NotNull JComponent component) {
    DefaultActionGroup actionGroup = (DefaultActionGroup)component.getClientProperty(COMPONENT_CONTEXT_MENU);
    if (actionGroup == null) {
      final DefaultActionGroup newActionGroup = new DefaultActionGroup();
      component.putClientProperty(COMPONENT_CONTEXT_MENU, newActionGroup);
      component.addMouseListener(new PopupHandler() {
        @Override
        public void invokePopup(Component comp, int x, int y) {
          ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, newActionGroup).getComponent().show(comp, x, y);
        }
      });
      actionGroup = newActionGroup;
    }

    return actionGroup;
  }

  @NotNull
  @Override
  public FileViewer createFileViewer(@NotNull File file) {
    return new IntellijFileViewer(file);
  }
}
