/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.service

import com.android.tools.idea.compose.ComposeProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PreviewDefinitionProviderTest {
  @get:Rule val projectRule = ComposeProjectRule()

  @Test
  fun testNameArgumentIsParsedCorrectly() = runTest {
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(name = "Named")
        @Preview("Positional")
        @Preview
        @Composable
        fun MyComponent() {}
      """,
    )

    assertPreviewDisplayNames("MyComponent:Named", "MyComponent:Positional", "MyComponent")
  }

  @Test
  fun testMultiPreviewAnnotation() = runTest {
    addFile(
      "MyPreviews.kt",
      """
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(name = "Preview1")
        @Preview(name = "Preview2")
        annotation class MyMultiPreview
      """,
    )
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable

        @MyMultiPreview
        @Composable
        fun MyComponent() {}
      """,
    )

    assertPreviewDisplayNames("MyComponent:Preview1", "MyComponent:Preview2")
  }

  @Test
  fun testNestedMultiPreviewAnnotation() = runTest {
    addFile(
      "MyPreviews.kt",
      """
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(name = "Preview1")
        @Preview(name = "Preview2")
        annotation class MyMultiPreview

        @MyMultiPreview
        @Preview(name = "Preview3")
        annotation class MyNestedMultiPreview
      """,
    )
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable

        @MyNestedMultiPreview
        @Composable
        fun MyComponent() {}
      """,
    )

    assertPreviewDisplayNames(
      "MyComponent:Preview1",
      "MyComponent:Preview2",
      "MyComponent:Preview3",
    )
  }

  @Test
  fun testMultiPreviewWithParameters() = runTest {
    addFile(
      "MyPreviews.kt",
      """
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(name = "Phone", device = "spec:shape=Normal,width=360,height=640,unit=dp,dpi=480")
        @Preview(name = "Tablet", device = "spec:shape=Normal,width=1280,height=800,unit=dp,dpi=480")
        annotation class DevicePreviews
      """,
    )
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable

        @DevicePreviews
        @Composable
        fun MyComponent() {}
      """,
    )

    assertPreviewDisplayNames("MyComponent:Phone", "MyComponent:Tablet")
  }

  @Test
  fun testNestedMultiPreviewsAreCombined() = runTest {
    addFile(
      "MyPreviews.kt",
      """
        import androidx.compose.ui.tooling.preview.Preview

        // A multi-preview for different devices
        @Preview(name = "Phone", device = "spec:shape=Normal,width=360,height=640,unit=dp,dpi=480")
        @Preview(name = "Tablet", device = "spec:shape=Normal,width=1280,height=800,unit=dp,dpi=480")
        annotation class DevicePreviews

        // A multi-preview for different themes
        @Preview(name = "Light", uiMode = 16) // UI_MODE_NIGHT_NO
        @Preview(name = "Dark", uiMode = 32) // UI_MODE_NIGHT_YES
        annotation class ThemePreviews

        // A NESTED multi-preview that combines the two above
        @DevicePreviews
        @ThemePreviews
        annotation class AllPreviews
      """,
    )
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable

        @AllPreviews
        @Composable
        fun MyComponent() {}
      """,
    )

    assertPreviewDisplayNames(
      "MyComponent:Phone",
      "MyComponent:Tablet",
      "MyComponent:Light",
      "MyComponent:Dark",
    )
  }

  @Test
  fun testDeeplyNestedMultiPreview() = runTest {
    addFile(
      "MyPreviews.kt",
      """
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(name = "Preview1")
        annotation class Level1

        @Level1
        @Preview(name = "Preview2")
        annotation class Level2

        @Level2
        @Preview(name = "Preview3")
        annotation class Level3
      """,
    )
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable

        @Level3
        @Composable
        fun MyComponent() {}
      """,
    )

    assertPreviewDisplayNames(
      "MyComponent:Preview1",
      "MyComponent:Preview2",
      "MyComponent:Preview3",
    )
  }

  @Test
  fun testMultipleMultiPreviewAnnotations() = runTest {
    addFile(
      "MyPreviews.kt",
      """
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(name = "Preview1")
        @Preview(name = "Preview2")
        annotation class Multi1

        @Preview(name = "Preview3")
        @Preview(name = "Preview4")
        annotation class Multi2
      """,
    )
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable

        @Multi1
        @Multi2
        @Composable
        fun MyComponent() {}
      """,
    )

    assertPreviewDisplayNames(
      "MyComponent:Preview1",
      "MyComponent:Preview2",
      "MyComponent:Preview3",
      "MyComponent:Preview4",
    )
  }

  @Test
  fun testNestedMultiPreviewAnnotationsAreTheSame() = runTest {
    addFile(
      "MyPreviews.kt",
      """
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(name = "Phone", device = "spec:shape=Normal,width=360,height=640,unit=dp,dpi=480")
        annotation class PhonePreview

        @PhonePreview
        @Preview(name = "Tablet", device = "spec:shape=Normal,width=1280,height=800,unit=dp,dpi=480")
        annotation class DevicePreviews
      """,
    )
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable

        @DevicePreviews
        @Composable
        fun MyComponent() {}
      """,
    )

    assertPreviewDisplayNames("MyComponent:Phone", "MyComponent:Tablet")
  }

  @Test
  fun testAnnotationResolutionByFqn() = runTest {
    addFile(
      "Annotations.kt",
      """
        package com.another.package

        annotation class Preview
      """,
      "com/another/package",
    )
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview as RealPreview
        import com.another.package.Preview as FakePreview

        @RealPreview
        @Composable
        fun RealPreviewComponent() {}

        @FakePreview
        @Composable
        fun FakePreviewComponent() {}
      """,
    )

    assertPreviewDisplayNames("RealPreviewComponent")
  }

  @Test
  fun testWearOsDeviceIsParsed() = runTest {
    addFile(
      "WearComponent.kt",
      """
        import androidx.compose.foundation.layout.Column
        import androidx.compose.foundation.layout.padding
        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.tooling.preview.Devices
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.ui.unit.dp

        @Preview(device = Devices.WEAR_OS_LARGE_ROUND)
        @Composable
        fun WearPreview() {
          Column { Text("Hello world", modifier = Modifier.padding(10.dp)) }
        }
      """,
    )

    assertPreviewDisplayNames("WearPreview")
  }

  @Test
  fun testAddingFileRecalculatesPreviews() = runTest {
    addFile(
      "InitialComponent.kt",
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        @Composable
        fun InitialPreview() {}
      """,
    )
    assertPreviewDisplayNames("InitialPreview")

    addFile(
      "NewComponent.kt",
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        @Composable
        fun NewPreview() {}
      """,
    )
    assertPreviewDisplayNames("InitialPreview", "NewPreview")
  }

  @Test
  fun testNoPreviewsFound() = runTest {
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable

        @Composable
        fun MyComponent() {}
      """,
    )
    assertNoPreviewsFound()
  }

  @Test
  fun testInvalidPreviewOnNonComposable() = runTest {
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        fun NotAComposable() {}
      """,
    )
    assertNoPreviewsFound()
  }

  @Test
  fun testCircularMultiPreviewReference() = runTest {
    addFile(
      "MyPreviews.kt",
      """
        import androidx.compose.ui.tooling.preview.Preview

        @SelfReferencing
        annotation class SelfReferencing

        @Preview
        @Composable
        fun MyComponent() {}
      """,
    )
    assertPreviewDisplayNames("MyComponent")
  }

  @Test
  fun testTypeAliasForPreview() = runTest {
    addFile(
      "MyPreviews.kt",
      """
        import androidx.compose.ui.tooling.preview.Preview

        typealias MyPreview = Preview
      """,
    )
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable

        @MyPreview
        @Composable
        fun MyComponent() {}
      """,
    )
    assertPreviewDisplayNames("MyComponent")
  }

  @Test
  fun testDeletingFileRecalculatesPreviews() = runTest {
    val file1 =
      addFile(
        "File1.kt",
        """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        @Composable
        fun Preview1() {}
      """,
      )
    addFile(
      "File2.kt",
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        @Composable
        fun Preview2() {}
      """,
    )
    assertPreviewDisplayNames("Preview1", "Preview2")

    WriteCommandAction.runWriteCommandAction(projectRule.project) { file1.virtualFile.delete(this) }

    assertPreviewDisplayNames("Preview2")
  }

  @Test
  fun testModifyingFileRecalculatesPreviews() = runTest {
    val file =
      addFile(
        "MyComponent.kt",
        """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        @Composable
        fun MyComponent() {}
      """,
      )
    assertPreviewDisplayNames("MyComponent")

    projectRule.fixture.saveText(
      file.virtualFile,
      """
        package com.example

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        @Composable
        fun MyComponent() {}

        @Preview
        @Composable
        fun AnotherComponent() {}
      """
        .trimIndent(),
    )

    assertPreviewDisplayNames("MyComponent", "AnotherComponent")
  }

  @Test
  fun testPreviewWithDeviceAndNoName() = runTest {
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(device = "spec:shape=Normal,width=360,height=640,unit=dp,dpi=480")
        @Composable
        fun MyComponent() {}
      """,
    )
    assertPreviewDisplayNames("MyComponent")
  }

  @Test
  fun testVariousAnnotationParameters() = runTest {
    addFile(
      "VariousPreviews.kt",
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Devices
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(uiMode = 16)
        @Composable
        fun UiModeComponent() {}

        @Preview(showBackground = true)
        @Composable
        fun BooleanComponent() {}

        @Preview(uiMode = 16, device = Devices.PHONE)
        @Composable
        fun MultipleParamsComponent() {}

        @Preview("MyPositionalName")
        @Composable
        fun PositionalNameComponent() {}
      """,
    )
    assertPreviewDisplayNames(
      "UiModeComponent",
      "BooleanComponent",
      "MultipleParamsComponent",
      "PositionalNameComponent:MyPositionalName",
    )
  }

  @Test
  fun testInvalidPreviewWithParameters() = runTest {
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        @Composable
        fun MyComponent(text: String) {}
      """,
    )
    assertNoPreviewsFound()
  }

  @Test
  fun testPreviewsOnPrivateAndInternalFunctions() = runTest {
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview
        @Composable
        private fun MyPrivatePreview() {}

        @Preview
        @Composable
        internal fun MyInternalPreview() {}
      """,
    )
    assertPreviewDisplayNames("MyPrivatePreview", "MyInternalPreview")
  }

  @Test
  fun testEmptyMultiPreviewAnnotation() = runTest {
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        annotation class EmptyMultiPreview

        @EmptyMultiPreview
        @Composable
        fun MyComponent() {}
      """,
    )
    assertNoPreviewsFound()
  }

  @Test
  fun testPreviewsWithSameName() = runTest {
    addFile(
      "MyComponent.kt",
      """
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(name = "MyPreview", uiMode = 16)
        @Preview(name = "MyPreview", uiMode = 32)
        @Composable
        fun MyComponent() {}
      """,
    )

    assertPreviewDisplayNames("MyComponent:MyPreview", "MyComponent:MyPreview")
  }

  private fun addFile(
    name: String,
    content: String,
    relativePath: String = "com/example",
  ): PsiFile {
    return projectRule.fixture.addFileToProject(
      "src/$relativePath/$name",
      """
      package ${relativePath.replace('/', '.')}

      $content
      """
        .trimIndent(),
    )
  }

  private suspend fun assertPreviewDisplayNames(vararg expected: String) {
    val provider = projectRule.project.service<PreviewDefinitionProvider>()
    val previews = provider.previews.first { it.values.flatten().size == expected.size }
    val previewNames = previews.values.flatten().map { it.displayName }
    assertThat(previewNames).containsExactlyElementsIn(expected.toList())
  }

  private suspend fun assertNoPreviewsFound() {
    val provider = projectRule.project.service<PreviewDefinitionProvider>()
    val previews = provider.previews.first { it.isEmpty() }
    assertThat(previews).isEmpty()
  }
}
