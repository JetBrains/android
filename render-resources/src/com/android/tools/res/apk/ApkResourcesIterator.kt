/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:JvmName("ApkResourcesIterator")
package com.android.tools.res.apk

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceType
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceIdentifier
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import java.util.zip.ZipFile

internal fun forEveryResource(apkPath: String, processor: ResourceEntryProcessor) {
  ZipFile(apkPath).use { zipFile ->
    val zipEntry = zipFile.getEntry("resources.arsc") ?: return@use
    val resourceFile = BinaryResourceFile.fromInputStream(zipFile.getInputStream(zipEntry))
    (resourceFile.chunks.firstOrNull() as? ResourceTableChunk)?.let { resourceTable ->
      val stringPool = resourceTable.stringPool
      for (pkg in resourceTable.packages) {
        for (typeSpec in pkg.typeSpecChunks) {
          val resType = ResourceType.fromXmlTagName(typeSpec.typeName)!!
          for (typeChunk in pkg.getTypeChunks(typeSpec.id)) {
            val binResConfig = typeChunk.configuration
            val qualifierString =
              binResConfig.toString().let { if (it == "default") "" else it }
            val folderConfig =
              FolderConfiguration.getConfigForQualifierString(qualifierString)!!

            typeChunk.entries.forEach { (rowId, typeChunkEntry) ->
              val binaryId =
                BinaryResourceIdentifier.create(pkg.id, typeSpec.id, rowId)
              processor.onResourceEntry(
                stringPool,
                resType,
                folderConfig,
                binaryId.resourceId(),
                typeChunkEntry
              )
            }
          }
        }
      }
    }
  }
}