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
package com.android.tools.idea.apk.viewer;

import com.android.SdkConstants;
import com.android.tools.apk.analyzer.ApkSizeCalculator;
import com.android.tools.apk.analyzer.Archive;
import com.android.tools.apk.analyzer.Archives;
import com.android.tools.apk.analyzer.internal.ArchiveTreeNode;
import com.android.tools.idea.apk.viewer.arsc.ArscViewer;
import com.android.tools.idea.apk.viewer.dex.DexFileViewer;
import com.android.tools.idea.apk.viewer.diff.ApkDiffPanel;
import com.android.tools.idea.apk.viewer.diff.ApkDiffParser;
import com.android.tools.idea.editors.NinePatchEditorProvider;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.file.BatchFileChangeListener;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.BinaryLightVirtualFile;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

public class ApkEditor extends UserDataHolderBase implements FileEditor, ApkViewPanel.Listener {
  private final Project myProject;
  private final VirtualFile myBaseFile;
  private final VirtualFile myRoot;
  private ApkViewPanel myApkViewPanel;
  private Archive myArchive;

  private JBSplitter mySplitter;
  private ApkFileEditorComponent myCurrentEditor;

  public ApkEditor(@NotNull Project project, @NotNull VirtualFile baseFile, @NotNull VirtualFile root) {
    myProject = project;
    myBaseFile = baseFile;
    myRoot = root;

    mySplitter = new JBSplitter(true, "android.apk.viewer", 0.62f);
    mySplitter.setName("apkViwerContainer");

    Path apk = VfsUtilCore.virtualToIoFile(baseFile).toPath();
    try {
      myArchive = Archives.open(apk);
      myApkViewPanel = new ApkViewPanel(myArchive, new ApkParser(myArchive, ApkSizeCalculator.getDefault(apk)));
      myApkViewPanel.setListener(this);
      mySplitter.setFirstComponent(myApkViewPanel.getContainer());
    }
    catch (IOException e) {
      myArchive = null;
      mySplitter.setFirstComponent(new JBLabel(e.toString()));
    }

    mySplitter.setSecondComponent(new JPanel());
  }

  /**
   * Changes the editor displayed based on the path selected in the tree.
   */
  @Override
  public void selectionChanged(@NotNull Archive archive, @Nullable ArchiveTreeNode entry) {
    if (myCurrentEditor != null) {
      Disposer.dispose(myCurrentEditor);
    }

    Path p = entry == null ? null : entry.getData().getPath();
    myCurrentEditor = getEditor(archive, p);
    mySplitter.setSecondComponent(myCurrentEditor.getComponent());
  }

  @Override
  public void selectApkAndCompare() {
    FileChooserDescriptor desc = new FileChooserDescriptor(true, false, false, false, false, false);
    desc.withFileFilter(file -> ApkFileSystem.EXTENSIONS.contains(file.getExtension()));
    VirtualFile file = FileChooser.chooseFile(desc, myProject, null);
    if(file == null) {
      // user canceled
      return;
    }
    VirtualFile newApk = ApkFileSystem.getInstance().getRootByLocal(file);
    assert newApk != null;

    DialogBuilder builder = new DialogBuilder(myProject);
    builder.setTitle(myRoot.getName() + " vs " + newApk.getName());
    ApkDiffParser parser = new ApkDiffParser(myRoot, newApk);
    ApkDiffPanel panel = new ApkDiffPanel(parser);
    builder.setCenterPanel(panel.getContainer());
    builder.setPreferredFocusComponent(panel.getPreferredFocusedComponent());
    builder.show();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return mySplitter;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myApkViewPanel.getPreferredFocusedComponent();
  }

  @NotNull
  @Override
  public String getName() {
    return myBaseFile.getName();
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
    return myBaseFile.isValid();
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
    if (myCurrentEditor != null) {
      Disposer.dispose(myCurrentEditor);
      myCurrentEditor = null;
    }

    if (myArchive != null) {
      try {
        myArchive.close();
      }
      catch (IOException e) {
        Logger.getInstance(ApkEditor.class).warn(e);
      }
      myArchive = null;
    }
  }

  @NotNull
  private ApkFileEditorComponent getEditor(@NotNull Archive archive, @Nullable Path p) {
    if (p == null) {
      return new EmptyPanel();
    }

    Path fileName = p.getFileName();
    if ("resources.arsc".equals(fileName.toString())) {
      byte[] arscContent;
      try {
        arscContent = Files.readAllBytes(p);
      }
      catch (IOException e) {
        return new EmptyPanel();
      }
      return new ArscViewer(arscContent);
    }

    if (p.toString().endsWith(SdkConstants.EXT_DEX)) {
      return new DexFileViewer(myProject, p, myBaseFile.getParent());
    }

    VirtualFile file = createVirtualFile(archive, p);
    Optional<FileEditorProvider> providers = getFileEditorProviders(file);
    if (!providers.isPresent()) {
      return new EmptyPanel();
    }
    else {
      FileEditor editor = providers.get().createEditor(myProject, file);
      return new ApkFileEditorComponent() {
        @NotNull
        @Override
        public JComponent getComponent() {
          return editor.getComponent();
        }

        @Override
        public void dispose() {
          Disposer.dispose(editor);
        }
      };
    }
  }

  @Nullable
  private static VirtualFile createVirtualFile(@NotNull Archive archive, @NotNull Path p) {
    Path name = p.getFileName();
    if (name == null) {
      return null;
    }

    try {
      byte[] content = Files.readAllBytes(p);

      if (archive.isBinaryXml(p, content)) {
        content = BinaryXmlParser.decodeXml(name.toString(), content);
      }

      return new BinaryLightVirtualFile(name.toString(), content);
    }
    catch (IOException e) {
      return null;
    }
  }

  @NotNull
  private Optional<FileEditorProvider> getFileEditorProviders(@Nullable VirtualFile file) {
    if (file == null || file.isDirectory()) {
      return Optional.empty();
    }

    FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(myProject, file);

    // skip 9 patch editor since nine patch information has been stripped out
    return Arrays.stream(providers).filter(
      fileEditorProvider -> !(fileEditorProvider instanceof NinePatchEditorProvider)).findFirst();
  }
}
