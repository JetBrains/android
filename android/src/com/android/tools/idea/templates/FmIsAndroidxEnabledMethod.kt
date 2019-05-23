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
package com.android.tools.idea.templates

import com.android.tools.idea.templates.TemplateMetadata.ATTR_ANDROIDX_SUPPORT
import freemarker.template.TemplateBooleanModel
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateModel
import freemarker.template.TemplateModelException

/**
 * Method invoked by FreeMarker to check if AndroidX mapping should be enabled. It has no parameters.
 */
class FmIsAndroidxEnabledMethod(private val paramMap: Map<String, Any>) : TemplateMethodModelEx {
  override fun exec(args: List<*>): TemplateModel {
    val useAndroidx = paramMap[ATTR_ANDROIDX_SUPPORT] as? Boolean == true
    return if (useAndroidx) TemplateBooleanModel.TRUE else TemplateBooleanModel.FALSE
  }
}