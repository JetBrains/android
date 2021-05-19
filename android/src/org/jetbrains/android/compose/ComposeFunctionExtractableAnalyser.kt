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
package org.jetbrains.android.compose

import com.android.tools.idea.flags.StudioFlags
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.AdditionalExtractableAnalyser
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractableCodeDescriptor
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.resolve.BindingContext

/**
 * Adds [COMPOSABLE_FQ_NAME] annotation to a function when it's extracted from a function annotated with [COMPOSABLE_FQ_NAME]
 */
class ComposableFunctionExtractableAnalyser : AdditionalExtractableAnalyser {
  override fun amendDescriptor(descriptor: ExtractableCodeDescriptor): ExtractableCodeDescriptor {
    if (!StudioFlags.COMPOSE_FUNCTION_EXTRACTION.get()) return descriptor

    val bindingContext = descriptor.extractionData.bindingContext ?: return descriptor
    val sourceFunction = descriptor.extractionData.targetSibling
    if (sourceFunction is KtAnnotated) {
      val annotationDescriptors = sourceFunction.annotationEntries.mapNotNull { bindingContext.get(BindingContext.ANNOTATION, it) }
      val composableAnnotation = annotationDescriptors.find { COMPOSABLE_FQ_NAMES.contains(it.fqName?.asString())  } ?: return descriptor
      return descriptor.copy(annotations = descriptor.annotations + composableAnnotation)
    }

    return descriptor
  }
}