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
package com.android.tools.idea.gradle.service.resolve

import com.android.tools.idea.flags.DeclarativeStudioSupport
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class NamespacePsiPackageReferenceProviderTest {
  @get:Rule val rule = AndroidProjectRule.withAndroidModel(AndroidProjectBuilder()).onEdt()

  val fixture by lazy { rule.fixture as JavaCodeInsightTestFixture }

  @Before
  fun setup() {
    fixture.addFileToProject(
      "src/main/java/com/example/myapplication/MyActivity.java",
      """
        package com.example.myapplication;

        import androidx.activity.ComponentActivity;

        class MyActivity extends ComponentActivity {
        }
      """.trimIndent())
    fixture.addFileToProject(
      "src/androidTest/java/com/example/myapplication/MyExampleInstrumentedTest.java",
      """
        package com.example.myapplication;

        class MyExampleInstrumentedTest {
        }
      """.trimIndent())
  }

  @Test
  fun testGroovyNamespaceAssignment() {
    val file = fixture.addFileToProject(
      "build.gradle",
      """
        android {
          namespace = "com.example.myapplication"
        }
      """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    doChecks()
  }

  @Test
  fun testGroovyTestNamespaceAssignment() {
    val file = fixture.addFileToProject(
      "build.gradle",
      """
        android {
          testNamespace = "com.example.myapplication.test"
        }
      """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    doChecks()
  }

  @Test
  fun testGroovyApplicationIdAssignment() {
    val file = fixture.addFileToProject(
      "build.gradle",
      """
        android {
          defaultConfig {
            applicationId = "com.example.myapplication"
          }
        }
      """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    doChecks(false)
  }

  @Test
  fun testGroovyNamespaceApplication() {
    val file = fixture.addFileToProject(
      "build.gradle",
      """
        android {
          namespace "com.example.myapplication"
        }
      """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    doChecks()
  }

  @Test
  fun testGroovyTestNamespaceApplication() {
    val file = fixture.addFileToProject(
      "build.gradle",
      """
        android {
          testNamespace "com.example.myapplication.test"
        }
      """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    doChecks()
  }

  @Test
  fun testGroovyApplicationIdApplication() {
    val file = fixture.addFileToProject(
      "build.gradle",
      """
        android {
          defaultConfig {
            applicationId "com.example.myapplication"
          }
        }
      """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    doChecks(false)
  }

  @Test
  fun testKotlinScriptNamespace() {
    val file = fixture.addFileToProject(
      "build.gradle.kts",
      """
        android {
          namespace = "com.example.myapplication"
        }
      """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    doChecks()
  }

  @Test
  fun testKotlinScriptTestNamespace() {
    val file = fixture.addFileToProject(
      "build.gradle.kts",
      """
        android {
          testNamespace = "com.example.myapplication.test"
        }
      """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    doChecks()
  }

  @Test
  fun testKotlinScriptApplicationId() {
    val file = fixture.addFileToProject(
      "build.gradle.kts",
      """
        android {
          defaultConfig {
            applicationId = "com.example.myapplication"
          }
        }
      """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    doChecks(false)
  }

  @Test
  fun testDeclarativeNamespace() {
    try {
      DeclarativeStudioSupport.override(true)
      val file = fixture.addFileToProject(
        "build.gradle.dcl",
        """
        android {
          namespace = "com.example.myapplication"
        }
      """.trimIndent())
      fixture.configureFromExistingVirtualFile(file.virtualFile)
      doChecks()
    }
    finally {
      DeclarativeStudioSupport.clearOverride()
    }
  }

  @Test
  fun testDeclarativeTestNamespace() {
    try {
      DeclarativeStudioSupport.override(true)
      val file = fixture.addFileToProject(
        "build.gradle.dcl",
        """
        android {
          testNamespace = "com.example.myapplication.test"
        }
      """.trimIndent())
      fixture.configureFromExistingVirtualFile(file.virtualFile)
      doChecks()
    }
    finally {
      DeclarativeStudioSupport.clearOverride()
    }
  }

  @Test
  fun testDeclarativeApplicationId() {
    try {
      DeclarativeStudioSupport.override(true)
      val file = fixture.addFileToProject(
        "build.gradle.dcl",
        """
        android {
          defaultConfig {
            applicationId = "com.example.myapplication"
          }
        }
      """.trimIndent())
      fixture.configureFromExistingVirtualFile(file.virtualFile)
      doChecks(false)
    }
    finally {
      DeclarativeStudioSupport.clearOverride()
    }
  }

  private fun doChecks(expectPackage: Boolean = true) {
    fixture.run {
      fun check(packageName: String) = when(expectPackage) {
        true -> assertThat(elementAtCaret).isEqualTo(findPackage(packageName))
        false -> assertThat(file.findReferenceAt(caretOffset)?.resolve()).isNull()
      }
      moveCaret("\"co|m.e").also { check("com") }
      moveCaret("ex|ample").also { check("com.example") }
      moveCaret("my|application").also { check("com.example.myapplication") }
    }
  }
}