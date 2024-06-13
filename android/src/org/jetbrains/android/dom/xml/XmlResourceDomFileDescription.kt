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

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.MultipleKnownRootsResourceDomFileDescription
import org.jetbrains.annotations.NonNls

/**
 * Describes all files in [ResourceFolderType.XML], except for
 * [org.jetbrains.android.dom.motion.MotionScene] and for [PreferenceElement].
 *
 * @see org.jetbrains.android.dom.motion.MotionDomFileDescription
 * @see PreferenceClassDomFileDescription
 */
class XmlResourceDomFileDescription :
  MultipleKnownRootsResourceDomFileDescription<XmlResourceElement>(
    XmlResourceElement::class.java,
    ResourceFolderType.XML,
    Util.SUPPORTED_TAGS,
  ) {

  companion object {
    @NonNls const val SEARCHABLE_TAG_NAME = "searchable"

    @NonNls const val KEYBOARD_TAG_NAME = "Keyboard"

    @NonNls const val DEVICE_ADMIN_TAG_NAME = "device-admin"

    @NonNls const val ACCOUNT_AUTHENTICATOR_TAG_NAME = "account-authenticator"

    @NonNls const val PREFERENCE_HEADERS_TAG_NAME = "preference-headers"
  }

  object Util {
    @JvmStatic
    fun isXmlResourceFile(file: XmlFile) = runReadAction {
      XmlResourceDomFileDescription().isMyFile(file, null)
    }

    @JvmField
    val SUPPORTED_TAGS =
      setOf(
        SdkConstants.TAG_APPWIDGET_PROVIDER,
        SEARCHABLE_TAG_NAME,
        KEYBOARD_TAG_NAME,
        DEVICE_ADMIN_TAG_NAME,
        ACCOUNT_AUTHENTICATOR_TAG_NAME,
        PREFERENCE_HEADERS_TAG_NAME,
      )
  }
}
