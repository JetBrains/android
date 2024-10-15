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
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.exceptions.MultiCauseException
import org.gradle.internal.serialize.PlaceholderException
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class GradleExceptionAnalyticsSupportTest {
  val gradleExceptionAnalyticsSupport = GradleExceptionAnalyticsSupport(listOf(
    "java.lang.",
    "org.gradle.",
    "com.android."
  ))

  @Test
  fun testSingleException() {
    val exception = RuntimeException("Exception")

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    Truth.assertThat(gradleFailureDetails).isEqualTo(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("java.lang.RuntimeException")
      ))
    )))
  }

  @Test
  fun testExceptionWithCauseStack() {
    val exception = TestThrowable1(TestThrowable2(RuntimeException("Exception")))

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    Truth.assertThat(gradleFailureDetails).isEqualTo(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1"),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2"),
        GradleException("java.lang.RuntimeException")
      ))
    )))
  }

  @Test
  fun testAllowListFiltersNotAllowedNames() {
    val exception = TestThrowable1(TestThrowable2(RuntimeException("Exception")))

    val gradleFailureDetails = GradleExceptionAnalyticsSupport(listOf("com.android.")).extractFailureDetails(exception)

    Truth.assertThat(gradleFailureDetails).isEqualTo(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1"),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2"),
        GradleException("<hidden>")
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

    Truth.assertThat(gradleFailureDetails).isEqualTo(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1"),
        GradleException("org.gradle.internal.exceptions.DefaultMultiCauseException"),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2"),
        GradleException("java.lang.RuntimeException")
      )),
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1"),
        GradleException("org.gradle.internal.exceptions.DefaultMultiCauseException"),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable3"),
        GradleException("java.lang.RuntimeException")
      ))
    )))
  }

  @Test
  fun testOtherMultiCauseException() {
    val exception = TestThrowable1(TestThrowableMultiCause(listOf(
      TestThrowable2(RuntimeException("Exception")),
      TestThrowable3(RuntimeException("Exception"))
    )))

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    Truth.assertThat(gradleFailureDetails).isEqualTo(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1"),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowableMultiCause"),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2"),
        GradleException("java.lang.RuntimeException")
      )),
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1"),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowableMultiCause"),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable3"),
        GradleException("java.lang.RuntimeException")
      ))
    )))
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

    Truth.assertThat(gradleFailureDetails).isEqualTo(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1"),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2"),
        GradleException("java.lang.RuntimeException")
      ))
    )))
  }

  @Test
  fun testInvocationTargetException() {
    //InvocationTargetException contains real cause happened during reflection call in target, not cause.
    //However, it's getCause returns target so no special treatment required in processing.
    val exception = TestThrowable1(InvocationTargetException(TestThrowable2(RuntimeException("Exception"))))

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    Truth.assertThat(gradleFailureDetails).isEqualTo(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1"),
        GradleException("java.lang.reflect.InvocationTargetException"),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2"),
        GradleException("java.lang.RuntimeException")
      ))
    )))
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

    Truth.assertThat(gradleFailureDetails).isEqualTo(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1"),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable2"),
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable3"),
        GradleException("java.lang.RuntimeException")
      ))
    )))
  }

  @Test
  fun testPlaceholderExceptionThrowingErrorOnReflectionAccess() {
    val originalException = TestThrowable2(RuntimeException("Exception"))
    val placeholder =  FailingTestPlaceholderException(originalException)
    val exception = TestThrowable1(placeholder)

    val gradleFailureDetails = gradleExceptionAnalyticsSupport.extractFailureDetails(exception)

    Truth.assertThat(gradleFailureDetails).isEqualTo(GradleFailureDetails(listOf(
      GradleError(listOf(
        GradleException("com.android.tools.idea.gradle.project.sync.issues.TestThrowable1"),
        // Not able to retrieve real class name, placeholder name is used.
        GradleException("com.android.tools.idea.gradle.project.sync.issues.FailingTestPlaceholderException"),
        GradleException("java.lang.RuntimeException")
      ))
    )))
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
