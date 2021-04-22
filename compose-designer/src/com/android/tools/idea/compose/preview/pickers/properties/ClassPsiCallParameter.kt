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
package com.android.tools.idea.compose.preview.pickers.properties

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.util.ImportDescriptorResult
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

/**
 * [PsiPropertyItem] for @Preview parameters that can take an Enum from the project. Can assign a fully qualified class to the value. It
 * while trying to import the class in to the parameter's file.
 */
internal class ClassPsiCallParameter(
  project: Project,
  private val model: PsiCallPropertyModel,
  resolvedCall: ResolvedCall<*>,
  descriptor: ValueParameterDescriptor,
  argumentExpression: KtExpression?,
  initialValue: String?
) : PsiCallParameterPropertyItem(
  project,
  model,
  resolvedCall,
  descriptor,
  argumentExpression,
  initialValue
) {

  /**
   * Set a new value using their fully qualified class. Will try to add the import statement for the class, if it fails, it will default to
   * set the fully qualified name of the value.
   */
  fun setFqValue(fqClass: String, className: String, newValue: String) {
    val importResult = model.ktFile.resolveImportReference(FqName(fqClass)).firstOrNull()?.let { importDescriptor ->
      WriteCommandAction.runWriteCommandAction<ImportDescriptorResult>(project) {
        ImportInsertHelper.getInstance(project).importDescriptor(model.ktFile, importDescriptor)
      }
    }

    if (importResult != null && importResult != ImportDescriptorResult.FAIL) {
      writeNewValue("$className.$newValue", true)
    }
    else {
      writeNewValue("$fqClass.$newValue", true)
    }
  }
}