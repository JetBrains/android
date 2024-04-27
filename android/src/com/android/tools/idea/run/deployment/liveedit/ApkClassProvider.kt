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

import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import org.jetbrains.kotlin.psi.KtFile

interface ApkClassProvider {
  fun getClass(ktFile: KtFile, className: String): IrClass?
}

class DefaultApkClassProvider : ApkClassProvider {
  override fun getClass(ktFile: KtFile, className: String): IrClass? {
    val classContent = ktFile.getModuleSystem()?.getClassFileFinderForSourceFile(ktFile.originalFile.virtualFile)?.findClassFile(className)
    return classContent?.let { IrClass(it.content) }
  }
}