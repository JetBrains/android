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
package com.android.tools.idea.insights.ai.codecontext

import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.Blames
import com.android.tools.idea.insights.Caption
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ExceptionStack
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.Stacktrace
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.ai.FakeGeminiPluginApi
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

// lines 14, characters 344
private val ANDROID_LIBRARY_CLASS_CONTENT =
  """
   package com.example.mylibrary;

   public class AndroidLibraryClass {
       private static int count = 0;
       public static void createCrashInAndroidLibrary()  {
           if (count == 1) {
               throw new ExceptionInInitializerError ("Android Library crash");
           }
       }

       public static void incrementCount() {
           count ++;
       }
   }
                             """
    .trimIndent()

// lines 7, characters 174
private val MAIN_ACTIVITY_CONTENT =
  """
  package com.example.myapp

  class MainActivity : ComponentActivity() {
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
     }
  }
"""
    .trimIndent()
// lines 18, characters 527
private val PARTIAL_ACTIVITY_CONTENT =
  """
  package com.example.myapp

  class PartialActivity : ComponentActivity() {
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         enableEdgeToEdge()
         setContent {
             MyApplicationTheme {
                 Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                     Greeting(
                         name = "Android",
                         modifier = Modifier.padding(innerPadding)
                     )
                 }
             }
         }
     }
  }
"""
    .trimIndent()
// lines 14, characters 264
private val CIRCLE_ACTIVITY_CONTENT =
  """
  package com.example.myapp

  class CircleActivity : ComponentActivity() {
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         Foo().bar()
     }
  }

  private class Foo {
     fun bar() {
        // Do something fun
     }
  }
"""
    .trimIndent()

private val EXCLUDED_ACTIVITY_CONTENT =
  """
  package com.example.myapp

  class ExcludedActivity : ComponentActivity() {
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
     }
  }
"""
    .trimIndent()

private val STACKTRACE =
  StacktraceGroup(
    listOf(
      ExceptionStack(
        stacktrace =
          Stacktrace(
            caption =
              Caption(
                title = "javax.net.ssl.SSLHandshakeException",
                subtitle = "Trust anchor for certification path not found.",
              ),
            blames = Blames.NOT_BLAMED,
            frames =
              listOf(
                Frame(
                  line = 362,
                  file = "SSLUtils.java",
                  rawSymbol =
                    "com.android.org.conscrypt.SSLUtils.toSSLHandshakeException(SSLUtils.java:362)",
                  symbol = "com.android.org.conscrypt.SSLUtils.toSSLHandshakeException",
                  offset = 23,
                  address = 0,
                  library = "dev.firebase.appdistribution.debug",
                  blame = Blames.NOT_BLAMED,
                ),
                Frame(
                  line = 5,
                  file = "ExcludedActivity.kt",
                  rawSymbol = "com.example.myapp.ExcludedActivity.onCreate(ExcludedActivity.kt:5)",
                  symbol = "com.example.myapp.ExcludedActivity.onCreate",
                  offset = 23,
                  address = 0,
                  library = "dev.firebase.appdistribution.debug",
                  blame = Blames.NOT_BLAMED,
                ),
                Frame(
                  line = 7,
                  file = "AndroidLibraryClass.kt",
                  rawSymbol =
                    "com.example.mylibrary.AndroidLibraryClass.createCrashInAndroidLibrary(AndroidLibraryClass.kt:7)",
                  symbol = "com.example.mylibrary.AndroidLibraryClass.createCrashInAndroidLibrary",
                  offset = 31,
                  address = 0,
                  library = "com.example.mylibrary.debug",
                  blame = Blames.NOT_BLAMED,
                ),
                Frame(
                  line = 5,
                  file = "PartialActivity.kt",
                  rawSymbol = "com.example.myapp.PartialActivity.onCreate(PartialActivity.kt:5)",
                  symbol = "com.example.myapp.PartialActivity.onCreate",
                  offset = 31,
                  address = 0,
                  library = "com.example.myapp.debug",
                  blame = Blames.NOT_BLAMED,
                ),
                Frame(
                  line = 6,
                  file = "CircleActivity.kt",
                  rawSymbol = "com.example.myapp.CircleActivity.onCreate(CircleActivity.kt:6)",
                  symbol = "com.example.myapp.CircleActivity.onCreate",
                  offset = 31,
                  address = 0,
                  library = "com.example.mylibrary.debug",
                  blame = Blames.NOT_BLAMED,
                ),
                Frame(
                  line = 5,
                  file = "CircleActivity.kt",
                  rawSymbol = "com.example.myapp.CircleActivity.Foo.bar(CircleActivity.kt:12)",
                  symbol = "com.example.myapp.CircleActivity.Foo.bar",
                  offset = 31,
                  address = 0,
                  library = "com.example.mylibrary.debug",
                  blame = Blames.NOT_BLAMED,
                ),
              ),
          ),
        type = "javax.net.ssl.SSLHandshakeException",
        exceptionMessage = "Trust anchor for certification path not found ",
        rawExceptionMessage =
          "javax.net.ssl.SSLHandshakeException: Trust anchor for certification path not found ",
      ),
      ExceptionStack(
        stacktrace =
          Stacktrace(
            caption =
              Caption(
                title = "java.security.cert.CertPathValidatorException",
                subtitle = "Trust anchor for certification path not found.",
              ),
            blames = Blames.BLAMED,
            frames =
              listOf(
                Frame(
                  line = 44,
                  file = "MainActivity.kt",
                  rawSymbol = "com.example.myapp.MainActivity.onCreate(MainActivity.kt:44)",
                  symbol = "com.example.myapp.MainActivity.onCreate",
                  offset = 23,
                  address = 0,
                  library = "com.example.myapp.debug",
                  blame = Blames.NOT_BLAMED,
                ),
                Frame(
                  line = 320,
                  file = "RealConnection.java",
                  rawSymbol =
                    "okhttp3.internal.connection.RealConnection.connectTls(RealConnection.java:320)",
                  symbol = "okhttp3.internal.connection.RealConnection.connectTls",
                  offset = 31,
                  address = 0,
                  library = "dev.firebase.appdistribution.debug",
                  blame = Blames.BLAMED,
                ),
                Frame(
                  line = 7,
                  file = "AndroidLibraryClass.kt",
                  rawSymbol =
                    "com.example.mylibrary.AndroidLibraryClass.createCrashInAndroidLibrary(AndroidLibraryClass.kt:7)",
                  symbol = "com.example.mylibrary.AndroidLibraryClass.createCrashInAndroidLibrary",
                  offset = 31,
                  address = 0,
                  library = "com.example.mylibrary.debug",
                  blame = Blames.NOT_BLAMED,
                ),
              ),
          ),
        type = "javax.net.ssl.SSLHandshakeException",
        exceptionMessage = "Trust anchor for certification path not found ",
        rawExceptionMessage =
          "Caused by: javax.net.ssl.SSLHandshakeException: Trust anchor for certification path not found ",
      ),
    )
  )

private val EXPECTED_ANDROID_LIBRARY_CLASS_CONTEXT =
  CodeContext(
    "/src/src/com/example/mylibrary/AndroidLibraryClass.kt",
    ANDROID_LIBRARY_CLASS_CONTENT,
  )
private val EXPECTED_MAIN_ACTIVITY_CONTEXT =
  CodeContext("/src/src/com/example/myapp/MainActivity.kt", MAIN_ACTIVITY_CONTENT)
private val EXPECTED_PARTIAL_ACTIVITY_CONTEXT =
  CodeContext("/src/src/com/example/myapp/PartialActivity.kt", PARTIAL_ACTIVITY_CONTENT)
private val EXPECTED_CIRCLE_ACTIVITY_CONTEXT =
  CodeContext("/src/src/com/example/myapp/CircleActivity.kt", CIRCLE_ACTIVITY_CONTENT)
private val EXCLUDED_ACTIVITY_CONTEXT =
  CodeContext("/src/src/com/example/myapp/ExcludedActivity.kt", EXCLUDED_ACTIVITY_CONTENT)

private val EXPECTED_CONTEXT =
  CodeContextData(
    listOf(
      EXPECTED_ANDROID_LIBRARY_CLASS_CONTEXT,
      EXPECTED_PARTIAL_ACTIVITY_CONTEXT,
      EXPECTED_CIRCLE_ACTIVITY_CONTEXT,
      EXPECTED_MAIN_ACTIVITY_CONTEXT,
    ),
    ContextSharingState.ALLOWED,
  )

class CodeContextResolverTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi

  @Before
  fun setUp() {
    fixture.addFileToProject(
      "src/com/example/mylibrary/AndroidLibraryClass.kt",
      ANDROID_LIBRARY_CLASS_CONTENT,
    )
    fixture.addFileToProject("src/com/example/myapp/MainActivity.kt", MAIN_ACTIVITY_CONTENT)
    fixture.addFileToProject("src/com/example/myapp/PartialActivity.kt", PARTIAL_ACTIVITY_CONTENT)
    fixture.addFileToProject("src/com/example/myapp/CircleActivity.kt", CIRCLE_ACTIVITY_CONTENT)
    fixture.addFileToProject("src/com/example/myapp/ExcludedActivity.kt", EXCLUDED_ACTIVITY_CONTENT)

    fakeGeminiPluginApi = FakeGeminiPluginApi()
    fakeGeminiPluginApi.excludedFilePaths = setOf(EXCLUDED_ACTIVITY_CONTEXT.filePath)
    ExtensionTestUtil.maskExtensions(
      GeminiPluginApi.EP_NAME,
      listOf(fakeGeminiPluginApi),
      projectRule.testRootDisposable,
    )
  }

  @Test
  fun `resolve code context`() = runBlocking {
    val resolver = CodeContextResolverImpl(projectRule.project)
    val conn = mock<Connection>().apply { doReturn(true).whenever(this).isMatchingProject() }
    val contexts = resolver.getSource(conn, STACKTRACE)

    assertThat(contexts).isEqualTo(EXPECTED_CONTEXT)
  }

  @Test
  fun `resolve code context returns empty list when Connection does not match project`() =
    runBlocking {
      val resolver = CodeContextResolverImpl(projectRule.project)
      val contexts = resolver.getSource(mock(), STACKTRACE)

      assertThat(contexts.codeContext).isEmpty()
    }

  @Test
  fun `resolve context from file names`() = runBlocking {
    val resolver = CodeContextResolverImpl(projectRule.project)
    val contextData =
      resolver.getSource(
        listOf(
          "/src/src/com/example/mylibrary/AndroidLibraryClass.kt",
          "/src/src/com/example/myapp/PartialActivity.kt",
          "/src/src/com/example/myapp/CircleActivity.kt",
          "/src/src/com/example/myapp/ExcludedActivity.kt",
          "/src/src/com/example/myapp/MainActivity.kt",
        )
      )

    assertThat(contextData).isEqualTo(EXPECTED_CONTEXT)
  }

  @Test
  fun `resolver returns empty context data when context sharing is off`() = runBlocking {
    val resolver = CodeContextResolverImpl(projectRule.project)

    fakeGeminiPluginApi.contextAllowed = false
    assertThat(resolver.getSource(mock(), STACKTRACE))
      .isEqualTo(CodeContextData(emptyList(), contextSharingState = ContextSharingState.DISABLED))
  }

  @Test
  fun `resolver returns empty context data when connection is mismatched`() = runBlocking {
    val connection = mock<Connection>()
    `when`(connection.isMatchingProject()).thenReturn(false)
    val resolver = CodeContextResolverImpl(projectRule.project)
    assertThat(resolver.getSource(connection, STACKTRACE))
      .isEqualTo(CodeContextData(emptyList(), contextSharingState = ContextSharingState.ALLOWED))
  }
}
