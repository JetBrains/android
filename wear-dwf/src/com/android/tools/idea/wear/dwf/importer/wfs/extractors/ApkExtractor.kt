/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.importer.wfs.extractors

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.RES_FOLDER
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.apk.analyzer.Archives
import com.android.tools.apk.analyzer.BinaryXmlParser
import com.android.tools.apk.analyzer.ResourceIdResolver
import com.android.tools.apk.analyzer.internal.ApkArchive
import com.android.tools.idea.res.DEFAULT_STRING_RESOURCE_FILE_NAME
import com.android.tools.res.ids.apk.ApkResourceIdManager
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceConfiguration
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val RESOURCES_ARSC_PATH = "resources.arsc"

/**
 * Class that extracts watch face files from a `.apk` archive produced by
 * [Watch Face Studio](https://developer.samsung.com/watch-face-studio/overview.html).
 */
class ApkExtractor(private val ioDispatcher: CoroutineDispatcher) : WatchFaceStudioFileExtractor {

  override suspend fun extract(apkFilePath: Path) =
    flow {
        val apkResourceIdManager =
          ApkResourceIdManager().apply { loadApkResources(apkFilePath.pathString) }

        val resourceIdResolver = ResourceIdResolver { id ->
          apkResourceIdManager.findById(id)?.resourceUrl?.toString()
        }

        Archives.open(apkFilePath).use { archiveContext ->
          val archive =
            checkNotNull(archiveContext.archive as? ApkArchive) {
              "The $apkFilePath file is expected to be an APK archive"
            }

          emit(
            ExtractedItem.Manifest(
              readXmlContent(
                archive,
                archive.contentRoot.resolve(FN_ANDROID_MANIFEST_XML),
                resourceIdResolver,
              )
            )
          )

          val resourceTable =
            archive.contentRoot
              .resolve(RESOURCES_ARSC_PATH)
              .inputStream()
              .use { BinaryResourceFile.fromInputStream(it) }
              .chunks
              .filterIsInstance<ResourceTableChunk>()
              .single()

          for (pkg in resourceTable.packages) {
            for (typeSpec in pkg.typeSpecChunks) {
              val resourceType =
                ResourceType.fromXmlTagName(typeSpec.typeName)
                  ?: error("Expected resource type to be valid")
              for (typeChunk in pkg.getTypeChunks(typeSpec.id)) {
                for (entry in typeChunk.entries) {
                  val value = entry.value.value() ?: error("Expected entry value to be non-null")
                  val formattedValue =
                    BinaryXmlParser.formatValue(value, resourceTable.stringPool, resourceIdResolver)

                  val name = entry.value.key()
                  val resource =
                    when (resourceType) {
                      ResourceType.DRAWABLE ->
                        ExtractedItem.BinaryResource(
                          name = name,
                          filePath = Path.of(formattedValue),
                          binaryContent = archive.contentRoot.resolve(formattedValue).readBytes(),
                        )
                      ResourceType.RAW,
                      ResourceType.XML ->
                        ExtractedItem.TextResource(
                          name = name,
                          filePath = Path.of(formattedValue),
                          text =
                            readXmlContent(
                              archive,
                              archive.contentRoot.resolve(formattedValue),
                              resourceIdResolver,
                            ),
                        )
                      ResourceType.STRING ->
                        ExtractedItem.StringResource(
                          name = name,
                          filePath = stringResourcePath(typeChunk.configuration),
                          value = resourceTable.stringPool.getString(value.data()),
                        )
                      else -> error("Unsupported type $resourceType")
                    }
                  emit(resource)
                }
              }
            }
          }
        }
      }
      .flowOn(ioDispatcher)

  private fun readXmlContent(
    archive: ApkArchive,
    sourcePath: Path,
    resourceIdResolver: ResourceIdResolver,
  ): String {
    val bytes = sourcePath.readBytes()
    val decodedBytes =
      if (archive.isBinaryXml(sourcePath, bytes)) {
        BinaryXmlParser.decodeXml(bytes, resourceIdResolver)
      } else {
        bytes
      }
    return decodedBytes.toString(Charsets.UTF_8)
  }

  private fun stringResourcePath(configuration: BinaryResourceConfiguration): Path {
    val folderConfiguration =
      FolderConfiguration.getConfigForQualifierString(
        if (configuration.isDefault) "" else configuration.toString()
      ) ?: error("Unexpected invalid resource configuration $configuration")

    return Path.of(
      RES_FOLDER,
      folderConfiguration.getFolderName(ResourceFolderType.VALUES),
      DEFAULT_STRING_RESOURCE_FILE_NAME,
    )
  }
}
