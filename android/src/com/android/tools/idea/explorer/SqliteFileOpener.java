/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.explorer;

import com.android.tools.idea.editors.sqlite.SqliteFileType;
import com.android.tools.idea.sqliteExplorer.SqliteExplorerProjectService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Extension that opens a Sqlite file using [SqliteExplorerProjectService].
 */
public class SqliteFileOpener implements FileOpener {
  @Override
  public boolean canOpenFile(VirtualFile virtualFile) {
    return virtualFile.getFileType() == SqliteFileType.INSTANCE;
  }

  @Override
  public void openFile(Project project, VirtualFile virtualFile) {
    SqliteExplorerProjectService sqliteExplorerProjectService = SqliteExplorerProjectService.getInstance(project);
    sqliteExplorerProjectService.openSqliteDatabase(virtualFile);
  }
}