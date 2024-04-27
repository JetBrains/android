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
@file:JvmName("IncludingLayout")
package com.android.tools.idea.rendering

import com.android.tools.idea.res.ensureNamespaceImported;
import com.android.SdkConstants
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.xml.XmlFile

fun setIncludingLayout(xmlFile: XmlFile, layout: String?) {
  xmlFile.rootTag?.let {
    WriteCommandAction.writeCommandAction(xmlFile.project, xmlFile)
      .withName(String.format("Set %1\$s Attribute", StringUtil.capitalize(SdkConstants.ATTR_SHOW_IN)))
      .run<Throwable> {
        if (layout != null) {
          ensureNamespaceImported(xmlFile, SdkConstants.TOOLS_URI, null)
        }
        it.setAttribute(SdkConstants.ATTR_SHOW_IN, SdkConstants.TOOLS_URI, layout)
      }
  }
}