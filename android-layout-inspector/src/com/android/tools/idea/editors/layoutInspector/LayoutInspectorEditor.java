/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.editors.layoutInspector;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.ddmlib.Client;
import com.android.layoutinspector.model.ClientWindow;
import com.android.layoutinspector.parser.LayoutFileDataParser;
import com.android.tools.idea.flags.ExperimentalSettingsConfigurable;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.DataManager;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.SearchTextField;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LayoutInspectorEditor extends UserDataHolderBase implements FileEditor {
  private final VirtualFile myVirtualFile;
  private final Project myProject;
  private LayoutInspectorEditorPanel myPanel;
  private LayoutInspectorContext myContext;

  public static class NewVersionNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
    private static Key<EditorNotificationPanel> KEY = Key.create("new.layout.inspector.notification");
    private static final Key<String> HIDDEN_KEY = Key.create("new.layout.inspector.notification.hidden");
    private static final String DISABLE_KEY = "new.layout.inspector.notification.disabled";

    @NotNull
    @Override
    public Key<EditorNotificationPanel> getKey() {
      return KEY;
    }

    @Nullable
    @Override
    public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                           @NotNull FileEditor fileEditor,
                                                           @NotNull Project project) {
      if (fileEditor instanceof LayoutInspectorEditor && StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLED.get()) {
        EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor);
        panel.setText("Using API 29? Try out the new Live Layout Inspector.");

        if (fileEditor.getUserData(HIDDEN_KEY) != null || PropertiesComponent.getInstance().isTrueValue(DISABLE_KEY)) {
          return null;
        }

        panel.createActionLabel("Try it", () -> ShowSettingsUtil.getInstance().showSettingsDialog(
          project, ExperimentalSettingsConfigurable.class, c -> {
            AtomicReference<Runnable> runnableReference = new AtomicReference<>();
            Runnable runnable = () -> {
              JComponent component = c.createComponent();
              if (component.getParent() == null) {
                // The component isn't completely set up right away, and we need to be able to iterate up the hierarchy to get the
                // search box. Reschedule the runnable until it's attached.
                ApplicationManager.getApplication().invokeLater(runnableReference.get());
                return;
              }
              SearchTextField textField = DataManager.getInstance().getDataContext(component).getData(SearchTextField.KEY);
              if (textField != null) {  // shouldn't be null, but at least don't blow up if it is. We just won't get highlighting.
                textField.setText("Enable Live Layout Inspector");
              }
            };
            runnableReference.set(runnable);
            ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
          }));
        panel.createActionLabel("Hide notification", () -> {
          fileEditor.putUserData(HIDDEN_KEY, "true");
          update(file, project);
        });
        panel.createActionLabel("Don't show again", () -> {
          PropertiesComponent.getInstance().setValue(DISABLE_KEY, "true");
          update(file, project);
        });

        return panel;
      }
      return null;
    }

    private static void update(@NotNull VirtualFile file, @NotNull Project project) {
      EditorNotifications.getInstance(project).updateNotifications(file);
    }
  }

  public LayoutInspectorEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myVirtualFile = file;
    myProject = project;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    if (myPanel == null) {
      try {
        myContext = new LayoutInspectorContext(LayoutFileDataParser.parseFromFile(virtualToIoFile(myVirtualFile)), this);
      }
      catch (IOException e) {
        return new JLabel(e.getLocalizedMessage(), SwingConstants.CENTER);
      }

      myPanel = new LayoutInspectorEditorPanel(this, myProject, myContext);
    }

    return myPanel;
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  public String getName() {
    return "Layout Inspector";
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void deselectNotify() {
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void selectNotify() {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @Override
  public boolean isValid() {
    return myVirtualFile.isValid();
  }

  public void setSources(@Nullable Client client, @Nullable ClientWindow window) {
    myContext.setSources(client, window);
  }
}
