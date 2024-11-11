/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.editing.documentation

import com.android.mockito.kotlin.getTypedArgument
import com.android.sdklib.AndroidVersion
import com.android.testutils.TestUtils
import com.android.tools.idea.downloads.UrlFileCache
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Sdks
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.Language
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.documentation.AsyncDocumentation
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiClass
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

private const val TEST_DATA_DIR =
  "tools/adt/idea/android/editing/documentation/testData/androidSdkDocumentationTargetProvider"
private const val ACTIVITY_DOC_URL =
  "http://developer.android.com/reference/android/app/Activity.html"

@RunWith(Parameterized::class)
class AndroidSdkDocumentationTargetProviderTest(private val language: Language) {

  @get:Rule val projectRule = AndroidProjectRule.withSdk(AndroidVersion(34))

  private val fixture by lazy { projectRule.fixture }
  private val project by lazy { projectRule.project }

  private val documentationContentFromServer by lazy {
    Files.readString(TestUtils.resolveWorkspacePath("$TEST_DATA_DIR/BitmapShader.html"))
  }

  private val mockUrlFileCache: UrlFileCache = mock {
    on { get(eq(ACTIVITY_DOC_URL), any(), isNull(), any()) } doAnswer
      { invocationOnMock ->
        val fileToReturn = File(fixture.tempDirPath, "serverContent.tmp")
        if (!fileToReturn.exists()) {
          ByteArrayInputStream(documentationContentFromServer.toByteArray()).use {
            serverContentStream ->
            val filter: (InputStream) -> InputStream = invocationOnMock.getTypedArgument(3)
            filter.invoke(serverContentStream).use { filteredStream ->
              FileOutputStream(fileToReturn).use { it.write(filteredStream.readAllBytes()) }
            }
          }
        }
        fileToReturn.toPath()
      }
  }

  @Before
  fun setUp() {
    project.replaceService(UrlFileCache::class.java, mockUrlFileCache, fixture.testRootDisposable)
  }

  @Test
  fun checkDocumentation() {
    val documentationContentAfterFiltering =
      Files.readString(TestUtils.resolveWorkspacePath("$TEST_DATA_DIR/BitmapShader.Rendered.html"))

    setUpCursorForActivity()
    val doc = getDocsAtCursor().single()

    val documentation = runReadAction { doc.computeDocumentation() }
    assertThat(documentation).isInstanceOf(AsyncDocumentation::class.java)

    val documentationData = runBlocking { (documentation as AsyncDocumentation).supplier() }
    assertThat(documentationData).isInstanceOf(DocumentationData::class.java)
    assertThat((documentationData as DocumentationData).html)
      .isEqualTo(documentationContentAfterFiltering)
  }

  @Test
  fun checkDocumentationWhenServerUnavailable() {
    whenever(mockUrlFileCache.get(eq(ACTIVITY_DOC_URL), any(), isNull(), any())).then {
      throw IOException()
    }

    setUpCursorForActivity()
    val doc = getDocsAtCursor().single()

    val documentation = runReadAction { doc.computeDocumentation() }
    assertThat(documentation).isInstanceOf(AsyncDocumentation::class.java)

    val documentationData = runBlocking { (documentation as AsyncDocumentation).supplier() }
    assertThat(documentationData).isInstanceOf(DocumentationData::class.java)
    val html = (documentationData as DocumentationData).html

    assertThat(html).contains("android.app")
    assertThat(html).contains("public")
    assertThat(html).contains("class")
    assertThat(html).contains("Activity")
  }

  @Test
  fun checkDocumentationHint() {
    setUpCursorForActivity()
    val doc = getDocsAtCursor().single()

    val documentationHint = runReadAction { doc.computeDocumentationHint() }
    assertThat(documentationHint).contains("android.app")
    assertThat(documentationHint).contains("public")
    assertThat(documentationHint).contains("class")
    assertThat(documentationHint).contains("Activity")

    verifyNoInteractions(mockUrlFileCache)
  }

  @Test
  fun checkNavigable() {
    setUpCursorForActivity()
    val doc = getDocsAtCursor().single()

    val navigatable = doc.navigatable
    assertThat(navigatable).isInstanceOf(PsiClass::class.java)
    assertThat((navigatable as PsiClass).qualifiedName).isEqualTo("android.app.Activity")
  }

  @Test
  fun pointerCreatesEquivalentDoc() {
    setUpCursorForActivity()
    val doc = getDocsAtCursor().single()

    val docFromPointer = runReadAction { doc.createPointer().dereference() }

    requireNotNull(docFromPointer)
    assertThat(docFromPointer.navigatable).isEqualTo(doc.navigatable)
    assertThat(docFromPointer.javaClass).isEqualTo(doc.javaClass)
  }

  @Test
  fun noRemoteDocumentationWhenLocalSourcesArePresent() {
    setUpCursorForActivity()
    val docWithNoSources = getDocsAtCursor().single()

    // SDK 34 was already added by the project rule above. This call will not add another version,
    // and instead will just return the one already in use.
    val sdk = Sdks.addAndroidSdk(fixture.testRootDisposable, fixture.module, AndroidVersion(34))

    // The test version of SDK 34 does not have sources at the time of the writing of this test. If
    // they are added, then we may need to flip this logic around; namely, the general test setup
    // would need to remove sources so that the provider in question runs, and this specific test
    // would put the sources back.
    val testDataSourceRoot =
      LocalFileSystem.getInstance()
        .findFileByPath(TestUtils.resolveWorkspacePath("$TEST_DATA_DIR/androidSources").toString())
    requireNotNull(testDataSourceRoot)
    sdk.sdkModificator.apply {
      addRoot(testDataSourceRoot, OrderRootType.SOURCES)
      application.invokeAndWait { runWriteAction { commitChanges() } }
    }

    IndexingTestUtil.waitUntilIndexesAreReady(project)

    val docWithSources = getDocsAtCursor().single()

    assertThat(docWithSources.javaClass).isNotEqualTo(docWithNoSources.javaClass)
  }

  private fun setUpCursorForActivity() {
    val psiFile =
      when (language) {
        JavaLanguage.INSTANCE ->
          fixture.addFileToProject(
            "src/MyJavaActivity.java",
            // language=Java
            """
            package com.example;

            import android.app.Acti<caret>vity;
            import android.os.Bundle;

            public class MyJavaActivity extends Activity {
              @Override
              public void onCreate(Bundle savedInstanceState) {}
            }
            """
              .trimIndent(),
          )
        KotlinLanguage.INSTANCE ->
          fixture.addFileToProject(
            "src/MyKotlinActivity.kt",
            // language=kotlin
            """
            package com.example

            import android.app.Acti<caret>vity
            import android.os.Bundle

            class MyKotlinActivity : Activity() {
              override fun onCreate(savedInstanceState: Bundle?) {}
            }
            """
              .trimIndent(),
          )
        else -> throw IllegalArgumentException("Unrecognized: $language")
      }
    fixture.configureFromExistingVirtualFile(psiFile.virtualFile)
  }

  private fun getDocsAtCursor(): List<DocumentationTarget> {
    return runReadAction {
      IdeDocumentationTargetProvider.getInstance(project)
        .documentationTargets(fixture.editor, fixture.file, fixture.caretOffset)
    }
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): List<Language> = listOf(JavaLanguage.INSTANCE, KotlinLanguage.INSTANCE)
  }
}
