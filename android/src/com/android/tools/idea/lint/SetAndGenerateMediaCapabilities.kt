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
package com.android.tools.idea.lint

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_PROPERTY
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.tools.lint.checks.MediaCapabilitiesDetector
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.plugins.groovy.lang.psi.util.childrenOfType

/**
 * A [DefaultLintQuickFix] implementation responsible for
 * setting the media capabilities property
 * as well as generating the capabilities descriptor.
 * Uses composition to call the respective quick fixes.
 *
 * Pre-conditions:
 *
 *  * There is no android.content.MEDIA_CAPABILITIES <property> in <application/>
 *  * Value @xml/media_capabilities is not an existing file.
 *
  * At the end of the quick fix, it opens up the media capabilities descriptor in the editor.
 */
internal class SetAndGenerateMediaCapabilities : DefaultLintQuickFix(COMMAND_NAME) {
  private var resourceUrl = ResourceUrl.create(ResourceType.XML, RESOURCE_URL_NAME, false)
  private val myGenerateDescriptorFix = GenerateMediaCapabilitiesDescriptorFix(resourceUrl)

  override fun apply(startElement: PsiElement,
                     endElement: PsiElement,
                     context: AndroidQuickfixContexts.Context) {
    val tag = startElement.getParentOfType<XmlTag>(false)
    if (tag != null && tag.name == TAG_APPLICATION) {
      WriteCommandAction.writeCommandAction(startElement.containingFile).withName(COMMAND_NAME).run<Throwable> {
        val propertyTag = tag.add(tag.createChildTag(TAG_PROPERTY, tag.namespace, null, false)) as? XmlTag
        propertyTag?.setAttribute(ATTR_NAME, ANDROID_URI, MediaCapabilitiesDetector.VALUE_MEDIA_CAPABILITIES)
        propertyTag?.setAttribute(MediaCapabilitiesDetector.ATTR_RESOURCE, ANDROID_URI, resourceUrl.toString())
        myGenerateDescriptorFix.apply(startElement, endElement, context)
      }
    }
  }

  override fun isApplicable(startElement: PsiElement,
                            endElement: PsiElement,
                            contextType: AndroidQuickfixContexts.ContextType): Boolean {
    val applicationTag = startElement.getParentOfType<XmlTag>(false)
    if (applicationTag == null || applicationTag.name != TAG_APPLICATION) {
      return false
    }
    return !isMediaPropertyPresent(applicationTag)
            && myGenerateDescriptorFix.isApplicable(startElement, endElement, contextType)
  }

  companion object {
    private const val RESOURCE_URL_NAME = "media_capabilities"

    /**
     * @param startElement Element pointing to the an attribute of application
     * @return true iff android:allowBackup=true or the attribute is not set.
     */
    fun isMediaPropertyPresent(applicationTag: PsiElement): Boolean {
      return applicationTag.childrenOfType<XmlTag>().any {
        it.name == TAG_PROPERTY
        && it.getAttribute(ATTR_NAME, ANDROID_URI)?.value == MediaCapabilitiesDetector.VALUE_MEDIA_CAPABILITIES
      }
    }
  }

}

private const val COMMAND_NAME = "Add media capabilities property and generate descriptor"