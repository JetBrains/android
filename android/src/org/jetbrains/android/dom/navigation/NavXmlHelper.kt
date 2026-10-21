/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android.dom.navigation

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_GRAPH
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.NAVIGATION_PREFIX
import com.android.SdkConstants.TAG_INCLUDE
import com.android.SdkConstants.TAG_NAVIGATION
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.stripPrefixFromId
import com.android.ide.common.util.PathString
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.util.toVirtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

fun getStartDestLayoutId(navResourceId: String, project: Project, resourceResolver: ResourceResolver): String? {
  if (!navResourceId.startsWith(NAVIGATION_PREFIX)) {
    return null
  }
  val fileName = resourceResolver.findResValue(navResourceId, false)?.value ?: return null
  val file = PathString(fileName).toVirtualFile() ?: return null
  val psiFile = AndroidPsiUtils.getPsiFileSafely(project, file) as? XmlFile ?: return null
  return ApplicationManager.getApplication()
    .runReadAction(
      Computable<String> {
        findStartDestination(psiFile.rootTag, project, resourceResolver, mutableSetOf(navResourceId))
          ?.getAttributeValue(ATTR_LAYOUT, TOOLS_URI)
      }
    )
}

/*
Start at the given tag and recurse into the start destination attribute
until we reach a tag that is not a navigation element. Return the tag or null
if the attribute or the tag is missing or a loop is detected.
 */
private fun findStartDestination(
  root: XmlTag?,
  project: Project,
  resourceResolver: ResourceResolver,
  visited: MutableSet<String>,
): XmlTag? {
  var current = root
  while (current?.name == TAG_NAVIGATION || current?.name == TAG_INCLUDE) {
    if (current.name == TAG_INCLUDE) {
      val graphRef = current.getAttributeValue(ATTR_GRAPH, AUTO_URI) ?: return null
      if (!graphRef.startsWith(NAVIGATION_PREFIX) || !visited.add(graphRef)) {
        return null
      }
      val fileName = resourceResolver.findResValue(graphRef, false)?.value ?: return null
      val file = PathString(fileName).toVirtualFile() ?: return null
      val psiFile = AndroidPsiUtils.getPsiFileSafely(project, file) as? XmlFile ?: return null
      current = psiFile.rootTag
      continue
    }

    val tagId = current.getAttributeValue(ATTR_ID, ANDROID_URI) ?: current.getAttributeValue(ATTR_GRAPH, AUTO_URI)
    if (tagId != null && !visited.add(tagId)) {
      return null
    }

    val startDestId = current.getAttributeValue(ATTR_START_DESTINATION, AUTO_URI)?.let(::stripPrefixFromId) ?: return null

    current =
      current.children.filterIsInstance(XmlTag::class.java).firstOrNull { tag ->
        val childId = tag.getAttributeValue(ATTR_ID, ANDROID_URI)?.let(::stripPrefixFromId)
        val graphId = tag.getAttributeValue(ATTR_GRAPH, AUTO_URI)?.removePrefix(NAVIGATION_PREFIX)
        childId == startDestId || (tag.name == TAG_INCLUDE && graphId == startDestId)
      }
  }

  return current
}
