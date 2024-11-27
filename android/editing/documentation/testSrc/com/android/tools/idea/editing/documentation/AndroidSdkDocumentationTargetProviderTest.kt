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

import com.android.sdklib.AndroidVersion
import com.android.testutils.TestUtils
import com.android.tools.idea.downloads.UrlFileCache
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Sdks
import com.android.tools.idea.testing.moveCaret
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
import com.intellij.platform.backend.documentation.DocumentationResult.Documentation
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

private const val TEST_DATA_DIR =
  "tools/adt/idea/android/editing/documentation/testData/androidSdkDocumentationTargetProvider"
private const val TEXT_VIEW_DOC_URL =
  "http://developer.android.com/reference/android/widget/TextView.html"
// The contents here don't really matter.
private const val SIMPLE_HTML = "<html><body>Yo, this is HTML.</body></html>"

private fun CharSequence.collapseSpaces() =
  replace(Regex(" {2,}"), " ").replace(Regex("^ +", RegexOption.MULTILINE), "")

@RunWith(Parameterized::class)
class AndroidSdkDocumentationTargetProviderTest(private val testConfig: TestConfig) {
  @get:Rule val projectRule = AndroidProjectRule.withSdk(AndroidVersion(34))

  private val fixture by lazy { projectRule.fixture }
  private val project by lazy { projectRule.project }

  private val simpleHtmlPath by lazy { fixture.createFile("simple.html", SIMPLE_HTML).toNioPath() }

  private val docUrl = TEXT_VIEW_DOC_URL + testConfig.urlSuffix

  private val preFilteringPath by lazy {
    TestUtils.resolveWorkspacePath("$TEST_DATA_DIR/TextView.html")
  }

  private val postFilteringPath by lazy {
    TestUtils.resolveWorkspacePath(
      "$TEST_DATA_DIR/TextView.${testConfig.targetType.simpleName}.Rendered.html"
    )
  }

  private val documentationContentAfterFiltering by lazy {
    Files.readString(postFilteringPath).collapseSpaces()
  }

  private val transformCaptor = argumentCaptor<(InputStream) -> InputStream>()

  private val mockUrlFileCache: UrlFileCache = mock()

  @Before
  fun setUp() {
    project.replaceService(UrlFileCache::class.java, mockUrlFileCache, fixture.testRootDisposable)
  }

  @Test
  fun checkDocumentation_fast() {
    whenever(mockUrlFileCache.get(eq(docUrl), any(), isNull(), any()))
      .thenReturn(
        // This one is already completed.
        CompletableDeferred(simpleHtmlPath)
      )

    setUpCursor()
    val doc = getDocsAtCursor().single()

    val documentation = runReadAction { doc.computeDocumentation() }
    assertThat(documentation).isInstanceOf(Documentation::class.java)

    val documentationData = runBlocking { (documentation as Documentation) }
    assertThat(documentationData).isInstanceOf(DocumentationData::class.java)
    assertThat((documentationData as DocumentationData).html).isEqualTo(SIMPLE_HTML)

    // Independently check that the passed-in filter is doing the right thing.
    @Suppress("DeferredResultUnused")
    verify(mockUrlFileCache).get(eq(docUrl), any(), isNull(), transformCaptor.capture())

    val filterOutput =
      FileInputStream(preFilteringPath.toFile())
        .use { inputStream ->
          String(transformCaptor.firstValue.invoke(inputStream).readAllBytes())
        }
        .collapseSpaces()

    assertThat(filterOutput).isEqualTo(documentationContentAfterFiltering)
  }

  @Test
  fun checkDocumentation_slow() {
    val completableDeferred = CompletableDeferred<Path>()
    whenever(mockUrlFileCache.get(eq(docUrl), any(), isNull(), any()))
      .thenReturn(completableDeferred)

    setUpCursor()
    val doc = getDocsAtCursor().single()

    val documentation = runReadAction { doc.computeDocumentation() }
    assertThat(documentation).isInstanceOf(AsyncDocumentation::class.java)

    // Actually complete the Deferred so we can get the result.
    completableDeferred.complete(simpleHtmlPath)

    val documentationData = runBlocking { (documentation as AsyncDocumentation).supplier() }
    assertThat(documentationData).isInstanceOf(DocumentationData::class.java)
    assertThat((documentationData as DocumentationData).html).isEqualTo(SIMPLE_HTML)

    // Independently check that the passed-in filter is doing the right thing.
    @Suppress("DeferredResultUnused")
    verify(mockUrlFileCache).get(eq(docUrl), any(), isNull(), transformCaptor.capture())

    val filterOutput =
      FileInputStream(preFilteringPath.toFile())
        .use { inputStream ->
          String(transformCaptor.firstValue.invoke(inputStream).readAllBytes())
        }
        .collapseSpaces()
    assertThat(filterOutput).isEqualTo(documentationContentAfterFiltering)
  }

  @Test
  fun checkDocumentationWhenServerUnavailable() {
    val completableDeferred = CompletableDeferred<Nothing>()
    whenever(mockUrlFileCache.get(eq(docUrl), any(), isNull(), any()))
      .thenReturn(completableDeferred)

    setUpCursor()
    val doc = getDocsAtCursor().single()

    val documentation = runReadAction { doc.computeDocumentation() }
    assertThat(documentation).isInstanceOf(AsyncDocumentation::class.java)

    completableDeferred.completeExceptionally(IOException())

    val documentationData = runBlocking { (documentation as AsyncDocumentation).supplier() }
    assertThat(documentationData).isInstanceOf(DocumentationData::class.java)
    val html = (documentationData as DocumentationData).html

    assertThat(html).contains("android.widget")
    assertThat(html).contains("public")
    assertThat(html).contains("class")
    assertThat(html).contains("TextView")
  }

  @Test
  fun checkDocumentationHint() {
    setUpCursor()
    val doc = getDocsAtCursor().single()

    val documentationHint = runReadAction { doc.computeDocumentationHint() }
    for (hintString in testConfig.hintStrings) {
      assertThat(documentationHint).contains(hintString)
    }

    verifyNoInteractions(mockUrlFileCache)
  }

  @Test
  fun checkNavigable() {
    setUpCursor()
    val doc = getDocsAtCursor().single()

    val navigatable = doc.navigatable
    assertThat(navigatable).isInstanceOf(testConfig.targetType.java)
    when (navigatable) {
      is PsiClass -> assertThat(navigatable.qualifiedName).isEqualTo("android.widget.TextView")
      is PsiField -> {
        assertThat(navigatable.containingClass?.qualifiedName).isEqualTo("android.widget.TextView")
        assertThat(navigatable.name).isEqualTo("AUTO_SIZE_TEXT_TYPE_NONE")
      }
      is PsiMethod -> {
        assertThat(navigatable.containingClass?.qualifiedName).isEqualTo("android.widget.TextView")
        assertThat(navigatable.name).isEqualTo("addTextChangedListener")
      }
      else -> fail("Unexpected type: ${testConfig.targetType}")
    }
  }

  @Test
  fun pointerCreatesEquivalentDoc() {
    setUpCursor()
    val doc = getDocsAtCursor().single()

    val docFromPointer = runReadAction { doc.createPointer().dereference() }

    requireNotNull(docFromPointer)
    assertThat(docFromPointer.navigatable).isEqualTo(doc.navigatable)
    assertThat(docFromPointer.javaClass).isEqualTo(doc.javaClass)
  }

  @Test
  fun noRemoteDocumentationWhenLocalSourcesArePresent() {
    whenever(mockUrlFileCache.get(eq(docUrl), any(), isNull(), any()))
      .thenReturn(CompletableDeferred(postFilteringPath))

    setUpCursor()
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

  private fun setUpCursor() {
    val psiFile =
      when (testConfig.language) {
        JavaLanguage.INSTANCE ->
          fixture.addFileToProject(
            "src/com/example/MyGreatClass.java",
            // language=Java
            """
            package com.example;

            import android.text.TextWatcher;
            import android.widget.TextView;

            public class MyGreatClass {
              public void foo(TextView textView, TextWatcher textWatcher) {
                int bar = TextView.AUTO_SIZE_TEXT_TYPE_NONE;
                textView.addTextChangedListener(textWatcher);
              }
            }
            """
              .trimIndent(),
          )
        KotlinLanguage.INSTANCE ->
          fixture.addFileToProject(
            "src/com/example/MyGreatClass.kt",
            // language=kotlin
            """
            package com.example

            import android.text.TextWatcher
            import android.widget.TextView

            class MyGreatClass {
              fun foo(textView: TextView, textWatcher: TextWatcher) {
                val bar = TextView.AUTO_SIZE_TEXT_TYPE_NONE
                textView.addTextChangedListener(textWatcher)
              }
            }
            """
              .trimIndent(),
          )
        else -> throw IllegalArgumentException("Unrecognized: ${testConfig.language}")
      }
    fixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    application.invokeAndWait { fixture.moveCaret(testConfig.cursorWindow) }
  }

  private fun getDocsAtCursor(): List<DocumentationTarget> {
    return runReadAction {
      IdeDocumentationTargetProvider.getInstance(project)
        .documentationTargets(fixture.editor, fixture.file, fixture.caretOffset)
    }
  }

  data class TestConfig(
    val language: Language,
    val targetType: KClass<*>,
    val urlSuffix: String,
    val cursorWindow: String,
    val hintStrings: List<String>,
  ) {
    override fun toString() = "${language.displayName} ${targetType.simpleName}"
  }

  companion object {
    private val JAVA_CONFIGS =
      listOf(
        TestConfig(
          JavaLanguage.INSTANCE,
          PsiClass::class,
          urlSuffix = "",
          cursorWindow = "Text|View.",
          hintStrings = listOf("android.widget", "public", "class", "TextView"),
        ),
        TestConfig(
          JavaLanguage.INSTANCE,
          PsiField::class,
          urlSuffix = "#AUTO_SIZE_TEXT_TYPE_NONE",
          cursorWindow = "AUTO_SIZE_TE|XT_TYPE_NONE",
          hintStrings = listOf("android.widget.TextView", "AUTO_SIZE_TEXT_TYPE_NONE", "int"),
        ),
        TestConfig(
          JavaLanguage.INSTANCE,
          PsiMethod::class,
          urlSuffix = "#addTextChangedListener(android.text.TextWatcher)",
          cursorWindow = "addText|ChangedListener",
          hintStrings =
            listOf("android.widget.TextView", "addTextChangedListener", "TextWatcher", "void"),
        ),
      )

    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): List<TestConfig> =
      JAVA_CONFIGS + JAVA_CONFIGS.map { it.copy(language = KotlinLanguage.INSTANCE) }
  }
}
