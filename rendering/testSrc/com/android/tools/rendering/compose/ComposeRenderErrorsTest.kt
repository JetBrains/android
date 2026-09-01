/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.rendering.compose

import com.google.common.truth.Truth.assertThat
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeoutException
import org.junit.Test

class ComposeRenderErrorsTest {

  @Test
  fun testUnwrapInvocationTargetException() {
    val rootCause = IllegalStateException("Root cause")
    val wrapper1 = InvocationTargetException(rootCause)
    val wrapper2 = InvocationTargetException(wrapper1)
    val wrapperNoCause = InvocationTargetException(null)

    assertThat(ComposeRenderErrors.unwrapIfInvocationTargetException(wrapper2)).isSameAs(rootCause)
    assertThat(ComposeRenderErrors.unwrapIfInvocationTargetException(wrapper1)).isSameAs(rootCause)
    assertThat(ComposeRenderErrors.unwrapIfInvocationTargetException(rootCause)).isSameAs(rootCause)
    assertThat(ComposeRenderErrors.unwrapIfInvocationTargetException(wrapperNoCause)).isSameAs(wrapperNoCause)
    assertThat(ComposeRenderErrors.unwrapIfInvocationTargetException(null)).isNull()
  }

  @Test
  fun testIsCompositionLocalThrowable() {
    val matching = IllegalStateException("CompositionLocal LocalGraphicsContext not present")
    val nonMatching1 = IllegalStateException("Something else")
    val nonMatching2 = IllegalArgumentException("CompositionLocal LocalGraphicsContext not present")

    assertThat(ComposeRenderErrors.isCompositionLocalThrowable(matching)).isTrue()
    assertThat(ComposeRenderErrors.isCompositionLocalThrowable(nonMatching1)).isFalse()
    assertThat(ComposeRenderErrors.isCompositionLocalThrowable(nonMatching2)).isFalse()
    assertThat(ComposeRenderErrors.isCompositionLocalThrowable(null)).isFalse()

    assertThat(ComposeRenderErrors.getHint(matching)).isEqualTo(ComposeRenderErrors.COMPOSITION_LOCAL_NOT_FOUND_HINT)
  }

  @Test
  fun testIsViewModelThrowable() {
    val viewModelError =
      IllegalStateException("ViewModel initialization error").apply {
        stackTrace =
          arrayOf(
            StackTraceElement("androidx.compose.ui.tooling.CommonPreviewUtils", "invokeComposableMethod", "CommonPreviewUtils.kt", 149),
            StackTraceElement("androidx.lifecycle.viewmodel.compose.ViewModelKt", "viewModel", "ViewModel.kt", 72),
          )
      }

    val regularError =
      IllegalStateException("Regular error").apply {
        stackTrace = arrayOf(StackTraceElement("com.example.MyScreenKt", "MyComposable", "MyScreen.kt", 10))
      }

    assertThat(ComposeRenderErrors.isViewModelThrowable(viewModelError)).isTrue()
    assertThat(ComposeRenderErrors.isViewModelThrowable(regularError)).isFalse()
    assertThat(ComposeRenderErrors.isViewModelThrowable(null)).isFalse()

    assertThat(ComposeRenderErrors.getHint(viewModelError)).isEqualTo(ComposeRenderErrors.VIEW_MODEL_HINT)
  }

  @Test
  fun testIsClassCastException() {
    val bridgeCastException =
      ClassCastException("class com.android.layoutlib.bridge.android.BridgeContext cannot be cast to class android.app.Activity")
    val otherCastException = ClassCastException("class java.lang.String cannot be cast to class java.lang.Integer")

    assertThat(ComposeRenderErrors.isClassCastException(bridgeCastException)).isTrue()
    assertThat(ComposeRenderErrors.isClassCastException(otherCastException)).isFalse()
    assertThat(ComposeRenderErrors.isClassCastException(null)).isFalse()

    assertThat(ComposeRenderErrors.getHint(bridgeCastException)).isEqualTo(ComposeRenderErrors.CLASS_CAST_EXCEPTION_HINT)
  }

  @Test
  fun testIsComposeNotFoundThrowable() {
    val composeNotFound =
      NoSuchMethodException("com.example.MyPreviewKt.NonExistentPreview").apply {
        stackTrace =
          arrayOf(
            StackTraceElement("java.lang.Class", "getMethod", "Class.java", 100),
            StackTraceElement(
              "androidx.compose.ui.tooling.CommonPreviewUtils",
              "invokeComposableViaReflection",
              "CommonPreviewUtils.kt",
              188,
            ),
          )
      }
    val regularNoSuchMethod = NoSuchMethodException("someOtherMethod")

    assertThat(ComposeRenderErrors.isComposeNotFoundThrowable(composeNotFound)).isTrue()
    assertThat(ComposeRenderErrors.isComposeNotFoundThrowable(regularNoSuchMethod)).isFalse()
  }

  @Test
  fun testIsPreviewParameterMismatchThrowable() {
    val typeMismatch =
      IllegalArgumentException("argument type mismatch").apply {
        stackTrace =
          arrayOf(
            StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 566),
            StackTraceElement("element1", "m", "F.kt", 1),
            StackTraceElement("element2", "m", "F.kt", 2),
            StackTraceElement("element3", "m", "F.kt", 3),
            StackTraceElement("element4", "m", "F.kt", 4),
            StackTraceElement("androidx.compose.ui.tooling.ComposableInvoker", "invokeComposable", "ComposableInvoker.kt", 203),
          )
      }

    assertThat(ComposeRenderErrors.isPreviewParameterMismatchThrowable(typeMismatch)).isTrue()
  }

  @Test
  fun testIsFailToLoadPreviewParameterProvider() {
    val providerError = NoSuchMethodException("com.example.MyProvider.\$FailToLoadPreviewParameterProvider")
    val providerErrorWithSuffix = NoSuchMethodException("com.example.MyProvider.\$FailToLoadPreviewParameterProvider not found")
    val regularNoSuchMethod = NoSuchMethodException("regularMethod")

    assertThat(ComposeRenderErrors.isFailToLoadPreviewParameterProvider(providerError)).isTrue()
    assertThat(ComposeRenderErrors.isFailToLoadPreviewParameterProvider(providerErrorWithSuffix)).isTrue()
    assertThat(ComposeRenderErrors.isFailToLoadPreviewParameterProvider(regularNoSuchMethod)).isFalse()
  }

  @Test
  fun testIsTimeoutToLoadPreview() {
    val timeout = TimeoutException("The render action was too slow to execute (100000ms)")
    val other = RuntimeException("Other")

    assertThat(ComposeRenderErrors.isTimeoutToLoadPreview(timeout)).isTrue()
    assertThat(ComposeRenderErrors.isTimeoutToLoadPreview(other)).isFalse()
  }

  @Test
  fun testMatchAndIsHandled() {
    val matching = IllegalStateException("CompositionLocal LocalGraphicsContext not present")
    val wrapped = InvocationTargetException(matching)

    val match = ComposeRenderErrors.match(wrapped)
    assertThat(match).isNotNull()
    assertThat(match?.type).isEqualTo(ComposeRenderErrorType.COMPOSITION_LOCAL_NOT_FOUND)
    assertThat(match?.rootCause).isSameAs(matching)
    assertThat(match?.summary).isEqualTo("Failed to instantiate Composition Local")
    assertThat(match?.hint).isEqualTo(ComposeRenderErrors.COMPOSITION_LOCAL_NOT_FOUND_HINT)
    assertThat(ComposeRenderErrors.isHandled(wrapped)).isTrue()

    val unknown = RuntimeException("Unknown generic error")
    assertThat(ComposeRenderErrors.match(unknown)).isNull()
    assertThat(ComposeRenderErrors.isHandled(unknown)).isFalse()
  }
}
