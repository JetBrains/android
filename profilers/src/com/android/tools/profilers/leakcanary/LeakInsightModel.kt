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
package com.android.tools.profilers.leakcanary

import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.tasks.analytics.LeakCanaryUiAction
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LeakInsightModel(
  private val ideServices: IdeProfilerServices,
  private val scope: CoroutineScope,
  private val trackUiAction: (LeakCanaryUiAction) -> Unit = {},
) {
  private val _currentInsight = MutableStateFlow<LoadingState<AiInsight?>>(LoadingState.Ready(null))
  val currentInsight = _currentInsight.asStateFlow()

  private val _isInsightVisible = MutableStateFlow(true)
  val isInsightVisible = _isInsightVisible.asStateFlow()
  private var insightJob: Job? = null
  private val _isInsightAutoGenerateEnabled =
    MutableStateFlow(ideServices.persistentProfilerPreferences.getBoolean(KEY_LEAKCANARY_INSIGHT_AUTO_GENERATE, false))
  val isInsightAutoGenerateEnabled = _isInsightAutoGenerateEnabled.asStateFlow()

  private val insightCache = ConcurrentHashMap<String, LoadingState<AiInsight?>>()
  private var selectedLeak: Leak? = null

  fun setInsightAutoGenerateEnabled(enabled: Boolean) {
    _isInsightAutoGenerateEnabled.value = enabled
    ideServices.persistentProfilerPreferences.setBoolean(KEY_LEAKCANARY_INSIGHT_AUTO_GENERATE, enabled)
    if (enabled) {
      trackUiAction(LeakCanaryUiAction.INSIGHT_AUTO_GENERATE_ENABLED)
      if (_isInsightVisible.value) {
        selectedLeak?.let { leak ->
          val cached = insightCache[leak.signature]
          if (cached == null || (cached is LoadingState.Ready && cached.value == null)) {
            fetchInsight(leak)
          }
        }
      }
    } else {
      trackUiAction(LeakCanaryUiAction.INSIGHT_AUTO_GENERATE_DISABLED)
    }
  }

  fun onLeakSelection(newLeak: Leak?) {
    selectedLeak = newLeak
    if (_isInsightVisible.value) {
      val cached = newLeak?.let { insightCache[it.signature] }
      if (cached != null) {
        _currentInsight.value = cached
        insightJob?.cancel()
      } else {
        _currentInsight.value = LoadingState.Ready(null)
        val autoGenerate = isInsightAutoGenerateEnabled.value
        if (autoGenerate) {
          newLeak?.let { fetchInsight(it) }
        } else {
          insightJob?.cancel()
        }
      }
    }
  }

  fun setInsightVisible(visible: Boolean) {
    _isInsightVisible.value = visible
    if (visible) {
      trackUiAction(LeakCanaryUiAction.INSIGHT_PANEL_OPENED)
      val selected = selectedLeak
      if (selected != null) {
        val cached = insightCache[selected.signature]
        if (cached != null) {
          _currentInsight.value = cached
        } else {
          _currentInsight.value = LoadingState.Ready(null)
          if (isInsightAutoGenerateEnabled.value) {
            fetchInsight(selected)
          }
        }
      } else {
        _currentInsight.value = LoadingState.Ready(null)
      }
    } else {
      trackUiAction(LeakCanaryUiAction.INSIGHT_PANEL_CLOSED)
      insightJob?.cancel()
    }
  }

  fun fetchInsight(leak: Leak) {
    trackUiAction(LeakCanaryUiAction.INSIGHT_FETCH_TRIGGERED)
    val loadingState = LoadingState.Loading()
    _currentInsight.value = loadingState
    insightCache[leak.signature] = loadingState
    _isInsightVisible.value = true

    insightJob?.cancel()
    insightJob = scope.launch {
      try {
        val flow = ideServices.fetchLeakInsight(leak.toString())
        val result = StringBuilder()
        flow.collect { chunk -> result.append(chunk) }
        val finalResult = result.toString()
        if (finalResult.isEmpty()) {
          trackUiAction(LeakCanaryUiAction.INSIGHT_FETCH_FAILED)
          val emptyState = LoadingState.Failure("AI Assistant returned an empty response.")
          if (selectedLeak == leak) {
            _currentInsight.value = emptyState
          }
          insightCache[leak.signature] = emptyState
        } else {
          trackUiAction(LeakCanaryUiAction.INSIGHT_FETCH_SUCCEEDED)
          val readyState = LoadingState.Ready(AiInsight(finalResult))
          if (selectedLeak == leak) {
            _currentInsight.value = readyState
          }
          insightCache[leak.signature] = readyState
        }
      } catch (e: Exception) {
        if (e is CancellationException) {
          trackUiAction(LeakCanaryUiAction.INSIGHT_FETCH_CANCELLED)
          if (insightCache[leak.signature] === loadingState) {
            insightCache.remove(leak.signature)
          }
          throw e
        }
        trackUiAction(LeakCanaryUiAction.INSIGHT_FETCH_FAILED)
        val errorMessage =
          if (isNetworkError(e)) {
            NETWORK_ERROR_MESSAGE
          } else {
            e.message ?: "Unknown error"
          }
        val failureState = LoadingState.Failure(errorMessage)
        if (selectedLeak == leak) {
          _currentInsight.value = failureState
        }
        insightCache[leak.signature] = failureState
      }
    }
  }

  fun submitInsightFeedback(feedback: InsightFeedback?) {
    val insight = (_currentInsight.value as? LoadingState.Ready)?.value ?: return
    val previousFeedback = insight.feedback
    if (previousFeedback != feedback) {
      val action =
        when (feedback) {
          InsightFeedback.THUMBS_UP -> LeakCanaryUiAction.INSIGHT_SENTIMENT_UP
          InsightFeedback.THUMBS_DOWN -> LeakCanaryUiAction.INSIGHT_SENTIMENT_DOWN
          null -> LeakCanaryUiAction.INSIGHT_SENTIMENT_CLEARED
        }
      trackUiAction(action)
    }
    val newInsightState = LoadingState.Ready(insight.copy(feedback = feedback))
    _currentInsight.value = newInsightState
    selectedLeak?.let { leak -> insightCache[leak.signature] = newInsightState }
  }

  private fun isNetworkError(t: Throwable): Boolean {
    return generateSequence(t) { it.cause }
      .any { it is java.net.UnknownHostException || it is java.net.SocketTimeoutException || it is java.net.SocketException }
  }

  fun clearInsights() {
    insightCache.clear()
    _currentInsight.value = LoadingState.Ready(null)
    insightJob?.cancel()
  }

  companion object {
    private const val KEY_LEAKCANARY_INSIGHT_AUTO_GENERATE = "leakcanary.insight.auto.generate"
    const val NETWORK_ERROR_MESSAGE = "Network connection lost. Please check your internet connection and try again."
  }
}
