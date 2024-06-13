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
package com.android.tools.idea.compose

import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.flatMap
import com.android.tools.preview.ComposePreviewElement
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

typealias PsiComposePreviewElement = ComposePreviewElement<SmartPsiElementPointer<PsiElement>>

typealias PsiComposePreviewElementInstance =
  ComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>

/**
 * Class containing all the support methods that provide the model for the [ComposePreviewMananger].
 * These methods are responsible for the flow transformation from the initial
 * [ComposePreviewElement]s in the file to the output [ComposePreviewElementInstance].
 */
object ComposePreviewElementsModel {
  /** Instantiates all the given [ComposePreviewElement] into [ComposePreviewElementInstance]s. */
  fun instantiatedPreviewElementsFlow(
    input: Flow<FlowableCollection<PsiComposePreviewElement>>
  ): Flow<FlowableCollection<PsiComposePreviewElementInstance>> =
    input.map { inputPreviews -> inputPreviews.flatMap { it.resolve() } }
}
