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
package com.android.tools.idea.model

import com.android.ide.common.xml.XmlPrettyPrinter
import com.intellij.openapi.module.Module
import org.w3c.dom.Document

internal sealed class MergedManifestException(
  private val mergedManifestInfo: MergedManifestInfo?,
  message: String,
  cause: Throwable? = null
) : RuntimeException(mergedManifestInfo?.errorMessage(message) ?: message, cause) {

  class MergingError(
    module: Module,
    cause: Throwable
  ) : MergedManifestException(null, "Manifest merger encountered an error processing module ${module.name}", cause)

  class MissingAttribute(
    val element: String,
    val namespace: String?,
    val attribute: String,
    info: MergedManifestInfo
  ) : MergedManifestException(info, "Element \"$element\" missing attribute \"${attribute.prependNamespace(namespace)}\"")

  class MissingElement(
    val element: String,
    info: MergedManifestInfo
  ) : MergedManifestException(info, "Missing element \"$element\"")

  class ParsingError(
    info: MergedManifestInfo,
    cause: Throwable
  ) : MergedManifestException(info, "Unexpected error parsing document", cause)
}

private fun MergedManifestInfo.errorMessage(reason: String): String {
  return "Error parsing the merged manifest for module \"${facet.module.name}\": $reason\n${xmlDocument?.toXmlString()}"
}

private fun String.prependNamespace(namespace: String?) = if (namespace == null) this else "$namespace:this"

private fun Document.toXmlString() = XmlPrettyPrinter.prettyPrint(this, true)