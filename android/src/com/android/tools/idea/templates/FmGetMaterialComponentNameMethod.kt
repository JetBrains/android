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

import com.android.support.AndroidxNameUtils
import freemarker.template.SimpleScalar
import freemarker.template.TemplateBooleanModel
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateModel
import freemarker.template.TemplateModelException

/**
 * Method invoked by FreeMarker to compute the mapping for material components naming.
 *
 * Arguments:
 *
 *  1. The old name (String)
 *  2. If the project has a dependency for the material2 library
 *
 * Example usage: `className=getMaterialComponentName('android.support.design.widget.FloatingActionButton', useMaterial2)`,
 * useMaterial2 flag is defined as a global flag.
 * if useMaterial2 if true, that returns "com.google.android.material.widget.FloatingActionButton"
 * otherwise "android.support.design.widget.FloatingActionButton"
 */
class FmGetMaterialComponentNameMethod : TemplateMethodModelEx {
  override fun exec(args: List<*>): TemplateModel {
    if (args.size != 2) {
      throw TemplateModelException("Wrong arguments")
    }

    val oldName = args[0].toString()
    return when ((args[1] as TemplateBooleanModel).asBoolean) {
      true -> SimpleScalar(AndroidxNameUtils.getNewName(oldName))
      else -> SimpleScalar(oldName)
    }
  }
}