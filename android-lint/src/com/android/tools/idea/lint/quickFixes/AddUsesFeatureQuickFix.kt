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
package com.android.tools.idea.lint.quickFixes

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.VALUE_FALSE
import com.android.xml.AndroidManifest.ATTRIBUTE_REQUIRED
import com.android.xml.AndroidManifest.NODE_MANIFEST
import com.android.xml.AndroidManifest.NODE_PERMISSION
import com.android.xml.AndroidManifest.NODE_USES_CONFIGURATION
import com.android.xml.AndroidManifest.NODE_USES_FEATURE
import com.android.xml.AndroidManifest.NODE_USES_SDK
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag

/**
 * Quickfix for adding a &lt;uses-feature&gt; element with required="false" to the
 * AndroidManifest.xml
 *
 * Note: The quick fix attempts to add the uses-feature tag after tags to adhere to the typical
 * manifest ordering. It finds and adds the element after the following elements if present and
 * skips to the next element up the chain. e.g: if only uses-sdk is present, the new uses-feature
 * tag is added after the uses-sdk
 * * uses-feature
 * * uses-configuration
 * * uses-sdk
 * * node-permission
 *
 * If none of the above elements are present, then it adds the uses-feature element as the first
 * child of the manifest element.
 */
class AddUsesFeatureQuickFix(private val myFeatureName: String, element: PsiElement) :
  PsiBasedModCommandAction<PsiElement>(element) {
  override fun getFamilyName() = "AddUsesFeatureQuickFix"

  override fun getPresentation(context: ActionContext, element: PsiElement) =
    if (PsiTreeUtil.getTopmostParentOfType(element, XmlTag::class.java)?.name == NODE_MANIFEST)
      Presentation.of("Add uses-feature tag")
    else null

  override fun perform(context: ActionContext, element: PsiElement): ModCommand {
    val parent =
      PsiTreeUtil.getTopmostParentOfType(element, XmlTag::class.java) ?: return ModCommand.nop()

    @Suppress("UnstableApiUsage")
    return ModCommand.psiUpdate(parent) { tag, _ ->
      var usesFeatureTag = tag.createChildTag(NODE_USES_FEATURE, null, null, false)
      val ancestor = findLocationForUsesFeature(tag)
      usesFeatureTag =
        if (ancestor != null) {
          // Add the uses-feature element after all uses-feature tags if any.
          tag.addAfter(usesFeatureTag!!, ancestor) as XmlTag
        } else {
          tag.addSubTag(usesFeatureTag, true)
        }
      if (usesFeatureTag != null) {
        usesFeatureTag.setAttribute(ATTR_NAME, ANDROID_URI, myFeatureName)
        usesFeatureTag.setAttribute(ATTRIBUTE_REQUIRED, ANDROID_URI, VALUE_FALSE)
      }
    }
  }

  companion object {
    // Find the correct location in the manifest to add the uses-feature tag.
    // https://developer.android.com/guide/topics/manifest/manifest-intro.html
    private fun findLocationForUsesFeature(parent: XmlTag): XmlTag? {
      // reverse manifest order for location of uses-feature.
      // The reason this is not a static final is to prevent the array creation at
      // clinit time and delay it to when the fix is applied.
      val reverseOrderManifestElements =
        arrayOf(NODE_USES_FEATURE, NODE_USES_CONFIGURATION, NODE_USES_SDK, NODE_PERMISSION)
      for (elementName in reverseOrderManifestElements) {
        val existingTags = parent.findSubTags(elementName)
        val len = existingTags.size
        if (len > 0) {
          return existingTags[len - 1]
        }
      }
      return null
    }
  }
}
