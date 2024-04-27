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
package org.jetbrains.android.dom.layout

import com.android.SdkConstants.VIEW_MERGE
import com.android.SdkConstants.VIEW_TAG
import com.android.resources.ResourceFolderType
import com.intellij.openapi.module.Module
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.CustomLogicResourceDomFileDescription
import org.jetbrains.android.dom.SingleRootResourceDomFileDescription
import org.jetbrains.android.dom.layout.DataBindingDomFileDescription.hasDataBindingRootTag
import org.jetbrains.android.dom.layout.FragmentLayoutDomFileDescription.hasFragmentRootTag
import org.jetbrains.android.dom.layout.MergeDomFileDescription.Companion.hasMergeRootTag
import org.jetbrains.android.dom.layout.ViewTagDomFileDescription.Companion.hasViewRootTag

/**
 * Root tag based on user classes. If we can't parse tag as known one (see [checkFile]), we assume
 * it comes from class.
 */
class LayoutViewElementDomFileDescription :
  CustomLogicResourceDomFileDescription<LayoutViewElement>(
    LayoutViewElement::class.java,
    ResourceFolderType.LAYOUT,
    "View"
  ) {

  override fun checkFile(file: XmlFile, module: Module?): Boolean {
    return !hasFragmentRootTag(file) &&
      !hasDataBindingRootTag(file) &&
      !hasMergeRootTag(file) &&
      !hasViewRootTag(file)
  }
}

/** Merge root tag: `<merge>`. */
class MergeDomFileDescription :
  SingleRootResourceDomFileDescription<Merge>(
    Merge::class.java,
    VIEW_MERGE,
    ResourceFolderType.LAYOUT
  ) {

  companion object {
    fun hasMergeRootTag(file: XmlFile) = VIEW_MERGE == file.rootTag?.name
  }
}

/** View tag root tag: `<view>`. */
class ViewTagDomFileDescription :
  SingleRootResourceDomFileDescription<View>(
    View::class.java,
    VIEW_TAG,
    ResourceFolderType.LAYOUT
  ) {

  companion object {
    fun hasViewRootTag(file: XmlFile) = VIEW_TAG == file.rootTag?.name
  }
}
