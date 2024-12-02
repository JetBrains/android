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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import org.jetbrains.kotlin.psi.KtFile

interface ApkClassProvider {
  fun getClass(applicationServices: ApplicationLiveEditServices, ktFile: KtFile, className: String): IrClass?
}

class DefaultApkClassProvider : ApkClassProvider {
  override fun getClass(
    applicationServices: ApplicationLiveEditServices,
    ktFile: KtFile,
    className: String
  ): IrClass? {
    val classContent = applicationServices.getClassContent(ktFile.originalFile.virtualFile, className)
    return classContent?.let { IrClass(it.content) }
  }
}
