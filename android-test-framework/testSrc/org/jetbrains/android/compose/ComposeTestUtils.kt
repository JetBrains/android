/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.intellij.testFramework.fixtures.CodeInsightTestFixture

fun CodeInsightTestFixture.stubComposableAnnotation(composableAnnotationPackage: String = "androidx.compose") {
  addFileToProject(
    "src/${composableAnnotationPackage.replace(".", "/")}/Composable.kt",
    // language=kotlin
    """
    package $composableAnnotationPackage

    annotation class Composable
    """.trimIndent()
  )
}

fun CodeInsightTestFixture.stubPreviewAnnotation(previewAnnotationPackage: String = "androidx.ui.tooling.preview") {
  addFileToProject(
    "src/${previewAnnotationPackage.replace(".", "/")}/Preview.kt",
    // language=kotlin
    """
    package $previewAnnotationPackage

    import kotlin.reflect.KClass

    object Devices {
        const val DEFAULT = ""

        const val NEXUS_7 = "id:Nexus 7"
        const val NEXUS_10 = "name:Nexus 10"
    }


    @Repeatable
    annotation class Preview(
      val name: String = "",
      val group: String = "",
      val apiLevel: Int = -1,
      val theme: String = "",
      val widthDp: Int = -1,
      val heightDp: Int = -1,
      val fontScale: Float = 1f,
      val showDecoration: Boolean = false,
      val showBackground: Boolean = false,
      val backgroundColor: Long = 0,
      val uiMode: Int = 0,
      val device: String = ""
    )

    interface PreviewParameterProvider<T> {
        val values: Sequence<T>
        val count get() = values.count()
    }

    annotation class PreviewParameter(
        val provider: KClass<out PreviewParameterProvider<*>>,
        val limit: Int = Int.MAX_VALUE
    )
    """.trimIndent()
  )
}
