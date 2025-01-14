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
package com.android.tools.idea.wear.dwf.dom.raw

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.wear.wff.WFFVersionExtractor
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.xml.XmlSchemaProvider
import com.intellij.xml.util.XmlUtil
import org.jetbrains.android.dom.isDeclarativeWatchFaceFile
import org.jetbrains.annotations.NonNls

class RawWatchfaceXmlSchemaProvider(
  private val wffVersionExtractor: WFFVersionExtractor = WFFVersionExtractor()
) : XmlSchemaProvider() {

  override fun getSchema(url: @NonNls String, module: Module?, baseFile: PsiFile): XmlFile? {
    val manifestDocument =
      module?.let { MergedManifestManager.getMergedManifestSupplier(module).now?.document } ?: return null
    val schemaVersion = wffVersionExtractor.extractFromManifest(manifestDocument) ?: return null
    return XmlUtil.findXmlFile(
      baseFile,
      VfsUtilCore.urlToPath(
        VfsUtilCore.toIdeaUrl(FileUtil.unquote(schemaVersion.schemaUrl.toExternalForm()), false)
      ),
    )
  }

  override fun isAvailable(file: XmlFile) =
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get() &&
      isDeclarativeWatchFaceFile(file)
}
