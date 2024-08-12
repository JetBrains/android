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
package com.google.idea.blaze.python.run;

import com.google.idea.blaze.base.io.AbsolutePathPatcher.AbsolutePathPatcherUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.python.debugger.PyDebugSupportUtils;
import com.jetbrains.python.debugger.PyLocalPositionConverter;
import com.jetbrains.python.debugger.PyPositionConverter;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySourcePosition;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * Converts the blaze-out symlink file paths to the actual source file paths. Otherwise a copy of
 * {@link PyLocalPositionConverter}
 */
public class BlazePyPositionConverter implements PyPositionConverter {

  private static final Logger logger = Logger.getInstance(BlazePyPositionConverter.class);

  @Override
  public PySourcePosition create(String filePath, int line) {
    return new PySourcePosition(convertFilePath(filePath), line) {};
  }

  @Override
  public PySourcePosition convertPythonToFrame(String filePath, int line) {
    return new PySourcePosition(filePath, line) {};
  }

  @Override
  public PySourcePosition convertFrameToPython(PySourcePosition position) {
    return position;
  }

  @Override
  public PySourcePosition convertToPython(XSourcePosition position) {
    return new PySourcePosition(
        convertFilePath(position.getFile().getPath()),
        convertLocalLineToRemote(position.getFile(), position.getLine())) {};
  }

  @Override
  @Nullable
  public XSourcePosition convertFromPython(PySourcePosition position) {
    return createXSourcePosition(getVirtualFile(position.getFile()), position.getLine());
  }

  @Override
  @Nullable
  public XSourcePosition convertFromPython(PySourcePosition position, String frameName) {
    return createXSourcePosition(getVirtualFile(position.getFile()), position.getLine());
  }

  @Override
  public PySignature convertSignature(PySignature signature) {
    return signature;
  }

  private static String convertFilePath(String filePath) {
    File file = new File(filePath);
    try {
      filePath = file.getCanonicalPath();
    } catch (IOException e) {
      logger.warn(e);
    }
    return AbsolutePathPatcherUtil.fixPath(filePath);
  }

  private static VirtualFile getVirtualFile(String filePath) {
    return LocalFileSystem.getInstance().findFileByPath(filePath);
  }

  @Nullable
  private static XSourcePosition createXSourcePosition(@Nullable VirtualFile vFile, int line) {
    if (vFile != null) {
      return XDebuggerUtil.getInstance()
          .createPosition(vFile, convertRemoteLineToLocal(vFile, line));
    } else {
      return null;
    }
  }

  /** Convert from 1- to 0-indexed line numbering, and account for continuation lines */
  private static int convertLocalLineToRemote(VirtualFile file, int line) {
    return ApplicationManager.getApplication()
        .runReadAction(
            (Computable<Integer>)
                () -> {
                  int lineNumber = line;
                  final Document document = FileDocumentManager.getInstance().getDocument(file);
                  if (document != null) {
                    while (PyDebugSupportUtils.isContinuationLine(document, lineNumber)) {
                      lineNumber++;
                    }
                  }
                  return lineNumber + 1;
                });
  }

  /** Convert from 0- to 1-indexed line numbering, and account for continuation lines */
  private static int convertRemoteLineToLocal(final VirtualFile vFile, int line) {
    final Document document =
        ApplicationManager.getApplication()
            .runReadAction(
                new Computable<Document>() {
                  @Override
                  public Document compute() {
                    return FileDocumentManager.getInstance().getDocument(vFile);
                  }
                });

    line--;
    if (document != null) {
      while (PyDebugSupportUtils.isContinuationLine(document, line - 1)) {
        line--;
      }
    }
    return line;
  }
}
