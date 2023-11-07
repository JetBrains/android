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
package org.jetbrains.android.dom.xml

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.MultipleKnownRootsResourceDomFileDescription
import org.jetbrains.android.dom.MultipleKnownRootsResourceDomFileDescription.isMyFile

/**
 * Describes all files in [ResourceFolderType.XML], except for [MotionScene] and for
 * [PreferenceElement].
 *
 * @see MotionDomFileDescription
 * @see PreferenceClassDomFileDescription
 */
object XmlResourceDomFileDescription :
  MultipleKnownRootsResourceDomFileDescription<XmlResourceElement?>() {
  @JvmStatic
  fun isXmlResourceFile(file: XmlFile): Boolean {
    return ReadAction.compute(
      ThrowableComputable<Boolean, RuntimeException> {
        XmlResourceDomFileDescription().isMyFile(file, null)
      }
    )
  }
}
