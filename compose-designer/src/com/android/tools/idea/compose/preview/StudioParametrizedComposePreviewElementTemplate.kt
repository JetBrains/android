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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.compose.preview.util.containingFile
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.StudioModuleRenderContext
import com.android.tools.preview.ParametrizedComposePreviewElementTemplate
import com.android.tools.preview.PreviewParameter
import com.android.tools.rendering.api.RenderModelModule
import com.android.tools.rendering.classloading.ClassTransform
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.android.uipreview.StudioModuleClassLoaderManager

/**
 * [ParametrizedComposePreviewElementTemplate] based on studio-specific [ModuleRenderContext] from
 * [PsiComposePreviewElement] constructor.
 */
class StudioParametrizedComposePreviewElementTemplate(
  basePreviewElement: PsiComposePreviewElement,
  parameterProviders: Collection<PreviewParameter>,
) :
  ParametrizedComposePreviewElementTemplate<SmartPsiElementPointer<PsiElement>>(
    basePreviewElement,
    parameterProviders,
    StudioParametrizedComposePreviewElementTemplate::class.java.classLoader,
    factory@{ element ->
      val psiFile = element.containingFile ?: return@factory null
      var buildTargetRefence = BuildTargetReference.from(psiFile) ?: return@factory null
      StudioModuleRenderContext.forBuildTargetReference(buildTargetRefence).let {
        RenderModelModule.ClassLoaderProvider {
          parent: ClassLoader?,
          additionalProjectTransform: ClassTransform,
          additionalNonProjectTransform: ClassTransform,
          onNewModuleClassLoader: Runnable ->
          StudioModuleClassLoaderManager.get()
            .getPrivate(parent, it, additionalProjectTransform, additionalNonProjectTransform)
            .also {
              onNewModuleClassLoader.run()
            } // TEMP: Adding this for consistency even though we pass `Runnable {}`.
        }
      }
    },
  )
