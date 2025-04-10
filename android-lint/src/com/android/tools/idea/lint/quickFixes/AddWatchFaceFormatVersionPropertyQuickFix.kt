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
package com.android.tools.idea.lint.quickFixes

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_PROPERTY
import com.android.SdkConstants.WATCH_FACE_FORMAT_DEFAULT_VERSION
import com.android.SdkConstants.WATCH_FACE_FORMAT_VERSION_PROPERTY
import com.android.tools.idea.lint.AndroidLintBundle.Companion.message
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.xml.AndroidManifest.NODE_APPLICATION
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag

class AddWatchFaceFormatVersionPropertyQuickFix :
  DefaultLintQuickFix(message("android.lint.fix.add.wff.version.property")) {

  override fun apply(
    startElement: PsiElement,
    endElement: PsiElement,
    context: AndroidQuickfixContexts.Context,
  ) {
    val parent = PsiTreeUtil.getParentOfType<XmlTag>(startElement, XmlTag::class.java, false)
    if (parent?.name != NODE_APPLICATION) {
      return
    }

    var wffVersionProperty = parent.createChildTag(TAG_PROPERTY, null, null, false) ?: return

    // Find the right location for the property tag under <application>.
    val currentPropertyTags = parent.findSubTags(TAG_PROPERTY)
    wffVersionProperty =
      if (currentPropertyTags.size > 0) {
        parent.addAfter(wffVersionProperty, currentPropertyTags.last()) as XmlTag
      } else {
        parent.addSubTag(wffVersionProperty, true)
      }

    wffVersionProperty.setAttribute(ATTR_NAME, ANDROID_URI, WATCH_FACE_FORMAT_VERSION_PROPERTY)
    wffVersionProperty.setAttribute(ATTR_VALUE, ANDROID_URI, WATCH_FACE_FORMAT_DEFAULT_VERSION)
  }

  override fun isApplicable(
    startElement: PsiElement,
    endElement: PsiElement,
    contextType: AndroidQuickfixContexts.ContextType,
  ): Boolean {
    val parent = PsiTreeUtil.getParentOfType(startElement, XmlTag::class.java, false)
    return parent?.name == TAG_APPLICATION
  }
}
