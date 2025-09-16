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
package com.android.tools.idea.npw.assetstudio.assets

import com.android.tools.idea.material.icons.common.MaterialSymbolsFontUrlProvider
import com.android.tools.idea.material.icons.common.SymbolConfiguration
import com.android.tools.idea.material.icons.metadata.MaterialMetadataIcon
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.JBColor
import java.awt.Color

private val FAKE_LAYOUT_RES_DIR = LightVirtualFile("layout")

private const val BASE_XML =
  """
  <?xml version="1.0" encoding="utf-8"?>
  <TextView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="100px"
    android:layout_height="100px"
    android:fontFamily="%s"
    android:fontVariationSettings="
        'FILL' %s,
        'wght' %s,
        'GRAD' %s,
        'opsz' %s"
    android:text="\u%s"
    android:textSize="100px"
    android:background="%s"
    android:textColor="%s"
    />
  """

/**
 * Method to convert the [Color] into the needed RGB value for the color properties in the XML.
 *
 * [Integer.toHexString] not used due to:
 * + toHexString() also output's the string's alpha value, which we don't need and would have to be
 *   formatted away
 * + toHexString() is not padded with 0s, to make a full color hex string
 *
 * @return String in the expected RGB format
 */
private fun Color.toHex(): String {
  return "#%06X".format(this.rgb and 0xFFFFFF)
}

private fun getXMLString(
  symbolConfiguration: SymbolConfiguration,
  unicode: Int,
  backgroundColor: Color,
): String {
  val fillStr = symbolConfiguration.filled.compareTo(false).toString()
  val weightStr = symbolConfiguration.weight.coerceIn(100, 700).toString()
  val gradeStr = symbolConfiguration.grade.coerceIn(-25, 200).toString()
  val opticalSizeStr = symbolConfiguration.opticalSize.coerceIn(20, 48).toString()
  val hexCodeStr = unicode.toString(16)
  return BASE_XML.format(
    MaterialSymbolsFontUrlProvider.getLocalFontFile(symbolConfiguration.type)!!.path,
    fillStr,
    weightStr,
    gradeStr,
    opticalSizeStr,
    hexCodeStr,
    backgroundColor.toHex(),
    JBColor.foreground().toHex(),
  )
}

private fun getDisplayName(name: String): String {
  return name.trim().split('_').joinToString(" ") { it.replaceFirstChar { it.uppercase() } }
}

private fun getFileName(
  symbolConfiguration: SymbolConfiguration,
  metadata: MaterialMetadataIcon,
): String {
  val shortenedType = symbolConfiguration.type.localName.removePrefix("materialsymbols")
  return "${shortenedType}_${symbolConfiguration.toFileName(metadata.name)}"
}

/**
 * A [LightVirtualFile] defined to allow quickly identifying the given file as an XML that is used
 * as adapter to be able to preview custom entities, in this case Material Symbols. The contents of
 * the file only reside in memory and contain some XML that will be passed to Layoutlib, alongside
 * of some visual properties required to render the Symbol.
 *
 * Adapted from [InMemoryLayoutVirtualFile], not extended to avoid adding a dependency to the
 * preview designer
 */
class MaterialSymbolsVirtualFile(
  val symbolConfiguration: SymbolConfiguration,
  val metadata: MaterialMetadataIcon,
  backgroundColor: Color,
) :
  LightVirtualFile(
    getFileName(symbolConfiguration, metadata),
    getXMLString(symbolConfiguration, metadata.unicode, backgroundColor),
  ),
  BackedVirtualFile,
  Comparable<MaterialSymbolsVirtualFile> {

  val displayName = getDisplayName(metadata.name)
  private val originFile = LightVirtualFile(name, XmlFileType.INSTANCE, content)

  override fun getParent() = FAKE_LAYOUT_RES_DIR

  override fun getOriginFile(): VirtualFile = originFile

  override fun compareTo(other: MaterialSymbolsVirtualFile): Int {
    return this.displayName.compareTo(other.displayName)
  }
}
