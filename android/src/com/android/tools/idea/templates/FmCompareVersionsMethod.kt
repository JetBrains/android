/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.ide.common.repository.GradleVersion
import freemarker.template.SimpleNumber
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateModel
import freemarker.template.TemplateModelException

/**
 * Method invoked by FreeMarker to compare two Maven artifact version strings.
 */
abstract class AbstractFmCompareVersionsMethod : TemplateMethodModelEx {

  override fun exec(args: List<*>): TemplateModel {
    if (args.size != 2) {
      throw TemplateModelException("Wrong arguments")
    }

    val lhs = GradleVersion.parse(args[0].toString())
    val rhs = GradleVersion.parse(args[1].toString())

    return SimpleNumber(doComparison(lhs, rhs))
  }

  protected abstract fun doComparison(lhs: GradleVersion, rhs: GradleVersion): Int
}

class FmCompareVersionsMethod : AbstractFmCompareVersionsMethod() {
  override fun doComparison(lhs: GradleVersion, rhs: GradleVersion): Int = lhs.compareTo(rhs)
}

class FmCompareVersionsIgnoringQualifiersMethod : AbstractFmCompareVersionsMethod() {
  override fun doComparison(lhs: GradleVersion, rhs: GradleVersion): Int = lhs.compareIgnoringQualifiers(rhs)
}
