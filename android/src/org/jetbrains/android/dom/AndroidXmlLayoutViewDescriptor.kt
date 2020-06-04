/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.android.dom

import com.android.SdkConstants
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import com.intellij.xml.XmlElementDescriptor
import org.jetbrains.android.util.AndroidUtils
import javax.swing.Icon

/**
 * XmlElementDescriptor for all View tags (tags that are inheritors of [SdkConstants.CLASS_VIEW]) and <view></view> tag.
 *
 * AndroidXmlLayoutViewDescriptor is returned by AndroidDomElementDescriptorProvider.
 */
class AndroidXmlLayoutViewDescriptor(val viewClass: PsiClass?, parentDescriptor: XmlElementDescriptor, icon: Icon?) :
  AndroidXmlTagDescriptor(viewClass, parentDescriptor, AndroidUtils.VIEW_CLASS_NAME, icon) {
  val isViewGroup by lazy { InheritanceUtil.isInheritor(viewClass, SdkConstants.CLASS_VIEWGROUP) }

  override fun getContentType(): Int {
    if (isViewGroup) return XmlElementDescriptor.CONTENT_TYPE_MIXED else return XmlElementDescriptor.CONTENT_TYPE_EMPTY
  }
}
