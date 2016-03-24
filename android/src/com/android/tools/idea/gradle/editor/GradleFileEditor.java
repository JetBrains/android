/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.editor;

import com.android.tools.idea.gradle.editor.entity.GradleEditorEntityGroup;
import com.android.tools.idea.gradle.editor.parser.GradleEditorModelParserFacade;
import com.android.tools.idea.gradle.editor.ui.GradleEditorComponent;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.util.List;

public class GradleFileEditor extends UserDataHolderBase implements FileEditor {

  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myVirtualFile;
  @NotNull private final GradleEditorComponent myComponent;
  @Nullable private Document myDocument;
  @Nullable private DocumentListener myDocumentListener;
  @Nullable private MessageBusConnection myConnection;
  @Nullable private List<GradleEditorEntityGroup> myGroups;

  private volatile boolean myEditorTriggeredModificationInProgress;

  public GradleFileEditor(@NotNull VirtualFile virtualFile, @NotNull Project project) {
    myProject = project;
    myVirtualFile = virtualFile;
    myGroups = new GradleEditorModelParserFacade().parse(virtualFile, project);
    myComponent = new GradleEditorComponent(project, myGroups);
    myDocument = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (myDocument == null) {
      myDocumentListener = null;
      myConnection = null;
    }
    else {
      myDocument.addDocumentListener(myDocumentListener = new DocumentAdapter() {
        @Override
        public void documentChanged(DocumentEvent e) {
          Document document = e.getDocument();
          if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) {
            return;
          }
          if (!myEditorTriggeredModificationInProgress) {
            refresh();
          }
        }
      });
      myConnection = project.getMessageBus().connect();
      myConnection.subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
        @Override
        public void updateFinished(@NotNull Document doc) {
          if (!myEditorTriggeredModificationInProgress) {
            refresh();
          }
        }
      });
      myConnection.subscribe(GradleEditorNotificationListener.TOPIC, new GradleEditorNotificationListener() {
        @Override
        public void beforeChange() {
          myEditorTriggeredModificationInProgress = true;
        }

        @Override
        public void afterChange() {
          myEditorTriggeredModificationInProgress = false;
        }
      });
    }
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

  @NotNull
  @Override
  public String getName() {
    return AndroidBundle.message("android.gradle.editor.tab.name");
  }

  @Override
  public void setState(@NotNull FileEditorState state) {

  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {
    // Reload gradle editor model when the editor is selected.
    refresh();
  }

  @Override
  public void deselectNotify() {
    // There is a possible case that a user switches from 'gradle editor' tab to a regular 'text editor' tab. We want to flush
    // changes (if any).
    myComponent.flushChanges();
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
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
    if (myDocument != null && myDocumentListener != null) {
      myDocument.removeDocumentListener(myDocumentListener);
      myDocument = null;
      myDocumentListener = null;
    }
    if (myConnection != null) {
      myConnection.disconnect();
    }
    disposeGroups();
  }

  public void refresh() {
    if (myDocument != null) {
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
      psiDocumentManager.commitDocument(myDocument);
    }
    disposeGroups();
    myGroups = new GradleEditorModelParserFacade().parse(myVirtualFile, myProject);
    myComponent.setData(myGroups);
  }

  private void disposeGroups() {
    List<GradleEditorEntityGroup> groups = myGroups;
    if (groups != null) {
      for (GradleEditorEntityGroup group : groups) {
        Disposer.dispose(group);
      }
      myGroups = null;
    }
  }
}
