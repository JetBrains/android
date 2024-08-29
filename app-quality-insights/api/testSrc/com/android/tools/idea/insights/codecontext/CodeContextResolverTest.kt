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
package com.android.tools.idea.insights.codecontext

import com.android.tools.idea.insights.Blames
import com.android.tools.idea.insights.Caption
import com.android.tools.idea.insights.ExceptionStack
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.Stacktrace
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.analytics.AppInsightsExperimentFetcher
import com.android.tools.idea.serverflags.protos.ExperimentType
import com.android.tools.idea.studiobot.AiExcludeService
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.nio.file.Path
import kotlin.io.path.name
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

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
class CodeContextResolverTest(private val experimentType: ExperimentType) {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val modes = ExperimentType.entries
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

    projectRule.replaceService(
      AppInsightsExperimentFetcher::class.java,
      createTestExperimentFetcher(experimentType),
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
    projectRule.replaceService(
      StudioBot::class.java,
      object : StudioBot.StubStudioBot() {
        override fun isContextAllowed(project: Project) = true

        override fun aiExcludeService(project: Project) =
          object : AiExcludeService {
            override fun isFileExcluded(file: VirtualFile) =
              file.path == EXCLUDED_ACTIVITY_CONTEXT.filePath

            override fun isFileExcluded(file: Path): Boolean {
              TODO("Not yet implemented")
            }

            override fun getExclusionStatus(file: VirtualFile): AiExcludeService.ExclusionStatus {
              TODO("Not yet implemented")
            }

            override fun getExclusionStatus(file: Path): AiExcludeService.ExclusionStatus {
              TODO("Not yet implemented")
            }

            override fun getBlockingFiles(file: VirtualFile): List<VirtualFile> {
              TODO("Not yet implemented")
            }

            override fun getBlockingFiles(file: Path): List<VirtualFile> {
              TODO("Not yet implemented")
            }
          }
      },
    )

    val resolver = CodeContextResolverImpl(projectRule.project)
    val contexts = resolver.getSource(STACKTRACE)

    val expected: List<CodeContext> =
      when (experimentType) {
        ExperimentType.EXPERIMENT_TYPE_UNSPECIFIED,
        ExperimentType.CONTROL -> emptyList()
        ExperimentType.TOP_SOURCE -> listOf(EXPECTED_ANDROID_LIBRARY_CLASS_CONTEXT)
        ExperimentType.TOP_THREE_SOURCES ->
          listOf(
            EXPECTED_ANDROID_LIBRARY_CLASS_CONTEXT,
            EXPECTED_PARTIAL_ACTIVITY_CONTEXT,
            EXPECTED_CIRCLE_ACTIVITY_CONTEXT,
          )
        ExperimentType.ALL_SOURCES ->
          listOf(
            EXPECTED_ANDROID_LIBRARY_CLASS_CONTEXT,
            EXPECTED_PARTIAL_ACTIVITY_CONTEXT,
            EXPECTED_CIRCLE_ACTIVITY_CONTEXT,
            EXPECTED_MAIN_ACTIVITY_CONTEXT,
          )
      }

    assertThat(contexts).isEqualTo(expected)
  }

  @Test
  fun `resolve code context when project does not allow context`() = runBlocking {
    projectRule.replaceService(
      StudioBot::class.java,
      object : StudioBot.StubStudioBot() {
        override fun isContextAllowed(project: Project) = false
      },
    )

    val resolver = CodeContextResolverImpl(projectRule.project)
    val contexts = resolver.getSource(STACKTRACE)

    assertThat(contexts).isEmpty()
  }

  private fun createTestExperimentFetcher(experimentType: ExperimentType) =
    object : AppInsightsExperimentFetcher {
      override fun getCurrentExperiment() = experimentType
    }
}
