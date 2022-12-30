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
package com.android.tools.idea.res;

import com.google.common.hash.Hashing;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Computes a combined 64-bit hash of time stamp and size of a file.
 */
public final class FileTimeStampLengthHasher {
  private static final byte[] NULL_HASH = new byte[8];

  /**
   * Computes a combined 64-bit hash of time stamp and size of a {@link VirtualFile}.
   * Returns an array of 8 zero bytes if the virtual file is null or is not valid.
   */
  @NotNull
  public static byte[] hash(@Nullable VirtualFile virtualFile) {
    if (virtualFile == null || !virtualFile.isValid()) {
      return NULL_HASH;
    }
    return Hashing.sipHash24().newHasher().putLong(virtualFile.getTimeStamp()).putLong(virtualFile.getLength()).hash().asBytes();
  }
}
