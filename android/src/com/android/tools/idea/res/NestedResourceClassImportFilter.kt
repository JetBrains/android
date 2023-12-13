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
package com.android.tools.idea.res

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.idea.util.ClassImportFilter
import org.jetbrains.kotlin.idea.util.ClassImportFilter.ClassInfo
import org.jetbrains.kotlin.psi.KtFile

/**
 * Filter that prevents nested resource classes from being automatically imported.
 *
 * Without this, classes like `my.app.pkg.R.string`, `my.app.pkg.R.color`, etc. might be
 * inadvertently imported if the user has checked the "Insert imports for nested classes" option
 * selected.
 */
class NestedResourceClassImportFilter : ClassImportFilter {
  override fun allowClassImport(classInfo: ClassInfo, contextFile: KtFile) =
    !classInfo.isNestedRClass()

  private fun ClassInfo.isNestedRClass() =
    isNested &&
      classKind == ClassKind.CLASS &&
      fqName.parent().shortName().asString() == "R" &&
      fqName.shortName().asString().first().isLowerCase()
}
