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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.testutils.classloader.MultiClassLoader
import com.android.tools.idea.gradle.project.sync.issues.GradleExceptionAnalyticsSupport.GradleError
import com.android.tools.idea.gradle.project.sync.issues.GradleExceptionAnalyticsSupport.GradleException
import com.android.tools.idea.gradle.project.sync.issues.GradleExceptionAnalyticsSupport.GradleFailureDetails
import com.google.common.truth.Truth
import com.ibm.icu.impl.Assert
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.exceptions.MultiCauseException
import org.gradle.internal.serialize.PlaceholderException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.lang.reflect.InvocationTargetException

class GradleExceptionAnalyticsSupportTest {

  @get:Rule
  val testNameRule = TestName()

  val gradleExceptionAnalyticsSupport = GradleExceptionAnalyticsSupport(listOf(
    "jdk.",
    "java.lang.",
    "org.gradle.",
    "com.android."
  ))

  private val defaultTopFrameInfo: GradleExceptionAnalyticsSupport.GradleExceptionStackFrame by lazy {
    GradleExceptionAnalyticsSupport.GradleExceptionStackFrame(
      className = GradleExceptionAnalyticsSupportTest::class.java.name,
      methodName = testNameRule.methodName,
      fileName = "",
      lineNumber = 0,
      frameIndex = 0,
    )
  }

  @Test
  fun testSingleException() {
    val exception = RuntimeException("Exception")

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    Truth.assertThat(toTestString(gradleFailureDetails)).isEqualTo("""
      GradleFailureDetails(
        GradleError(
          GradleException(
            exceptionClass=java.lang.RuntimeException
            topFrame=GradleExceptionStackFrame(${GradleExceptionAnalyticsSupportTest::class.java.name}#${testNameRule.methodName}, frameIndex=0)
          )
        )
      )
    """.trimIndent())
  }

  @Test
  fun testExceptionWithCauseStack() {
    val exception = TestThrowable1(TestThrowable2(RuntimeException("Exception")))

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    Truth.assertThat(toTestString(gradleFailureDetails)).isEqualTo(toTestString(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1", defaultTopFrameInfo),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2", defaultTopFrameInfo),
        GradleException("java.lang.RuntimeException", defaultTopFrameInfo)
      ))
    ))))
  }

  @Test
  fun testAllowListFiltersNotAllowedNames() {
    val exception = TestThrowable1(TestThrowable2(RuntimeException("Exception")))

    val gradleFailureDetails = GradleExceptionAnalyticsSupport(listOf("com.android.")).extractFailureDetails(exception)

    Truth.assertThat(toTestString(gradleFailureDetails)).isEqualTo(toTestString(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1", defaultTopFrameInfo),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2", defaultTopFrameInfo),
        GradleException("<hidden>", defaultTopFrameInfo)
      ))
    ))))
  }

  @Test
  fun testAllowListFiltersFirstStackFrame() {
    try { Assert.fail("message") } catch (exception: Throwable) {
      val gradleFailureDetails = GradleExceptionAnalyticsSupport(listOf("com.android.")).extractFailureDetails(exception)
      // This also tests file name and line number. It will fail with changes to this file as line numbers would change.
      // If it starts failing too much will have to think of smth else.
      val reportedFrame = GradleExceptionAnalyticsSupport.GradleExceptionStackFrame(
        className = GradleExceptionAnalyticsSupportTest::class.java.name,
        methodName = testNameRule.methodName,
        fileName = "GradleExceptionAnalyticsSupportTest.kt",
        lineNumber = 104,
        frameIndex = 1
      )
      Truth.assertThat(gradleFailureDetails).isEqualTo(GradleFailureDetails(listOf(
        GradleError(listOf(
          GradleException("<hidden>", reportedFrame),
        ))
      )))
    }
  }

  @Test
  fun testAllowListFiltersAllStackFrames() {
    val exception = TestThrowable1(TestThrowable2(RuntimeException("Exception")))

    val gradleFailureDetails = GradleExceptionAnalyticsSupport(emptyList()).extractFailureDetails(exception)

    Truth.assertThat(gradleFailureDetails).isEqualTo(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("<hidden>", null),
        GradleException("<hidden>", null),
        GradleException("<hidden>", null)
      ))
    )))
  }

  @Test
  fun testDefaultMultiCauseException() {
    val exception = TestThrowable1(DefaultMultiCauseException("Error", listOf(
      TestThrowable2(RuntimeException("Exception")),
      TestThrowable3(RuntimeException("Exception"))
    )))

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    Truth.assertThat(toTestString(gradleFailureDetails)).isEqualTo(toTestString(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1", defaultTopFrameInfo),
        GradleException("org.gradle.internal.exceptions.DefaultMultiCauseException", defaultTopFrameInfo),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2", defaultTopFrameInfo),
        GradleException("java.lang.RuntimeException", defaultTopFrameInfo)
      )),
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1", defaultTopFrameInfo),
        GradleException("org.gradle.internal.exceptions.DefaultMultiCauseException", defaultTopFrameInfo),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable3", defaultTopFrameInfo),
        GradleException("java.lang.RuntimeException", defaultTopFrameInfo)
      ))
    ))))
  }

  @Test
  fun testOtherMultiCauseException() {
    val exception = TestThrowable1(TestThrowableMultiCause(listOf(
      TestThrowable2(RuntimeException("Exception")),
      TestThrowable3(RuntimeException("Exception"))
    )))

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    Truth.assertThat(toTestString(gradleFailureDetails)).isEqualTo(toTestString(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1", defaultTopFrameInfo),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowableMultiCause", defaultTopFrameInfo),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2", defaultTopFrameInfo),
        GradleException("java.lang.RuntimeException", defaultTopFrameInfo)
      )),
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1", defaultTopFrameInfo),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowableMultiCause", defaultTopFrameInfo),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable3", defaultTopFrameInfo),
        GradleException("java.lang.RuntimeException", defaultTopFrameInfo)
      ))
    ))))
  }

  @Test
  fun testPlaceholderException() {
    val originalException = TestThrowable2(RuntimeException("Exception"))
    val exception = TestThrowable1(PlaceholderException(
      originalException::class.java.name,
      originalException.message, null,
      originalException.toString(), null,
      originalException.cause
    ))

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    Truth.assertThat(toTestString(gradleFailureDetails)).isEqualTo(toTestString(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1", defaultTopFrameInfo),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2", defaultTopFrameInfo),
        GradleException("java.lang.RuntimeException", defaultTopFrameInfo)
      ))
    ))))
  }

  @Test
  fun testInvocationTargetException() {
    //InvocationTargetException contains real cause happened during reflection call in target, not cause.
    //However, it's getCause returns target so no special treatment required in processing.
    val exception = TestThrowable1(InvocationTargetException(TestThrowable2(RuntimeException("Exception"))))

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    Truth.assertThat(toTestString(gradleFailureDetails)).isEqualTo(toTestString(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1", defaultTopFrameInfo),
        GradleException("java.lang.reflect.InvocationTargetException", defaultTopFrameInfo),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2", defaultTopFrameInfo),
        GradleException("java.lang.RuntimeException", defaultTopFrameInfo)
      ))
    ))))
  }

  @Test
  fun testPlaceholderExceptionLoadedWithDifferentClassloader() {
    val classloader = MultiClassLoader(listOf(
      PlaceholderException::class.java.name,
      TestThrowable3::class.java.name
    ))

    val exception3 = classloader.loadClass(TestThrowable3::class.java.name)
      .getConstructor(Throwable::class.java)
      .newInstance(RuntimeException("Exception")) as Throwable
    val originalException = TestThrowable2(exception3)
    val placeholderException = classloader.loadClass(PlaceholderException::class.java.name).getConstructor(
      String::class.java,
      String::class.java,
      Throwable::class.java,
      String::class.java,
      Throwable::class.java,
      Throwable::class.java,
    ).newInstance(
      originalException::class.java.name,
      originalException.message, null,
      originalException.toString(), null,
      exception3
    ) as Throwable

    val exception = TestThrowable1(placeholderException)

    // isInstance does not work with this exceptions
    Truth.assertThat(exception3).isNotInstanceOf(TestThrowable3::class.java)
    Truth.assertThat(placeholderException).isNotInstanceOf(PlaceholderException::class.java)

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    val reflectionFrame = GradleExceptionAnalyticsSupport.GradleExceptionStackFrame(
      className = "jdk.internal.reflect.DirectConstructorHandleAccessor",
      methodName = "newInstance",
      fileName = "",
      lineNumber = 0,
      frameIndex = 0
    )
    Truth.assertThat(toTestString(gradleFailureDetails)).isEqualTo(toTestString(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1", defaultTopFrameInfo),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2", reflectionFrame),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable3", reflectionFrame),
        GradleException("java.lang.RuntimeException", defaultTopFrameInfo)
      ))
    ))))
  }

  @Test
  fun testPlaceholderExceptionThrowingErrorOnReflectionAccess() {
    val originalException = TestThrowable2(RuntimeException("Exception"))
    val placeholder =  FailingTestPlaceholderException(originalException)
    val exception = TestThrowable1(placeholder)

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    Truth.assertThat(toTestString(gradleFailureDetails)).isEqualTo(toTestString(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1", defaultTopFrameInfo),
        // Not able to retrieve real class name, placeholder name is used.
        GradleException("com.android.tools.idea.gradle.project.sync.issues.FailingTestPlaceholderException", defaultTopFrameInfo),
        GradleException("java.lang.RuntimeException", defaultTopFrameInfo)
      ))
    ))))
  }
}

private class TestThrowable1(cause: Throwable) : Throwable(cause)
private class TestThrowable2(cause: Throwable) : Throwable(cause)
//Need to make public to access constructor with reflection in test
class TestThrowable3(cause: Throwable) : Throwable(cause)
private class TestThrowableMultiCause(val providedCauses: List<Throwable>) : Throwable(), MultiCauseException {
  override fun getResolutions(): List<String> = emptyList()
  override fun getCauses(): List<Throwable> = providedCauses
}
class FailingTestPlaceholderException(exception: Throwable) : PlaceholderException(
  exception::class.java.name,
  exception.message, null,
  exception.toString(), null,
  exception.cause
) {
  override fun getExceptionClassName(): String = error("error in getExceptionClassName")
}

private fun toTestString(failure: GradleFailureDetails): String = buildString {
  appendLine("GradleFailureDetails(")
  failure.errors.forEach { error ->
    appendLine("  GradleError(")
    error.exceptions.forEach { exception ->
      appendLine("    GradleException(")
      appendLine("      exceptionClass=${exception.exceptionClass}")
      exception.topFrame?.also {
        appendLine("      topFrame=GradleExceptionStackFrame(${it.className}#${it.methodName}, frameIndex=${it.frameIndex})")
      }
      appendLine("    )")
    }
    appendLine("  )")
  }
  append(")")
}
