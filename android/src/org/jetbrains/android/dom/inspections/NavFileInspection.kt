/*
 * Copyright (C) 2023 The Android Open Source Project
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
package org.jetbrains.android.dom.inspections

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.resources.ResourceFolderType
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.navigation.getClassesForTag
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.Nls

class NavFileInspection : LocalInspectionTool() {

  @Nls
  override fun getGroupDisplayName(): String = AndroidBundle.message("android.inspections.group.name")

  @Nls
  override fun getDisplayName(): String = AndroidBundle.message("android.inspections.nav.file")

  override fun getShortName(): String = "NavigationFile"

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file !is XmlFile) {
      return ProblemDescriptor.EMPTY_ARRAY
    }
    val facet = AndroidFacet.getInstance(file) ?: return ProblemDescriptor.EMPTY_ARRAY

    if (isRelevantFile(facet, file)) {
      val visitor = AttributesVisitor(facet.module, manager, isOnTheFly)
      file.accept(visitor)
      return visitor.myResult.toTypedArray()
    }
    return ProblemDescriptor.EMPTY_ARRAY
  }

  private fun isRelevantFile(facet: AndroidFacet, file: XmlFile): Boolean {
    val resourceType = ModuleResourceManagers.getInstance(facet).localResourceManager.getFileResourceFolderType(file)
    return resourceType == ResourceFolderType.NAVIGATION
  }


  private class AttributesVisitor(
    private val module: Module,
    private val myInspectionManager: InspectionManager,
    private val myOnTheFly: Boolean) : XmlRecursiveElementVisitor() {
    val myResult: MutableList<ProblemDescriptor> = ArrayList()

    override fun visitXmlAttribute(attribute: XmlAttribute) {
      super.visitXmlAttribute(attribute)
      val namespace = attribute.namespace
      if (ANDROID_URI != namespace || ATTR_NAME != attribute.localName) {
        return
      }
      val tag = attribute.parent
      val value = attribute.value
      val allowedDestinations = getClassesForTag(module, tag.name).keys.map { it.qualifiedName }.toSet()
      if (!allowedDestinations.contains(value)) {
        attribute.valueElement?.let {
          myResult.add(
            myInspectionManager.createProblemDescriptor(
              it,
              AndroidBundle.message("android.inspections.nav.name.not.valid", value, tag.name),
              myOnTheFly,
              emptyArray(),
              ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
            )
          )
        }
      }
    }
  }
}