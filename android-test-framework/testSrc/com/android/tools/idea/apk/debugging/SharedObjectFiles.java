/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.apk.debugging;

import com.android.sdklib.devices.Abi;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SharedObjectFiles {
  private SharedObjectFiles() {
  }

  @NotNull
  public static List<VirtualFile> createSharedObjectFiles(@NotNull VirtualFile parentFolder,
                                                          @NotNull String sharedObjectFileName,
                                                          @NotNull Abi... abis) throws IOException {
    return ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<List<VirtualFile>, IOException>() {
      @Override
      public List<VirtualFile> compute() throws IOException {
        List<VirtualFile> files = new ArrayList<>();
        for (Abi abi : abis) {
          VirtualFile abiFolder = getOrCreateAbiFolder(this, parentFolder, abi);
          files.add(abiFolder.createChildData(this, sharedObjectFileName));
        }
        return files;
      }
    });
  }


  @NotNull
  public static VirtualFile createSharedObjectFile(@NotNull VirtualFile parentFolder,
                                                   @NotNull String sharedObjectFileName,
                                                   @NotNull Abi abi) throws IOException {
    return ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        VirtualFile abiFolder = getOrCreateAbiFolder(this, parentFolder, abi);
        return abiFolder.createChildData(this, sharedObjectFileName);
      }
    });
  }

  private static VirtualFile getOrCreateAbiFolder(@NotNull Object requestor, @NotNull VirtualFile parentFolder, @NotNull Abi abi)
    throws IOException {
    String abiFolderName = abi.toString();
    VirtualFile abiFolder = parentFolder.findChild(abiFolderName);
    if (abiFolder == null) {
      abiFolder = parentFolder.createChildDirectory(requestor, abiFolderName);
    }
    return abiFolder;
  }
}
