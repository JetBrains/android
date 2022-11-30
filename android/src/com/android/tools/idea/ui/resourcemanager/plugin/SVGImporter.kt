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
package com.android.tools.idea.ui.resourcemanager.plugin

import com.android.ide.common.vectordrawable.Svg2Vector
import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.importer.QualifierMatcher
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.LightVirtualFile
import java.io.ByteArrayOutputStream
import java.io.File

private val supportedFileTypes = setOf("svg")

/**
 * Importer for SVGs.
 *
 * This importer uses the [Svg2Vector] library to convert SVG files to VectorDrawable.
 */
class SVGImporter : ResourceImporter {
  override val presentableName = "SVG Importer"

  override val userCanEditQualifiers get() = true

  override fun getSupportedFileTypes() = supportedFileTypes

  override fun getSourcePreview(asset: DesignAsset): DesignAssetRenderer? =
    DesignAssetRendererManager.getInstance().getViewer(SVGAssetRenderer::class.java)

  override fun processFile(file: File): DesignAsset? {
    val qualifierMatcherResult = QualifierMatcher().parsePath(file.path)
    return convertSVGToVectorDrawable(file)?.let {
      DesignAsset(it, qualifierMatcherResult.qualifiers.toList(), ResourceType.DRAWABLE, qualifierMatcherResult.resourceName)
    }
  }

  private fun convertSVGToVectorDrawable(it: File): LightVirtualFile? {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val errors = try {
      Svg2Vector.parseSvgToXml(it.toPath(), byteArrayOutputStream)
    } catch (e: Exception) {
      Logger.getInstance(SVGImporter::class.java).warn("Error converting ${it.absolutePath} to vector drawable - ${e.localizedMessage}")
      return null
    }
    if (errors.isNotBlank()) {
      Logger.getInstance(SVGImporter::class.java).warn("Error converting ${it.absolutePath} to vector drawable:\n$errors")
    }
    if (byteArrayOutputStream.size() == 0) {
      return null
    }
    return LightVirtualFile("${it.nameWithoutExtension}.xml", String(byteArrayOutputStream.toByteArray()))
  }
}
