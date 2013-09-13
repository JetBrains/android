/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.editors.navigation;

import com.android.navigation.Listener;
import com.android.navigation.NavigationModel;
import com.android.navigation.XMLReader;
import com.android.navigation.XMLWriter;
import com.intellij.AppTopics;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBScrollPane;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class NavigationEditor implements FileEditor {
  public static final String TOOLBAR = "NavigationEditorToolbar";
  private static final Logger LOG = Logger.getInstance("#" + NavigationEditor.class.getName());
  private static final String NAME = "Navigation";
  private static final int INITIAL_FILE_BUFFER_SIZE = 1000;
  private static final int SCROLL_UNIT_INCREMENT = 20;

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private NavigationModel myNavigationModel;
  private final Listener<NavigationModel.Event> myNavigationModelListener;
  private VirtualFile myFile;
  private JComponent myComponent;
  private boolean myDirty;

  public NavigationEditor(Project project, VirtualFile file) {
    // Listen for 'Save All' events
    FileDocumentManagerListener saveListener = new FileDocumentManagerAdapter() {
      @Override
      public void beforeAllDocumentsSaving() {
        try {
          saveFile();
        }
        catch (IOException e) {
          LOG.error("Unexpected exception while saving navigation file", e);
        }
      }
    };
    project.getMessageBus().connect(this).subscribe(AppTopics.FILE_DOCUMENT_SYNC, saveListener);

    myFile = file;
    try {
      myNavigationModel = read(file);
      // component = new NavigationModelEditorPanel1(project, file, read(file));
      NavigationEditorPanel editor = new NavigationEditorPanel(project, file, myNavigationModel);
      JBScrollPane scrollPane = new JBScrollPane(editor);
      scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
      JPanel p = new JPanel(new BorderLayout());

      JComponent controls = createToolbar(editor);
      p.add(controls, BorderLayout.NORTH);
      p.add(scrollPane);
      myComponent = p;
    }
    catch (FileReadException e) {
      myNavigationModel = new NavigationModel();
      {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel message = new JLabel("Invalid Navigation File");
        Font font = message.getFont();
        message.setFont(font.deriveFont(30f));
        panel.add(message, BorderLayout.NORTH);
        panel.add(new JLabel(e.getMessage()), BorderLayout.CENTER);
        myComponent = new JBScrollPane(panel);
      }
    }
    myNavigationModelListener = new Listener<NavigationModel.Event>() {
      @Override
      public void notify(@NotNull NavigationModel.Event event) {
        myDirty = true;
      }
    };
    myNavigationModel.getListeners().add(myNavigationModelListener);
  }

  // See  AndroidDesignerActionPanel
  protected JComponent createToolbar(NavigationEditorPanel myDesigner) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar zoomToolBar = actionManager.createActionToolbar(TOOLBAR, getActions(myDesigner), true);
    JPanel bottom = new JPanel(new BorderLayout());
    //bottom.add(layoutToolBar.getComponent(), BorderLayout.WEST);
    bottom.add(zoomToolBar.getComponent(), BorderLayout.EAST);
    panel.add(bottom, BorderLayout.SOUTH);

    return panel;
  }

  private static class FileReadException extends Exception {
    private FileReadException(Throwable throwable) {
      super(throwable);
    }
  }

  // See AndroidDesignerActionPanel
  private static ActionGroup getActions(final NavigationEditorPanel myDesigner) {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new AnAction(null, "Zoom Out (-)", AndroidIcons.ZoomOut) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myDesigner.zoom(false);
      }
    });
    group.add(new AnAction(null, "Reset Zoom to 100% (1)", AndroidIcons.ZoomActual) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myDesigner.setScale(1);
      }
    });
    group.add(new AnAction(null, "Zoom In (+)", AndroidIcons.ZoomIn) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myDesigner.zoom(true);
      }
    });

    return group;
  }

  private static NavigationModel read(VirtualFile file) throws FileReadException {
    try {
      return (NavigationModel)new XMLReader(file.getInputStream()).read();
    }
    catch (Exception e) {
      throw new FileReadException(e);
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
    return NAME;
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return myDirty;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public void selectNotify() {
  }

  @Override
  public void deselectNotify() {
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

  private void saveFile() throws IOException {
    if (myDirty) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream(INITIAL_FILE_BUFFER_SIZE);
      new XMLWriter(stream).write(myNavigationModel);
      myFile.setBinaryContent(stream.toByteArray());
      myDirty = false;
    }
  }

  @Override
  public void dispose() {
    try {
      saveFile();
    }
    catch (IOException e) {
      LOG.error("Unexpected exception while saving navigation file", e);
    }

    myNavigationModel.getListeners().remove(myNavigationModelListener);
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myUserDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myUserDataHolder.putUserData(key, value);
  }
}
