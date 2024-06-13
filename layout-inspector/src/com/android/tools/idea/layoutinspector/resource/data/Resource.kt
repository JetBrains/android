/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource.data

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.layoutinspector.common.StringTable

/**
 * Represents the components of a resource ID.
 *
 * For example, with "@android:id/textView", "android" is the namespace, "id" is the type, and
 * "textView" is the name.
 */
class Resource(
  val type: Int = 0, // Key for a StringTable
  val namespace: Int = 0, // Key for a StringTable
  val name: Int = 0, // Key for a StringTable
) {
  fun createReference(strings: StringTable): ResourceReference? {
    if (!strings.keys.containsAll(listOf(type, namespace, name))) return null

    val type = strings[type]
    val namespace = strings[namespace]
    val name = strings[name]

    val resNamespace = ResourceNamespace.fromPackageName(namespace)
    val resType = ResourceType.fromFolderName(type) ?: return null

    return ResourceReference(resNamespace, resType, name)
  }
}

fun createReference(url: String?, projectPackageName: String?): ResourceReference? {
  if (url == null || projectPackageName == null) {
    return null
  }
  val projectNamespace = ResourceNamespace.fromPackageName(projectPackageName)
  return ResourceUrl.parse(url)?.resolve(projectNamespace) {
    when (it) {
      SdkConstants.ANDROID_NS_NAME -> SdkConstants.ANDROID_URI
      else -> projectNamespace.xmlNamespaceUri
    }
  }
}
