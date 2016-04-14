/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.intellij.ProjectTopics;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public class ThemeEditor extends UserDataHolderBase implements FileEditor {
  private final ThemeEditorVirtualFile myVirtualFile;
  private final ThemeEditorComponent myComponent;

  public ThemeEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myVirtualFile = (ThemeEditorVirtualFile)file;

    myComponent = new ThemeEditorComponent(project);
    Disposer.register(this, myComponent);

    // If project roots change, reload the themes. This happens for example once the libraries have finished loading.
    project.getMessageBus().connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        // Avoid invoking reload on the event listener thread. The rootsChanged event is called while holding the write lock
        // so calling reload can potentially cause a deadlock.
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            ConfiguredThemeEditorStyle theme = null;
            ConfiguredThemeEditorStyle subStyle = null;

            // If the currently selected module has been disposed we set everything to null to force a full reload.
            // The current module can be disposed if, for example, it's renamed.
            if (!myComponent.getSelectedModule().isDisposed()) {
              // If the SDK is changing we will not be able to reload anything as AndroidTargetData.getTargetData will be returning null;
              if (ModuleRootManager.getInstance(myComponent.getSelectedModule()).getSdk() == null) {
                return;
              }
              theme = myComponent.getSelectedTheme();
              subStyle = myComponent.getCurrentSubStyle();
            }

            myComponent.reload((theme == null) ? null : theme.getQualifiedName(), (subStyle == null) ? null : subStyle.getQualifiedName());
          }
        });
      }
    });
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  /**
   * Displayed in the IDE on the tab at the bottom of the editor.
   */
  @NotNull
  @Override
  public String getName() {
    return "Theme Editor";
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel fileEditorStateLevel) {
    ConfiguredThemeEditorStyle theme = myComponent.getSelectedTheme();
    ConfiguredThemeEditorStyle subStyle = myComponent.getCurrentSubStyle();
    return new ThemeEditorState(theme == null ? null : theme.getQualifiedName(),
                                subStyle == null ? null : subStyle.getQualifiedName(),
                                myComponent.getProportion(),
                                myComponent.getSelectedModule().getName());
  }

  @Override
  public void setState(@NotNull FileEditorState fileEditorState) {
    if (!(fileEditorState instanceof ThemeEditorState)) {
      return;
    }

    ThemeEditorState state = (ThemeEditorState)fileEditorState;
    myComponent.reload(state.getThemeName(), state.getSubStyleName(), state.getModuleName());

    myComponent.setProportion(state.getProportion());
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myVirtualFile.isValid();
  }

  @Override
  public void selectNotify() {
    myComponent.selectNotify();
  }

  @Override
  public void deselectNotify() {
    myComponent.deselectNotify();
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener propertyChangeListener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener propertyChangeListener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @NotNull
  public ThemeEditorVirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @Override
  public void dispose() {
  }
}
