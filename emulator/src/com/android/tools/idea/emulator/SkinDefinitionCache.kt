/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.annotations.concurrency.Slow
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.utils.ThreadSafe
import java.nio.file.Path

/**
 * Cache of AVD skin definitions. The cache stores weak [SkinDefinition] references, so clients are expected
 * to maintain their own strong [SkinDefinition] references.
 */
@ThreadSafe
internal class SkinDefinitionCache {
  /** Skin definitions keyed by skin definition folders. */
  private val folderToSkin: MutableMap<Path, SkinDefinition?> = ContainerUtil.createConcurrentWeakValueMap()

  @Slow
  fun getSkinDefinition(skinFolder: Path?): SkinDefinition? {
    if (skinFolder == null) {
      return null
    }
    return folderToSkin.computeIfAbsent(skinFolder) { SkinDefinition.create(skinFolder) }
  }

  companion object {
    @JvmStatic
    fun getInstance(): SkinDefinitionCache {
      return INSTANCE
    }

    @JvmStatic
    private val INSTANCE = SkinDefinitionCache()
  }
}