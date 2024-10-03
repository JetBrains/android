/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.ui;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.ex.FileLookup.Finder;
import com.intellij.openapi.fileChooser.ex.FileLookup.LookupFile;
import com.intellij.openapi.fileChooser.ex.FileLookup.LookupFilter;
import com.intellij.openapi.fileChooser.ex.FileTextFieldImpl;
import com.intellij.openapi.fileChooser.ex.LocalFsFinder;
import com.intellij.openapi.fileChooser.ex.LocalFsFinder.FileChooserFilter;
import com.intellij.openapi.fileChooser.ex.LocalFsFinder.VfsFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBTextField;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import javax.swing.JTextField;

/** A text field that auto-completes paths in the workspace root. */
// This outer class basically a copy of FileTextFieldImpl.Vfs but we can't use that directly because
// we need to specify our own custom Finder.
public final class WorkspaceFileTextField extends FileTextFieldImpl {

  private WorkspaceFileTextField(
      WorkspacePathResolver pathResolver,
      JTextField textField,
      LookupFilter filter,
      Disposable parent) {
    super(textField, new WorkspaceFinder(pathResolver), filter, ImmutableMap.of(), parent);
  }

  public static WorkspaceFileTextField create(
      WorkspacePathResolver pathResolver,
      FileChooserDescriptor descriptor,
      int columns,
      Disposable parent) {
    JTextField textField = new WorkspacePathTextField(pathResolver, columns);
    return new WorkspaceFileTextField(
        pathResolver, textField, new FileChooserFilter(descriptor, /* showHidden= */ true), parent);
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    LookupFile lookupFile = getFile();
    if (lookupFile instanceof VfsFile) {
      return ((VfsFile) lookupFile).getFile();
    }
    return null;
  }

  // Scheduled for removal in a future IJ version.
  @Override
  @Nullable
  public VirtualFile getSelectedFile() {
    return getVirtualFile();
  }

  private static class WorkspaceFinder implements Finder {

    private final WorkspacePathResolver pathResolver;

    private WorkspaceFinder(WorkspacePathResolver pathResolver) {
      this.pathResolver = pathResolver;
    }

    @Nullable
    @Override
    public LookupFile find(String filePath) {
      Path path = Paths.get(normalize(filePath));
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByNioFile(path);
      if (vFile != null) {
        return new LocalFsFinder.VfsFile(vFile);
      } else if (path.isAbsolute()) {
        return new LocalFsFinder.IoFile(path);
      }
      return null;
    }

    @Override
    public String normalize(String path) {
      File file = new File(path);
      if (!file.isAbsolute()) {
        file = pathResolver.resolveToFile(path);
      }

      return file.getAbsolutePath();
    }

    @Override
    public String getSeparator() {
      return File.separator;
    }
  }

  // FileTextFieldImpl calls setText with the absolute path after every filename autocomplete. But
  // we don't want to swap in the absolute path, we'd rather just show the path relative to the
  // workspace root. So we override setText().
  private static class WorkspacePathTextField extends JBTextField {

    final WorkspacePathResolver pathResolver;

    WorkspacePathTextField(WorkspacePathResolver pathResolver, int columns) {
      super(columns);
      this.pathResolver = pathResolver;
    }

    @Override
    public void setText(String path) {
      WorkspacePath workspacePath = pathResolver.getWorkspacePath(new File(path));
      if (workspacePath == null) {
        super.setText(path);
        return;
      }

      String relativePath = workspacePath.relativePath();
      if (path.endsWith(File.separator) && !relativePath.endsWith(File.separator)) {
        relativePath += File.separator;
      }
      super.setText(relativePath);
    }
  }
}
