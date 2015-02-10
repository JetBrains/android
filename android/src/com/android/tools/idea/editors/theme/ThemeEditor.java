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

import com.android.sdklib.devices.Device;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.intellij.ProjectTopics;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public class ThemeEditor extends UserDataHolderBase implements FileEditor {
  private final ThemeEditorVirtualFile myVirtualFile;
  private final Configuration myConfiguration;
  private VirtualFile myFile;
  private final ThemeEditorComponent myComponent;
  private long myModificationCount;

  public ThemeEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myFile = file;
    myVirtualFile = (ThemeEditorVirtualFile)file;
    Module module = myVirtualFile.getModule();

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    myConfiguration = facet.getConfigurationManager().getConfiguration(myFile);
    myModificationCount = getModificationCount();

    // We currently use the default device. We will dynamically adjust the width and height depending on the size of the window.
    // TODO: Add configuration chooser to allow changing parameters of the configuration.
    final Device device = new Device.Builder(myConfiguration.getDevice()).build();
    myConfiguration.setDevice(device, false);
    myComponent = new ThemeEditorComponent(myConfiguration, module);

    // If project roots change, reload the themes. This happens for example once the libraries have finished loading.
    project.getMessageBus().connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        long newModificationCount = getModificationCount();
        if (myModificationCount != newModificationCount) {
          myModificationCount = newModificationCount;
          myComponent.reload(myComponent.getPreviousSelectedTheme());
        }
      }
    });

    // a theme can contain theme attributes (listed in attrs.xml) and also global defaults (all of attrs.xml)
    myComponent.reload(null/*defaultThemeName*/);
  }

  /**
   * Returns the modification count of the app resources repository or -1 if it fails to get the count.
   */
  private long getModificationCount() {
    AppResourceRepository resourceRepository = AppResourceRepository.getAppResources(myConfiguration.getModule(), true);
    return resourceRepository != null ? resourceRepository.getModificationCount() : -1;
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
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NotNull FileEditorState fileEditorState) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public void selectNotify() {
    long newModificationCount = getModificationCount();
    if (myModificationCount != newModificationCount) {
      myModificationCount = newModificationCount;
      myComponent.reload(myComponent.getPreviousSelectedTheme());
    }
  }

  @Override
  public void deselectNotify() {
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

  @Override
  public void dispose() {
    // TODO what should go here?
  }

  public ThemeEditorVirtualFile getVirtualFile() {
    return myVirtualFile;
  }
}
