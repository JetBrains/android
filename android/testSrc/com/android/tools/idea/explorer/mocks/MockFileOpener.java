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
package com.android.tools.idea.explorer.mocks;

import com.android.tools.idea.explorer.FileOpener;
import com.android.tools.idea.explorer.FutureValuesTracker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class MockFileOpener implements FileOpener {

  public final FutureValuesTracker<Void> tracker = new FutureValuesTracker<>();

  @Override
  public boolean canOpenFile(VirtualFile virtualFile) {
    return true;
  }

  @Override
  public void openFile(Project project, VirtualFile virtualFile) {
    tracker.produce(null);
  }
}
