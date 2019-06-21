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
package com.android.tools.idea.resources.base

import com.intellij.util.containers.ObjectIntHashMap
import java.io.IOException

/**
 * Represents an XML file from which an Android resource was created.
 *
 * [relativePath] path of the file relative to the resource directory, or null if the source file of the resource is not available
 * [configuration] configuration the resource file is associated with
 */
data class ResourceSourceFile(val relativePath: String?, val configuration: RepositoryConfiguration) {
  /**
   * Serializes the ResourceSourceFile to the given stream.
   */
  @Throws(IOException::class)
  fun serialize(stream: Base128OutputStream, configIndexes: ObjectIntHashMap<String>) {
    stream.writeString(relativePath)
    stream.writeInt(configIndexes[configuration.folderConfiguration.qualifierString])
  }

  val repository : LoadableResourceRepository
    get() = configuration.repository

  companion object {
    /**
     * Creates a ResourceSourceFile by reading its contents of the given stream.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun deserialize(stream: Base128InputStream, configurations: List<RepositoryConfiguration>): ResourceSourceFile {
      val relativePath = stream.readString()
      val configIndex = stream.readInt()
      return ResourceSourceFile(relativePath, configurations[configIndex])
    }
  }
}
