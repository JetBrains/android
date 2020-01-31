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

import com.android.tools.idea.templates.TemplateUtils.underlinesToCamelCase
import freemarker.template.SimpleScalar
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateModel
import freemarker.template.TemplateModelException

// TODO(qumeric): this should be removed in favor of TemplateUtils method

/**
 * Method invoked by FreeMarker to convert a underscore_names word into CamelCase.
 */
class FmUnderscoreToCamelCaseMethod : TemplateMethodModelEx {
  override fun exec(args: List<*>): TemplateModel {
    if (args.size != 1) {
      throw TemplateModelException("Wrong arguments")
    }
    return SimpleScalar(underlinesToCamelCase(args[0].toString()))
  }
}