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
package org.jetbrains.android.dom

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.intellij.psi.xml.XmlFile

/**
 * Checks if the given [file] is a Declarative Watch Face file. A DWF file is located in the
 * `res/raw` folder and has a `WatchFace` root tag.
 */
fun isDeclarativeWatchFaceFile(file: XmlFile) =
  FileDescriptionUtils.isResourceOfTypeWithRootTag(
    file,
    ResourceFolderType.RAW,
    setOf(SdkConstants.TAG_WATCH_FACE),
  )
