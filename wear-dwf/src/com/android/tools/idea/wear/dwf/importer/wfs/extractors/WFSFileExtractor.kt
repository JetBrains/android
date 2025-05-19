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

import com.android.SdkConstants.FD_RES_RAW
import com.android.ide.common.xml.XmlPrettyPrinter
import com.android.tools.idea.wear.dwf.importer.wfs.WFSImportException.InvalidHoneyFaceFileException
import com.android.tools.idea.wear.dwf.importer.wfs.honeyface.HoneyFaceParser
import com.android.tools.idea.wear.dwf.importer.wfs.honeyface.HoneyFaceXmlConverter
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.Decompressor
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private val EXCLUDED_FILES =
  setOf("res/drawable-nodpi/preview_circular.png", "res/values/strings.xml")

private const val WATCH_FACE_FILENAME = "watchface.xml"
private const val HONEY_FACE_FILENAME = "honeyface.json"
private const val WFS_PREVIEW_FILENAME = "latest_preview.png"
private const val STUDIO_PREVIEW_FILENAME = "preview.png"

/**
 * A class that extracts watch face files from a `.wfs` file. This format is used by
 * [Watch Face Studio](https://developer.samsung.com/watch-face-studio/overview.html) before the
 * watch face is built/published.
 */
internal class WFSFileExtractor(
  private val ioDispatcher: CoroutineDispatcher,
  private val parser: HoneyFaceParser = HoneyFaceParser(),
  private val xmlConverter: HoneyFaceXmlConverter = HoneyFaceXmlConverter(),
) {

  suspend fun extract(wfsFile: VirtualFile, mainFolderPath: Path, resFolderPath: Path) {
    withContext(ioDispatcher) {
      extractWFSFiles(wfsFile, mainFolderPath, resFolderPath)
      generateRawWatchFaceFile(mainFolderPath, resFolderPath)
    }
  }

  private fun extractWFSFiles(wfsFile: VirtualFile, mainFolderPath: Path, resFolderPath: Path) {
    Decompressor.Zip(wfsFile.toNioPath())
      .entryFilter { it -> it.name !in EXCLUDED_FILES }
      .extract(mainFolderPath)

    val wfsPreviewFile = mainFolderPath.resolve(WFS_PREVIEW_FILENAME).toFile()
    if (wfsPreviewFile.exists()) {
      val previewFileDestination = resFolderPath.resolve(STUDIO_PREVIEW_FILENAME).toFile()

      FileUtil.copy(previewFileDestination, previewFileDestination)
      FileUtil.delete(wfsPreviewFile)
    }
  }

  private fun generateRawWatchFaceFile(mainFolderPath: Path, resFolderPath: Path) {
    val honeyFaceFile = File(mainFolderPath.toFile(), HONEY_FACE_FILENAME)
    val honeyFace =
      parser.parse(honeyFaceFile)
        ?: throw InvalidHoneyFaceFileException("Failed to parse the HoneyFace file.")
    FileUtil.delete(honeyFaceFile)

    val rawWatchFaceXmlDocument = xmlConverter.toXml(honeyFace)
    val rawWatchFaceFile = resFolderPath.resolve(FD_RES_RAW).resolve(WATCH_FACE_FILENAME).toFile()

    FileUtil.createIfDoesntExist(rawWatchFaceFile)
    FileUtil.writeToFile(
      rawWatchFaceFile,
      XmlPrettyPrinter.prettyPrint(rawWatchFaceXmlDocument, false),
    )
  }
}
