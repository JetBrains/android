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
package com.android.tools.idea.lint.common

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Project
import org.intellij.lang.annotations.Language
import org.jetbrains.android.JavaCodeInsightFixtureAdtTestCase
import org.mockito.Mockito

class LintIdeGradleVisitorTest : JavaCodeInsightFixtureAdtTestCase() {
  // Keep in sync with GroovyGradleVisitorTest#testBasic!
  fun testBasic() {
    check(
      """
      dependencies {
          implementation(platform(libs.compose.bom))
          implementation platform("androidx.compose:compose-bom:2022.12.00")
      }
      """,
      """
      checkMethodCall(statement="dependencies", unnamedArguments="{ implementation(platform(libs.compose.bom)) implementation platform("androidx.compose:compose-bom:2022.12.00") }")
      checkMethodCall(statement="implementation", parent="dependencies", unnamedArguments="platform(libs.compose.bom)")
      checkDslPropertyAssignment(property="implementation", value="platform(libs.compose.bom)", parent="dependencies")
      checkMethodCall(statement="platform", parent="dependencies", unnamedArguments="libs.compose.bom")
      checkMethodCall(statement="implementation", parent="dependencies", unnamedArguments="platform("androidx.compose:compose-bom:2022.12.00")")
      checkDslPropertyAssignment(property="implementation", value="platform("androidx.compose:compose-bom:2022.12.00")", parent="dependencies")
      checkMethodCall(statement="platform", parent="dependencies", unnamedArguments=""androidx.compose:compose-bom:2022.12.00"")
      """
    )
  }

  fun testNamedDependency() {
    check(
      """
      apply plugin: 'com.android.application'
      dependencies {
          implementation group: 'com.android.support', name: 'support-v4', version: '19.0'
      }
      """,
      """
      checkMethodCall(statement="apply", namedArguments="plugin=com.android.application")
      checkMethodCall(statement="dependencies", unnamedArguments="{ implementation group: 'com.android.support', name: 'support-v4', version: '19.0' }")
      checkMethodCall(statement="implementation", parent="dependencies", namedArguments="group=com.android.support, name=support-v4, version=19.0")
      checkDslPropertyAssignment(property="implementation", value="group: 'com.android.support', name: 'support-v4', version: '19.0'", parent="dependencies")
      """
    )
  }

  fun testFunctionsAndVariables() {
    @Suppress("GroovyUnusedAssignment", "GrMethodMayBeStatic")
    check(
      """
      apply plugin: 'com.android.application'
      android {
          compileSdkVersion 30
          def foo = 'foo'
          defaultConfig {
              def bar = 'bar'
          }
      }
      final GPS_VERSION = '5.0.77'
      def getVersionName() {
          "1.0"
      }
      dependencies {
          compile "com.google.android.gms:play-services-wearable:${"$"}{GPS_VERSION}"
      }
      """,
      """
      checkDslPropertyAssignment(property="GPS_VERSION", value="'5.0.77'", parent="")
      checkDslPropertyAssignment(property="bar", value="'bar'", parent="defaultConfig", parentParent="android")
      checkDslPropertyAssignment(property="compile", value=""com.google.android.gms:play-services-wearable:${"$"}{GPS_VERSION}"", parent="dependencies")
      checkDslPropertyAssignment(property="compileSdkVersion", value="30", parent="android")
      checkDslPropertyAssignment(property="foo", value="'foo'", parent="android")
      checkMethodCall(statement="android", unnamedArguments="{ compileSdkVersion 30 def foo = 'foo' defaultConfig { def bar = 'bar' } }")
      checkMethodCall(statement="apply", namedArguments="plugin=com.android.application")
      checkMethodCall(statement="compile", parent="dependencies", unnamedArguments=""com.google.android.gms:play-services-wearable:${"$"}{GPS_VERSION}"")
      checkMethodCall(statement="compileSdkVersion", parent="android", unnamedArguments="30")
      checkMethodCall(statement="defaultConfig", parent="android", unnamedArguments="{ def bar = 'bar' }")
      checkMethodCall(statement="dependencies", unnamedArguments="{ compile "com.google.android.gms:play-services-wearable:${"$"}{GPS_VERSION}" }")
      """
    )
  }

  fun testNesting() {
    // Make sure we treat "dependencies.x" as a property, but not y (e.g. dependencies.x.y); that
    // should only be the case for x { y { ... } }
    check(
      """
      dependencies {
          x(y(z(a(b(c("hello world"))))))
          x {
             y {
                z "hello world"
             }
          }
      }
      """,
      """
      checkMethodCall(statement="dependencies", unnamedArguments="{ x(y(z(a(b(c("hello world")))))) x { y { z "hello world" } } }")
      checkMethodCall(statement="x", parent="dependencies", unnamedArguments="y(z(a(b(c("hello world")))))")
      checkDslPropertyAssignment(property="x", value="y(z(a(b(c("hello world")))))", parent="dependencies")
      checkMethodCall(statement="y", parent="dependencies", unnamedArguments="z(a(b(c("hello world"))))")
      checkMethodCall(statement="z", parent="dependencies", unnamedArguments="a(b(c("hello world")))")
      checkMethodCall(statement="a", parent="dependencies", unnamedArguments="b(c("hello world"))")
      checkMethodCall(statement="b", parent="dependencies", unnamedArguments="c("hello world")")
      checkMethodCall(statement="c", parent="dependencies", unnamedArguments=""hello world"")
      checkMethodCall(statement="x", parent="dependencies", unnamedArguments="{ y { z "hello world" } }")
      checkMethodCall(statement="y", parent="x", parentParent="dependencies", unnamedArguments="{ z "hello world" }")
      checkMethodCall(statement="z", parent="y", parentParent="x", unnamedArguments=""hello world"")
      checkDslPropertyAssignment(property="z", value=""hello world"", parent="y", parentParent="x")
      """
    )
  }

  fun testNesting2() {
    check(
      """
      android {
          buildTypes {
              debug {
                  packageNameSuffix ".debug"
              }
          }
      }
      """,
      """
      checkMethodCall(statement="android", unnamedArguments="{ buildTypes { debug { packageNameSuffix ".debug" } } }")
      checkMethodCall(statement="buildTypes", parent="android", unnamedArguments="{ debug { packageNameSuffix ".debug" } }")
      checkMethodCall(statement="debug", parent="buildTypes", parentParent="android", unnamedArguments="{ packageNameSuffix ".debug" }")
      checkMethodCall(statement="packageNameSuffix", parent="debug", parentParent="buildTypes", unnamedArguments="".debug"")
      checkDslPropertyAssignment(property="packageNameSuffix", value="".debug"", parent="debug", parentParent="buildTypes")
      """
    )
  }

  fun testLanguageLevels() {
    check(
      """
      plugins {
          id 'java'
      }
      java.sourceCompatibility JavaVersion.VERSION_1_8
      java.targetCompatibility JavaVersion.VERSION_1_8
      android.compileOptions.sourceCompatibility JavaVersion.VERSION_1_8
      android.defaultConfig.vectorDrawables.useSupportLibrary true
      """,
      """
      checkMethodCall(statement="plugins", unnamedArguments="{ id 'java' }")
      checkMethodCall(statement="id", parent="plugins", unnamedArguments="'java'")
      checkDslPropertyAssignment(property="id", value="'java'", parent="plugins")
      checkMethodCall(statement="sourceCompatibility", parent="java", unnamedArguments="JavaVersion.VERSION_1_8")
      checkDslPropertyAssignment(property="sourceCompatibility", value="JavaVersion.VERSION_1_8", parent="java")
      checkMethodCall(statement="targetCompatibility", parent="java", unnamedArguments="JavaVersion.VERSION_1_8")
      checkDslPropertyAssignment(property="targetCompatibility", value="JavaVersion.VERSION_1_8", parent="java")
      checkMethodCall(statement="sourceCompatibility", parent="compileOptions", parentParent="android", unnamedArguments="JavaVersion.VERSION_1_8")
      checkDslPropertyAssignment(property="sourceCompatibility", value="JavaVersion.VERSION_1_8", parent="compileOptions", parentParent="android")
      checkMethodCall(statement="useSupportLibrary", parent="vectorDrawables", parentParent="defaultConfig", unnamedArguments="true")
      checkDslPropertyAssignment(property="useSupportLibrary", value="true", parent="vectorDrawables", parentParent="defaultConfig")
      """
    )
  }

  fun testZeroArgMethod() {
    check(
      """
      buildscript {
        repositories {
          jcenter()
        }
      }
      """,
      """
      checkMethodCall(statement="buildscript", unnamedArguments="{ repositories { jcenter() } }")
      checkMethodCall(statement="repositories", parent="buildscript", unnamedArguments="{ jcenter() }")
      checkMethodCall(statement="jcenter", parent="repositories", parentParent="buildscript")
      """
    )
  }

  fun testPropertyExpression() {
    check(
      """
      buildscript {
        ext.androidGradleVersion = '0.11.0'
        dependencies {
          classpath "com.android.tools.build:gradle:${"$"}androidGradleVersion"
        }
      }
      """,
      """
      checkDslPropertyAssignment(property="androidGradleVersion", value="'0.11.0'", parent="ext", parentParent="buildscript")
      checkDslPropertyAssignment(property="classpath", value=""com.android.tools.build:gradle:${"$"}androidGradleVersion"", parent="dependencies", parentParent="buildscript")
      checkMethodCall(statement="buildscript", unnamedArguments="{ ext.androidGradleVersion = '0.11.0' dependencies { classpath "com.android.tools.build:gradle:${"$"}androidGradleVersion" } }")
      checkMethodCall(statement="classpath", parent="dependencies", parentParent="buildscript", unnamedArguments=""com.android.tools.build:gradle:${"$"}androidGradleVersion"")
      checkMethodCall(statement="dependencies", parent="buildscript", unnamedArguments="{ classpath "com.android.tools.build:gradle:${"$"}androidGradleVersion" }")
      """
    )
  }

  // Test infrastructure below

  private fun check(@Language("groovy") gradleSource: String, expected: String) {
    val file = myFixture.addFileToProject("build.gradle", gradleSource.trimIndent())

    val visitor = LintIdeGradleVisitor()
    val detector = LoggingGradleDetector()
    val client = LintIdeSupport.get().createClient(project)
    val project = Mockito.mock(Project::class.java)
    val configuration = Mockito.mock(Configuration::class.java)
    whenever(project.getConfiguration(any())).thenReturn(configuration)

    val request =
      LintIdeRequest(client, myFixture.project, listOf(file.virtualFile), emptyList(), true)
    val driver = Mockito.mock(LintDriver::class.java)
    whenever(driver.client).thenReturn(client)
    whenever(driver.request).thenReturn(request)
    val context =
      GradleContext(visitor, driver, project, null, file.virtualFile.toNioPath().toFile())
    visitor.visitBuildScript(context, listOf(detector))

    // The order may vary slightly due to differences in the way we're handling
    // the ASTs (e.g. do we get a property callback or a method callback
    // first?), but the order should not matter to detectors
    assertEquals(
      expected.trimIndent().trim().lines().sorted().joinToString("\n"),
      detector.toString().trim().lines().sorted().joinToString("\n")
    )
  }
}

// Keep in sync with the detector and tests in GroovyGradleVisitorTest
class LoggingGradleDetector : Detector(), GradleScanner {
  private val sb = StringBuilder()

  private fun log(method: String, vararg arguments: Pair<String, String?>) {
    sb.append(method).append('(')
    val toList: List<Pair<String, String?>> = arguments.filter { it.second != null }.toList()
    sb.append(toList.joinToString(", ") { (key, value) -> "$key=\"$value\"" })
    sb.append(')')
    sb.append('\n')
  }

  override fun visitBuildScript(context: Context) {
    log("visitBuildScript", "file" to context.file.name)
  }

  override fun checkDslPropertyAssignment(
    context: GradleContext,
    property: String,
    value: String,
    parent: String,
    parentParent: String?,
    propertyCookie: Any,
    valueCookie: Any,
    statementCookie: Any
  ) {
    log(
      "checkDslPropertyAssignment",
      "property" to property,
      "value" to value,
      "parent" to parent,
      "parentParent" to parentParent
    )
  }

  override fun checkMethodCall(
    context: GradleContext,
    statement: String,
    parent: String?,
    parentParent: String?,
    namedArguments: Map<String, String>,
    unnamedArguments: List<String>,
    cookie: Any
  ) {
    log(
      "checkMethodCall",
      "statement" to statement,
      "parent" to parent,
      "parentParent" to parentParent,
      "namedArguments" to namedArguments.log(),
      "unnamedArguments" to unnamedArguments.log()
    )
  }

  private fun List<String>.log(): String? {
    if (isEmpty()) {
      return null
    }
    return joinToString(", ").replace('\n', ' ')
  }

  private fun Map<String, String>.log(): String? {
    if (isEmpty()) {
      return null
    }
    return this.keys.sorted().joinToString(", ") { "$it=${getValue(it).replace('\n',' ')}" }
  }

  override fun toString(): String = sb.replace(Regex(" +"), " ").trim()
}
