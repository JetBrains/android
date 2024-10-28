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
import com.android.tools.idea.insights.ExceptionStack
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.Stacktrace
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.ai.FakeGeminiPluginApi
import com.android.tools.idea.insights.experiments.AppInsightsExperimentFetcher
import com.android.tools.idea.insights.experiments.Experiment
import com.android.tools.idea.insights.experiments.ExperimentGroup
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

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
// lines 7, characters 176
private val CIRCLE_ACTIVITY_CONTENT =
  """
  package com.example.myapp

  class CircleActivity : ComponentActivity() {
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
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
                  line = 5,
                  file = "CircleActivity.kt",
                  rawSymbol = "com.example.myapp.CircleActivity.onCreate(CircleActivity.kt:5)",
                  symbol = "com.example.myapp.CircleActivity.onCreate",
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
    "com.example.mylibrary.AndroidLibraryClass",
    "/src/src/com/example/mylibrary/AndroidLibraryClass.kt",
    ANDROID_LIBRARY_CLASS_CONTENT,
    Language.KOTLIN,
  )
private val EXPECTED_MAIN_ACTIVITY_CONTEXT =
  CodeContext(
    "com.example.myapp.MainActivity",
    "/src/src/com/example/myapp/MainActivity.kt",
    MAIN_ACTIVITY_CONTENT,
    Language.KOTLIN,
  )
private val EXPECTED_PARTIAL_ACTIVITY_CONTEXT =
  CodeContext(
    "com.example.myapp.PartialActivity",
    "/src/src/com/example/myapp/PartialActivity.kt",
    PARTIAL_ACTIVITY_CONTENT,
    Language.KOTLIN,
  )
private val EXPECTED_CIRCLE_ACTIVITY_CONTEXT =
  CodeContext(
    "com.example.myapp.CircleActivity",
    "/src/src/com/example/myapp/CircleActivity.kt",
    CIRCLE_ACTIVITY_CONTENT,
    Language.KOTLIN,
  )
private val EXCLUDED_ACTIVITY_CONTEXT =
  CodeContext(
    "com.example.myapp.ExcludedActivity",
    "/src/src/com/example/myapp/ExcludedActivity.kt",
    EXCLUDED_ACTIVITY_CONTENT,
    Language.KOTLIN,
  )

@RunWith(Parameterized::class)
class CodeContextResolverTest(private val experiment: Experiment) {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private lateinit var fakeGeminiPluginApi: FakeGeminiPluginApi

  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val modes = ExperimentGroup.CODE_CONTEXT.experiments

    fun expectedCodeContextTrackingInfoByExperiment(
      experiment: Experiment
    ): CodeContextTrackingInfo =
      when (experiment) {
        Experiment.UNKNOWN,
        Experiment.CONTROL -> CodeContextTrackingInfo.EMPTY
        Experiment.TOP_SOURCE -> CodeContextTrackingInfo(1, 14, 344)
        Experiment.TOP_THREE_SOURCES -> CodeContextTrackingInfo(3, 39, 1047)
        Experiment.ALL_SOURCES -> CodeContextTrackingInfo(4, 46, 1221)
      }
  }

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

    projectRule.replaceService(
      AppInsightsExperimentFetcher::class.java,
      createTestExperimentFetcher(experiment),
    )
  }

  /**
   * Tests the resolving of code context for all 4 experiments.
   *
   * In addition, it covers these scenarios:
   * 1. source file appears more than once in the stack trace
   * 2. source file does not exist in project
   * 3. source file is excluded in .aiexclude and would've otherwise been included
   */
  @Test
  fun `resolve code context based on assigned experiment`() = runBlocking {
    val resolver = CodeContextResolverImpl(projectRule.project)
    val contexts = resolver.getSource(STACKTRACE)

    val expected =
      when (experiment) {
        Experiment.UNKNOWN -> CodeContextData.UNASSIGNED
        Experiment.CONTROL -> CodeContextData.CONTROL
        Experiment.TOP_SOURCE ->
          CodeContextData(
            listOf(EXPECTED_ANDROID_LIBRARY_CLASS_CONTEXT),
            experiment,
            expectedCodeContextTrackingInfoByExperiment(experiment),
          )
        Experiment.TOP_THREE_SOURCES ->
          CodeContextData(
            listOf(
              EXPECTED_ANDROID_LIBRARY_CLASS_CONTEXT,
              EXPECTED_PARTIAL_ACTIVITY_CONTEXT,
              EXPECTED_CIRCLE_ACTIVITY_CONTEXT,
            ),
            experiment,
            expectedCodeContextTrackingInfoByExperiment(experiment),
          )
        Experiment.ALL_SOURCES ->
          CodeContextData(
            listOf(
              EXPECTED_ANDROID_LIBRARY_CLASS_CONTEXT,
              EXPECTED_PARTIAL_ACTIVITY_CONTEXT,
              EXPECTED_CIRCLE_ACTIVITY_CONTEXT,
              EXPECTED_MAIN_ACTIVITY_CONTEXT,
            ),
            experiment,
            expectedCodeContextTrackingInfoByExperiment(experiment),
          )
      }

    assertThat(contexts).isEqualTo(expected)
  }

  private fun createTestExperimentFetcher(experiment: Experiment) =
    object : AppInsightsExperimentFetcher {
      override fun getCurrentExperiment(experimentGroup: ExperimentGroup) = experiment
    }
}
