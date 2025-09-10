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
package com.android.tools.idea.material.icons.common

import com.android.tools.idea.material.icons.utils.MaterialIconsUtils
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.toDirFormat
import java.io.File
import java.net.URL
import java.net.URLEncoder
import kotlin.math.absoluteValue

private const val SYMBOLS_HOST = "https://fonts.gstatic.com/s/i/short-term/release/"

/**
 * Enum representing the different types of Material Symbols icon types
 *
 * @property remoteFileName file name of the font on the remote server
 * @property localName local name corresponding to the symbol type, used in file and directory names
 * @property displayName displayed name in Android Studio UI
 */
enum class Symbols(val remoteFileName: String, val localName: String, val displayName: String) {
  OUTLINED(
    "MaterialSymbolsOutlined[FILL,GRAD,opsz,wght]",
    "materialsymbolsoutlined",
    "Material Symbols Outlined",
  ),
  ROUNDED(
    "MaterialSymbolsRounded[FILL,GRAD,opsz,wght]",
    "materialsymbolsrounded",
    "Material Symbols Rounded",
  ),
  SHARP(
    "MaterialSymbolsSharp[FILL,GRAD,opsz,wght]",
    "materialsymbolssharp",
    "Material Symbols Sharp",
  );

  companion object {

    /** Returns a [Symbols] instance based on a given string in the [localName] format */
    fun getInstance(string: String): Symbols {
      return when (string.toDirFormat()) {
        "materialsymbolsoutlined" -> OUTLINED
        "materialsymbolsrounded" -> ROUNDED
        "materialsymbolssharp" -> SHARP
        else -> OUTLINED
      }
    }
  }
}

/**
 * Data class representing the properties of a Material Symbol that affect its rendering and display
 *
 * @property type The [Symbols] type (Outlined, Rounded, Sharp)
 * @property weight The font weight, affecting the stroke thickness in the render, affecting the
 *   icon's width
 * @property grade The font grade, affecting the stroke thickness in the render without affecting
 *   the icon's width
 * @property opticalSize The optical size of the icon
 * @property filled Whether the icon should be rendered "empty" or "filled" within the constraints
 *   of its outline
 */
data class SymbolConfiguration(
  val type: Symbols,
  val weight: Int,
  val grade: Int,
  val opticalSize: Int,
  val filled: Boolean,
) {

  companion object {
    val DEFAULT = SymbolConfiguration(Symbols.OUTLINED, 400, 0, 24, false)
  }

  /**
   * @return The common part between the remote URL and File representations of the
   *   [MaterialSymbols], describing its [SymbolConfiguration]
   */
  private fun getCoreString(): String {
    return buildString {
      if (weight != DEFAULT.weight) {
        append("wght${weight}")
      }

      if (grade != DEFAULT.grade) {
        append("grad")
        if (grade < 0) {
          append("N")
        }
        append("${grade.absoluteValue}")
      }

      if (filled) {
        append("fill1")
      }
    }
  }

  /** @return the local file name for a given Material Symbol */
  fun toFileName(symbolName: String): String {
    return buildString {
      append("${symbolName}_")
      getCoreString().let { if (it.isNotEmpty()) append("${it}_") }
      append("${opticalSize}px.xml")
    }
  }

  /** @return the file name on the remote server for a given Material Symbol */
  fun toUrlString(symbolName: String): String {
    return buildString {
      append(SYMBOLS_HOST)
      append("${type.localName}/")
      append("${symbolName}/")
      append("${getCoreString().takeUnless { it.isEmpty() } ?: "default"}/")
      append("${opticalSize}px.xml")
    }
  }
}

/** Provides utility functions for obtaining URLs and file paths for Material Symbols fonts. */
class MaterialSymbolsFontUrlProvider {

  companion object {
    private const val HOST =
      "https://raw.githubusercontent.com/google/material-design-icons/master/"
    private const val EXTENSION = ".ttf"

    private const val FOLDER = "variablefont/"

    /**
     * Generates the [URL] used to download a font file for Material Symbols from the remote server
     *
     * @param type [Symbols] enum instance indicating which style of Material Symbols to download
     *   the font for
     * @return the [URL] used for download
     */
    fun getRemoteFontUrl(type: Symbols): URL {
      val fileName = type.remoteFileName
      val encodedString = URLEncoder.encode(fileName, "UTF-8")
      val url = URL(HOST + FOLDER + encodedString + EXTENSION)

      return url
    }

    /**
     * Gets the local directory where the font files used for Material Symbols rendering are located
     *
     * @param type The [Symbols] type
     * @return The [File] object of the directory
     */
    fun getLocalFontDirectoryFile(type: Symbols): File? {
      val directoryName = type.localName
      val fontDirectoryPath =
        MaterialIconsUtils.getIconsSdkTargetPath()?.resolve(FOLDER + directoryName) ?: return null
      return fontDirectoryPath
    }

    /**
     * Gets the local [File] where a font file used for Material Symbols rendering is located
     *
     * @param type The [Symbols] type
     * @return The [File] object of the font file
     */
    fun getLocalFontFile(type: Symbols): File? {
      val fileName = type.localName + EXTENSION
      val fontFilePath = getLocalFontDirectoryFile(type)?.resolve(fileName) ?: return null
      return fontFilePath
    }

    /**
     * Checks if the font file for a particular Material Symbol style exists already in the Sdk
     *
     * @param type The [Symbols] type
     * @return If the font file exists in the Sdk
     */
    fun hasFontPathInSdk(type: Symbols): Boolean {
      val fontFilePath = getLocalFontFile(type)
      return fontFilePath?.exists() ?: false
    }
  }
}
