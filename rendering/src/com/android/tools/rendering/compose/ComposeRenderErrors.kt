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

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeoutException

/**
 * Common analysis and classification utilities for Jetpack Compose rendering errors.
 *
 * Provides predicates, exception unwrapping, and diagnostic hints for common Compose preview failure modes (e.g. missing CompositionLocals,
 * ViewModel instantiations, type mismatches, and ClassCastExceptions).
 */
object ComposeRenderErrors {

  const val VIEW_MODEL_HINT =
    """
      This appears to be a ViewModel-related error, as Previews cannot instantiate them directly.
      A common solution is to refactor the Composable to accept state parameters directly, instead
      of the whole ViewModel instance.
      Alternatively, consider mocking the ViewModel or providing a custom factory for the Preview.
      """

  const val CLASS_CAST_EXCEPTION_HINT =
    """
      To resolve this error and ensure the resilience of the Composables across both preview and
      runtime environments, the recommended practice is to avoid direct casting. Instead, you
      should implement a safe, recursive findActivity() extension function. This function is
      designed to properly traverse the Context hierarchy, returning the actual Activity when
      present or safely yielding null when running in a preview. This allows you to interact
      with the Activity using the safe-call operator ?., thereby preventing rendering errors
      and maintaining a smooth development workflow.
      """

  const val COMPOSITION_LOCAL_NOT_FOUND_HINT =
    """
      This error is not a bug in the Compose framework; it is an intentional "fail-fast" mechanism.
      Here's the background you need to know:
      * Purpose of CompositionLocal: It's a way to pass data down the composable tree implicitly,
      without having to pass it as a parameter to every single composable.
      * Creation: When a CompositionLocal is created (e.g., with staticCompositionLocalOf), it
      requires a defaultFactory lambda. This lambda is only executed if a composable tries to access
       the local's value (via .current) but no value has been provided by an ancestor in the tree.
      * The Crash: For essential services that have no sensible default (like theme colors or screen
      density), the standard practice is to make this defaultFactory throw an IllegalStateException.
      The error is always solved by wrapping the composable (or one of its ancestors) in the correct
       CompositionLocalProvider composable, which provides a value for that specific
       CompositionLocal.
      """

  /**
   * Unwraps [InvocationTargetException] recursively to get the original root cause of a reflection-based failure. If the wrapper has no
   * cause, the wrapper itself is returned.
   */
  @JvmStatic
  tailrec fun unwrapIfInvocationTargetException(throwable: Throwable?): Throwable? {
    val cause = (throwable as? InvocationTargetException)?.cause
    return if (cause == null || cause === throwable) throwable else unwrapIfInvocationTargetException(cause)
  }

  /**
   * Matches the given [throwable] against known Compose render errors after unwrapping. Returns a [ComposeRenderErrorMatch] with the
   * classified type, root cause, summary, and hint, or `null` if the error is unhandled.
   */
  @JvmStatic
  fun match(throwable: Throwable?): ComposeRenderErrorMatch? {
    val rootCause = unwrapIfInvocationTargetException(throwable) ?: return null
    val type = ComposeRenderErrorType.entries.firstOrNull { it.matches(rootCause) } ?: return null
    return ComposeRenderErrorMatch(type, rootCause, type.summary(rootCause), type.hint)
  }

  /** Returns a diagnostic hint for the given [throwable] if it is a known Compose issue. */
  @JvmStatic fun getHint(throwable: Throwable?): String? = match(throwable)?.hint

  /** Checks if the given [throwable] is one of the recognized Compose preview errors. */
  @JvmStatic fun isHandled(throwable: Throwable?): Boolean = match(throwable) != null

  // TODO(b/552871624): Convenience predicate delegates preserving targeted API compatibility.
  // These only exist to remain compatible with legacy ComposeRenderErrorContributor APIs/callers
  // and can be removed once all callers in Android Studio / designer are refactored to delegate
  // directly to the match(throwable) method.
  @JvmStatic
  fun isCompositionLocalThrowable(throwable: Throwable?): Boolean =
    ComposeRenderErrorType.COMPOSITION_LOCAL_NOT_FOUND.matches(unwrapIfInvocationTargetException(throwable))

  @JvmStatic
  fun isViewModelThrowable(throwable: Throwable?): Boolean =
    ComposeRenderErrorType.VIEW_MODEL_INSTANTIATION.matches(unwrapIfInvocationTargetException(throwable))

  @JvmStatic
  fun isClassCastException(throwable: Throwable?): Boolean =
    ComposeRenderErrorType.CLASS_CAST_TO_ACTIVITY.matches(unwrapIfInvocationTargetException(throwable))

  @JvmStatic
  fun isComposeNotFoundThrowable(throwable: Throwable?): Boolean =
    ComposeRenderErrorType.COMPOSE_PREVIEW_NOT_FOUND.matches(unwrapIfInvocationTargetException(throwable))

  @JvmStatic
  fun isPreviewParameterMismatchThrowable(throwable: Throwable?): Boolean =
    ComposeRenderErrorType.PREVIEW_PARAMETER_PROVIDER_MISMATCH.matches(unwrapIfInvocationTargetException(throwable))

  @JvmStatic
  fun isFailToLoadPreviewParameterProvider(throwable: Throwable?): Boolean =
    ComposeRenderErrorType.FAIL_TO_LOAD_PREVIEW_PARAMETER_PROVIDER.matches(unwrapIfInvocationTargetException(throwable))

  @JvmStatic
  fun isTimeoutToLoadPreview(throwable: Throwable?): Boolean =
    ComposeRenderErrorType.TIMEOUT_TO_LOAD_PREVIEW.matches(unwrapIfInvocationTargetException(throwable))
}

/** Result of matching a [Throwable] against a known [ComposeRenderErrorType]. */
data class ComposeRenderErrorMatch(
  val type: ComposeRenderErrorType,
  val rootCause: Throwable,
  val summary: String,
  val hint: String?,
)

/** Known Compose preview rendering failure modes. */
enum class ComposeRenderErrorType(
  private val predicate: (Throwable) -> Boolean,
  val summary: (Throwable) -> String,
  val hint: String? = null,
) {
  PREVIEW_PARAMETER_PROVIDER_MISMATCH(
    predicate = { t ->
      t is IllegalArgumentException &&
        t.message == "argument type mismatch" &&
        t.stackTrace.drop(5).firstOrNull()?.methodName?.startsWith("invokeComposable") == true
    },
    summary = { "PreviewParameterProvider/@Preview type mismatch." },
  ),
  FAIL_TO_LOAD_PREVIEW_PARAMETER_PROVIDER(
    predicate = { t ->
      t is NoSuchMethodException &&
        (t.message?.endsWith("\$FailToLoadPreviewParameterProvider") == true ||
          t.message?.endsWith("\$FailToLoadPreviewParameterProvider not found") == true)
    },
    summary = { "Fail to load PreviewParameterProvider" },
  ),
  COMPOSE_PREVIEW_NOT_FOUND(
    predicate = { t ->
      t is NoSuchMethodException && t.stackTrace.getOrNull(1)?.methodName?.startsWith("invokeComposableViaReflection") == true
    },
    summary = { t -> "Unable to find @Preview '${t.message}'" },
  ),
  TIMEOUT_TO_LOAD_PREVIEW(
    predicate = { it is TimeoutException },
    summary = { "Timeout error" },
  ),
  CLASS_CAST_TO_ACTIVITY(
    predicate = { t ->
      t is ClassCastException &&
        t.message?.startsWith("class com.android.layoutlib.bridge.android.BridgeContext cannot be cast to class android.app.Activity") ==
          true
    },
    summary = { "Context cannot be cast to Activity in Compose Preview" },
    hint = ComposeRenderErrors.CLASS_CAST_EXCEPTION_HINT,
  ),
  COMPOSITION_LOCAL_NOT_FOUND(
    predicate = { t ->
      t is IllegalStateException && t.message?.startsWith("CompositionLocal") == true && t.message?.endsWith("not present") == true
    },
    summary = { "Failed to instantiate Composition Local" },
    hint = ComposeRenderErrors.COMPOSITION_LOCAL_NOT_FOUND_HINT,
  ),
  VIEW_MODEL_INSTANTIATION(
    predicate = { t ->
      t.stackTrace.any {
        (it.methodName == "viewModel" || it.className.endsWith("ViewModelProvider") || it.className.endsWith("ViewModelKt")) &&
          it.className.startsWith("androidx.lifecycle")
      }
    },
    summary = { "Failed to instantiate a ViewModel" },
    hint = ComposeRenderErrors.VIEW_MODEL_HINT,
  );

  fun matches(throwable: Throwable?): Boolean = throwable != null && predicate(throwable)
}
