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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLoadingPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class BinaryXmlViewer implements ApkFileEditorComponent {
  private final File myApk;
  private volatile boolean myDisposed;

  private final Disposable myDisposable;
  private final JBLoadingPanel myLoadingPanel;
  private Editor myEditor;

  public BinaryXmlViewer(@NotNull VirtualFile apkFile, @NotNull VirtualFile file) {
    //noinspection Convert2Lambda // we need a new instance of this disposable every time, not just a lambda method
    myDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), myDisposable);
    myLoadingPanel.startLoading();

    myApk = VfsUtilCore.virtualToIoFile(apkFile);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Document document = getDocument(file);

      ApplicationManager.getApplication().invokeLater(() -> {
        if (myDisposed) {
          return;
        }

        myEditor = EditorFactory.getInstance().createViewer(document);
        myLoadingPanel.add(myEditor.getComponent());
        myLoadingPanel.stopLoading();
      });
    });
  }

  @NotNull
  private Document getDocument(@NotNull VirtualFile file) {
    assert !ApplicationManager.getApplication().isDispatchThread();

    if (ApkFileSystem.getInstance().isBinaryXml(file)) {
      String aaptOutput = getAaptOutput(aapt -> aapt.getXmlTree(myApk, ApkFileSystem.getInstance().getRelativePath(file)));
      return EditorFactory.getInstance().createDocument(aaptOutput);
    }

    if (ApkFileSystem.getInstance().isArsc(file)) {
      String aaptOutput = getAaptOutput(aapt -> aapt.dumpResources(myApk));
      return EditorFactory.getInstance().createDocument(aaptOutput);
    }

    return ApplicationManager.getApplication()
      .runReadAction((Computable<Document>)() -> FileDocumentManager.getInstance().getDocument(file));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myLoadingPanel;
  }

  @Override
  public void dispose() {
    if (myEditor != null) {
      EditorFactory.getInstance().releaseEditor(myEditor);
      myEditor = null;
    }

    myDisposed = true;
    Disposer.dispose(myDisposable);
  }

  private interface AaptCommandDelegate {
    ProcessOutput exec(@NotNull AaptInvoker aapt) throws ExecutionException;
  }

  private static String getAaptOutput(@NotNull AaptCommandDelegate delegate) {
    AaptInvoker aapt = AaptInvoker.getInstance();
    if (aapt == null) {
      return "Unable to locate aapt, please set the correct path to Android SDK and make sure build tools are installed.";
    }

    try {
      ProcessOutput aaptOutput = delegate.exec(aapt);

      if (aaptOutput.getExitCode() != AaptInvoker.SUCCESS) {
        return aaptOutput.getStderr();
      }
      else {
        return aaptOutput.getStdout();
      }
    }
    catch (ExecutionException e) {
      return e.toString();
    }
  }
}
