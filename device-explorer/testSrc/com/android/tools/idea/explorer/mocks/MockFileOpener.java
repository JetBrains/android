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

import com.android.tools.idea.explorer.DeviceExplorerController;
import com.android.tools.idea.FutureValuesTracker;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;

public class MockFileOpener implements DeviceExplorerController.FileOpener {

  @NotNull private final FutureValuesTracker<Path> myOpenFileTracker = new FutureValuesTracker<>();

  @Override
  public void openFile(@NotNull Path localPath) {
    myOpenFileTracker.produce(localPath);
  }

  @Override
  public void openFile(@NotNull VirtualFile virtualFile) {
    myOpenFileTracker.produce(Paths.get(virtualFile.getPath()));
  }

  @NotNull
  public FutureValuesTracker<Path> getOpenFileTracker() {
    return myOpenFileTracker;
  }
}
