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
package com.android.tools.idea.wear.dwf.dom.xml

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.module.Module
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.CustomLogicResourceDomFileDescription

/**
 * File description for Watch Face Shapes XML files.
 *
 * @see <a
 *   href="https://developer.android.com/training/wearables/wff/setup#declare-shape-support">Declare
 *   support for watch face shapes</a>
 */
class WatchFaceShapesDescription :
  CustomLogicResourceDomFileDescription<WatchFaceShapesElement>(
    WatchFaceShapesElement::class.java,
    ResourceFolderType.XML,
    SdkConstants.TAG_WATCH_FACES,
  ) {

  override fun checkFile(file: XmlFile, module: Module?) =
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get() == true
}
