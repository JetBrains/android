/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.studiobot.mimetype

import com.android.tools.idea.studiobot.MimeType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile

/**
 * Augments MimeTypes with additional attributes based on the file or source string it describes.
 *
 * Under the hood MimeTypes are represented as strings, and the strings consist of the base type
 * name, followed by attribute names and values delimited by ";". Attributes can be extracted using
 * [MimeType.getAttribute].
 *
 * Some MimeTypes may have particular attributes that indicate something more specific than just the
 * type. These attributes can be identified based on information like the name of a file and its
 * location in the project. But often these are context or platform-specific. For example, in
 * Android the XML mimetype may have a role attribute identified based on whether it is a resource
 * file or not. We let implementations of this class identify these attributes.
 */
interface MimeTypeAugmenter {

  /**
   * Augments [type] with attributes based on [virtualFile] and [source]. If no attributes are
   * identified, returns [type].
   */
  fun augment(type: MimeType, virtualFile: VirtualFile?, source: CharSequence?): MimeType

  companion object {
    val EP_NAME: ExtensionPointName<MimeTypeAugmenter> =
      ExtensionPointName.create("com.android.tools.idea.ml.mimetypeAugmenter")

    fun augment(base: MimeType, virtualFile: VirtualFile?, source: CharSequence?): MimeType {
      val refiners = EP_NAME.extensionList
      // Pass the base type through all the available refiners, refining on both the virtual file
      // and source string when applicable
      return refiners.fold(base) { mimeType, refiner ->
        refiner.augment(mimeType, virtualFile, source)
      }
    }
  }
}
