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
package com.android.tools.idea.apk.dex;

import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public final class DexFiles {
  private DexFiles() {
  }

  @NotNull
  public static DexBackedDexFile getDexFile(@NotNull VirtualFile file) throws IOException {
    return com.android.tools.apk.analyzer.dex.DexFiles.getDexFile(file.contentsToByteArray());
  }

}
