/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.idea.wizard.template.AssetNameConverter
import com.android.tools.idea.wizard.template.AssetNameConverter.Type
import freemarker.template.SimpleScalar
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateModel
import freemarker.template.TemplateModelException

/**
 * Similar to [FmCamelCaseToUnderscoreMethod], but strips off common class
 * suffixes such as "Activity", "Fragment", etc.
 */
class FmClassNameToResourceMethod : TemplateMethodModelEx {
  override fun exec(args: List<*>): TemplateModel {
    if (args.size != 1) {
      throw TemplateModelException("Wrong arguments")
    }

    val name = args[0].toString()

    val resource = if (name.isEmpty()) "" else AssetNameConverter(Type.CLASS_NAME, name).getValue(Type.RESOURCE)
    return SimpleScalar(resource)
  }
}