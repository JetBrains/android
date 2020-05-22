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
import com.intellij.openapi.module.Module
import com.intellij.psi.xml.XmlFile

/**
 * Merge root tag: `<merge>`
 */
class MergeDomFileDescription : LayoutDomFileDescription<Merge>(Merge::class.java, VIEW_MERGE) {
  override fun checkFile(file: XmlFile, module: Module?) = hasMergeRootTag(file)

  companion object {
    fun hasMergeRootTag(file: XmlFile) = VIEW_MERGE == file.rootTag?.name
  }
}

/**
 * View tag root tag: `<view>`
 */
class ViewTagDomFileDescription : LayoutDomFileDescription<View>(View::class.java, VIEW_TAG) {
  override fun checkFile(file: XmlFile, module: Module?) = hasViewRootTag(file)

  companion object {
    fun hasViewRootTag(file: XmlFile) = VIEW_TAG == file.rootTag?.name
  }
}