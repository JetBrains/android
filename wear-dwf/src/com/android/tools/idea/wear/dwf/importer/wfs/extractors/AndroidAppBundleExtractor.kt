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
import com.android.aapt.Resources
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.apk.analyzer.Archives
import com.android.tools.apk.analyzer.internal.AppBundleArchive
import com.android.tools.idea.apk.viewer.ProtoXmlPrettyPrinterImpl
import com.android.tools.idea.res.DEFAULT_STRING_RESOURCE_FILE_NAME
import com.android.tools.idea.util.buffered
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.readBytes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val BASE_PATH = "base"
private const val AAB_RESOURCES_PB_PATH = "$BASE_PATH/resources.pb"
private const val AAB_MANIFEST_PATH = "$BASE_PATH/manifest/${FN_ANDROID_MANIFEST_XML}"

/**
 * Class that extracts watch face files from a `.aab` (Android App Bundle) archive.
 *
 * This format is used when publishing / deploying a watch face from
 * [Watch Face Studio](https://developer.samsung.com/watch-face-studio/overview.html).
 *
 * Inside the `.aab` file, there is a `base/` folder that contains the watch face data.
 */
class AndroidAppBundleExtractor(private val ioDispatcher: CoroutineDispatcher) :
  WatchFaceStudioFileExtractor {

  override suspend fun extract(aabFilePath: Path) =
    flow {
        Archives.open(aabFilePath).use { archiveContext ->
          val archive =
            checkNotNull(archiveContext.archive as? AppBundleArchive) {
              "The $aabFilePath file is expected to be an Android App Bundle archive"
            }

          emit(
            ExtractedItem.Manifest(
              readXmlContent(archive, archive.contentRoot.resolve(AAB_MANIFEST_PATH))
            )
          )

          val resourceTable =
            archive.contentRoot.resolve(AAB_RESOURCES_PB_PATH).inputStream().buffered().use {
              Resources.ResourceTable.parseFrom(it)
            }

          resourceTable.packageList.forEach { pkg ->
            pkg.typeList.forEach { type ->
              val resourceType = ResourceType.fromClassName(type.name) ?: return@forEach

              type.entryList.forEach { entry ->
                val resourceName = entry.name

                entry.configValueList.forEach { configValue ->
                  val filePath = Path.of(configValue.value.item.file.path ?: "")
                  val sourcePath = archive.contentRoot.resolve("$BASE_PATH/$filePath")

                  val resource =
                    when (resourceType) {
                      ResourceType.STRING ->
                        ExtractedItem.StringResource(
                          name = resourceName,
                          filePath = stringResourcePath(configValue),
                          value = configValue.value.item.str.value,
                        )
                      ResourceType.RAW,
                      ResourceType.XML ->
                        ExtractedItem.TextResource(
                          name = resourceName,
                          filePath = filePath,
                          text = readXmlContent(archive, sourcePath),
                        )
                      ResourceType.DRAWABLE ->
                        ExtractedItem.BinaryResource(
                          name = resourceName,
                          filePath = filePath,
                          binaryContent = sourcePath.readBytes(),
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

  private fun readXmlContent(archive: AppBundleArchive, sourcePath: Path): String {
    val bytes = sourcePath.readBytes()
    return if (archive.isProtoXml(sourcePath, bytes)) {
      ProtoXmlPrettyPrinterImpl().prettyPrint(bytes)
    } else {
      bytes.toString(Charsets.UTF_8)
    }
  }

  private fun stringResourcePath(configValue: Resources.ConfigValue): Path {
    val folderConfiguration =
      FolderConfiguration().apply {
        localeQualifier = LocaleQualifier.getQualifier(configValue.config.locale)
      }
    return Path.of(
      RES_FOLDER,
      folderConfiguration.getFolderName(ResourceFolderType.VALUES),
      DEFAULT_STRING_RESOURCE_FILE_NAME,
    )
  }
}
