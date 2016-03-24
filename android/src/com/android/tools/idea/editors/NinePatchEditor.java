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

package com.android.tools.idea.editors;

import com.android.draw9patch.graphics.GraphicsUtilities;
import com.android.draw9patch.ui.ImageEditorPanel;
import com.android.draw9patch.ui.ImageViewer;
import com.intellij.AppTopics;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class NinePatchEditor implements FileEditor, ImageViewer.PatchUpdateListener {
  private static final Logger LOG =
    Logger.getInstance("#com.android.tools.idea.editors.NinePatchEditor");
  private static final String NAME = "9-Patch";

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private final Project myProject;
  private VirtualFile myFile;

  private BufferedImage myBufferedImage;
  private ImageEditorPanel myImageEditorPanel;
  private boolean myDirtyFlag;

  public NinePatchEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myProject = project;

    // Listen for 'Save All' events
    FileDocumentManagerListener saveListener = new FileDocumentManagerAdapter() {
      @Override
      public void beforeAllDocumentsSaving() {
        saveFile();
      }
    };
    project.getMessageBus().connect(this).subscribe(AppTopics.FILE_DOCUMENT_SYNC, saveListener);

    myFile = file;
    try {
      myBufferedImage = loadImage(file);
      myImageEditorPanel = new ImageEditorPanel(null, myBufferedImage, myFile.getName());
      myImageEditorPanel.getViewer().addPatchUpdateListener(this);
    } catch (IOException e) {
      LOG.error("Unexpected exception while reading 9-patch file", e);
    }
  }

  private BufferedImage loadImage(VirtualFile file) throws IOException {
    myBufferedImage = ImageIO.read(file.getInputStream());
    if (myBufferedImage == null) {
      throw new IOException("Unable to parse file: " + file.getCanonicalPath());
    }
    return GraphicsUtilities.toCompatibleImage(myBufferedImage);
  }

  private void saveFile() {
    UIUtil.invokeLaterIfNeeded(() -> {
      try {
        saveFileFromEDT();
      }
      catch (IOException e) {
        LOG.error("Unexpected exception while saving 9-patch file", e);
      }
    });
  }

  // Saving Files using VFS requires EDT and a write action.
  private void saveFileFromEDT() throws IOException {
    if (!myDirtyFlag) {
      return;
    }

    new WriteCommandAction.Simple(myProject, "Update N-patch", PsiManager.getInstance(myProject).findFile(myFile)) {
      @Override
      protected void run() throws Throwable {
        ByteArrayOutputStream stream = new ByteArrayOutputStream((int)myFile.getLength());
        ImageIO.write(myBufferedImage, "PNG", stream);
        myFile.setBinaryContent(stream.toByteArray());
      }
    }.execute();

    myDirtyFlag = false;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myImageEditorPanel != null ? myImageEditorPanel :
           new JLabel("Unexpected error while loading 9-patch file. See Event Log for details.");
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myImageEditorPanel;
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return myDirtyFlag;
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

  @Override
  public void dispose() {
    saveFile();

    if (myImageEditorPanel != null) {
      myImageEditorPanel.getViewer().removePatchUpdateListener(this);
      myImageEditorPanel.dispose();
      myImageEditorPanel = null;
    }
  }

  @Override
  public void patchesUpdated() {
    myDirtyFlag = true;
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
