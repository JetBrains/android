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
import com.android.tools.profilers.AutoCompleteTextField;
import com.android.tools.profilers.ContextMenuInstaller;
import com.android.tools.profilers.ExportDialog;
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
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class IntellijProfilerComponents implements IdeProfilerComponents {

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
    IntelliJStackTraceView stackTraceView = new IntelliJStackTraceView(myProject, model);
    stackTraceView.installNavigationContextMenu(createContextMenuInstaller());
    return stackTraceView;
  }

  @NotNull
  @Override

  public ContextMenuInstaller createContextMenuInstaller() {
    return new IntellijContextMenuInstaller();
  }

  @NotNull
  @Override
  public ExportDialog createExportDialog() {
    return new IntellijExportDialog(myProject);
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

  @NotNull
  @Override
  public AutoCompleteTextField createAutoCompleteTextField(@NotNull String placeholder,
                                                           @NotNull String value,
                                                           @NotNull Collection<String> variants) {
    return new IntellijAutoCompleteTextField(myProject, placeholder , value, variants);
  }
}
