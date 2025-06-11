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
package com.android.tools.idea.wear.dwf.inspections

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_PROPERTY
import com.android.SdkConstants.WATCH_FACE_FORMAT_VERSION_PROPERTY
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.wear.dwf.WearDwfBundle.message
import com.android.tools.wear.wff.WFFVersion
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription
import org.jetbrains.android.facet.AndroidFacet

private val knownWffVersions = WFFVersion.entries.map { it.version }

class UnknownWFFVersionInspection : LocalInspectionTool() {

  override fun isAvailableForFile(file: PsiFile): Boolean {
    val file = file as? XmlFile ?: return false
    val facet = file.androidFacet ?: return false
    return ManifestDomFileDescription.isManifestFile(file, facet)
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
    object : XmlElementVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        val androidFacet = tag.androidFacet ?: return
        if (!tag.isWFFVersionProperty()) return
        val wffVersionAttribute = tag.getAttribute(ATTR_VALUE, ANDROID_URI) ?: return
        val wffVersion = androidFacet.resolvePlaceholders(wffVersionAttribute.value)
        if (!wffVersion.isInteger()) return
        if (wffVersion !in knownWffVersions) {
          holder.registerProblem(
            wffVersionAttribute,
            message("inspection.unknown.wff.version.description"),
            ProblemHighlightType.WARNING,
          )
        }
      }
    }

  private fun XmlTag.isWFFVersionProperty() =
    name == TAG_PROPERTY &&
      getAttributeValue(ATTR_NAME, ANDROID_URI) == WATCH_FACE_FORMAT_VERSION_PROPERTY

  private fun AndroidFacet.resolvePlaceholders(value: String?) =
    if (value == null) null else getModuleSystem().getManifestOverrides().resolvePlaceholders(value)

  private fun String?.isInteger() = this?.toIntOrNull() != null
}
