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
import com.android.tools.profilers.stacktrace.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBLoadingPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class IntellijProfilerComponents implements IdeProfilerComponents {
  private static final String COMPONENT_CONTEXT_MENU = "ComponentContextMenu";

  private static final Map<String, FileType> FILE_TYPE_MAP = new ImmutableMap.Builder<String, FileType>()
    .put(".html", StdFileTypes.HTML)
    .put(".xml", StdFileTypes.XML)
    .put(".json", FileTypeManager.getInstance().getStdFileType("JSON"))
    .build();

  private static final ImmutableSet<String> IMAGE_EXTENSIONS = ImmutableSet.of(".bmp", ".gif", ".jpeg", ".jpg", ".png");

  @NotNull private final Project myProject;

  public IntellijProfilerComponents(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public LoadingPanel createLoadingPanel(int delayMs) {
    return new LoadingPanel() {
      private final JBLoadingPanel myLoadingPanel = new JBLoadingPanel(new BorderLayout(), myProject, delayMs);

      @NotNull
      @Override
      public JComponent getComponent() {
        return myLoadingPanel;
      }

      @Override
      public void setChildComponent(@Nullable Component comp) {
        myLoadingPanel.getContentPanel().removeAll();
        if (comp != null) {
          myLoadingPanel.add(comp);
        }
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
  public DataViewer createFileViewer(@NotNull File file) {
    String fileName = file.getName();
    int dot = fileName.lastIndexOf('.');
    String extension = dot >= 0 && dot < fileName.length() ? fileName.substring(dot) : "";

    if (IMAGE_EXTENSIONS.contains(extension)) {
      BufferedImage image = null;
      try {
        image = ImageIO.read(file);
      } catch (IOException ignored) {
      }
      if (image != null) {
        return IntellijDataViewer.createImageViewer(image);
      }
      else {
        return IntellijDataViewer.createInvalidViewer();
      }
    }

    String content = null;
    if (file.exists()) {
      try {
        content = new String(Files.readAllBytes(file.toPath()));
      }
      catch (IOException ignored) {}
    }

    if (content == null) {
      return IntellijDataViewer.createInvalidViewer();
    }

    return IntellijDataViewer.createEditorViewer(content, FILE_TYPE_MAP.getOrDefault(extension, null));
  }

  @NotNull
  @Override
  public JComponent createResizableImageComponent(@NotNull BufferedImage image) {
    return new ResizableImage(image);
  }
}
