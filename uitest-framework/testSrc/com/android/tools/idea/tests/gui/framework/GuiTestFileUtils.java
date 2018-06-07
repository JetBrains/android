// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.tests.gui.framework;

import com.android.tools.idea.io.TestFileUtils;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public final class GuiTestFileUtils {
  private GuiTestFileUtils() {
  }

  /**
   * Creates the file if it doesn't exist, writes the string to it, refreshes the VFS, and reloads the document.
   */
  public static void writeAndReloadDocument(@NotNull Path path, @NotNull String string) throws IOException {
    VirtualFile file = TestFileUtils.writeFileAndRefreshVfs(path, string);
    EdtTestUtil.runInEdtAndWait(() -> FileDocumentManager.getInstance().reloadFiles(file));
  }
}
