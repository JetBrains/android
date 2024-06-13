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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.ComposePreviewElementsModel
import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ComposePreviewElementsModelTest {
  @Test
  fun testInstantiatedPreviewElementsFlow(): Unit = runBlocking {
    val basePreviewElement =
      SingleComposePreviewElementInstance.forTesting<SmartPsiElementPointer<PsiElement>>(
        "Template",
        groupName = "TemplateGroup",
      )

    // Fake PreviewElementTemplate that generates a couple of instances
    val template =
      object : PsiComposePreviewElement by basePreviewElement {
        private val templateInstances =
          listOf(
            SingleComposePreviewElementInstance.forTesting<SmartPsiElementPointer<PsiElement>>(
              "Instance1",
              groupName = "TemplateGroup",
            ),
            SingleComposePreviewElementInstance.forTesting("Instance2", groupName = "TemplateGroup"),
          )

        override fun resolve(): Sequence<PsiComposePreviewElementInstance> =
          templateInstances.asSequence()
      }
    val allPreviews =
      listOf(
        SingleComposePreviewElementInstance.forTesting("PreviewMethod1", groupName = "GroupA"),
        SingleComposePreviewElementInstance.forTesting("SeparatePreview", groupName = "GroupA"),
        SingleComposePreviewElementInstance.forTesting("PreviewMethod2", groupName = "GroupB"),
        SingleComposePreviewElementInstance.forTesting("AMethod"),
        template,
      )

    val instancesFlow =
      ComposePreviewElementsModel.instantiatedPreviewElementsFlow(
        MutableStateFlow(FlowableCollection.Present(allPreviews))
      )

    assertThat(instancesFlow.first().asCollection().map { it.methodFqn })
      .containsExactly(
        "PreviewMethod1",
        "SeparatePreview",
        "PreviewMethod2",
        "AMethod",
        "Instance1",
        "Instance2",
      )
      .inOrder()
  }
}
