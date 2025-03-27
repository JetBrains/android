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
package com.android.tools.idea.editing.documentation.target

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.downloads.RemoteFileCache.FetchStats
import com.android.tools.idea.downloads.RemoteFileCache.RemoteFileCacheException
import com.android.tools.idea.downloads.UrlFileCache
import com.android.tools.idea.stats.getEditorFileTypeForAnalytics
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.EDITING_METRICS_EVENT
import com.google.wireless.android.sdk.stats.EditorFileType
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.codeInsight.navigation.SingleTargetElementInfo
import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.model.Pointer
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.createSmartPointer
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi

private const val DEV_SITE_ROOT = "http://developer.android.com"
private val HREF_REGEX = Regex("(<A.*?HREF=\")(/[^/])", RegexOption.IGNORE_CASE)

private fun isNavigatableQuickDoc(source: PsiElement?, target: PsiElement) =
  target !== source && target !== source?.parent

/** [DocumentationTarget] for an item in the Android SDK. */
sealed class AndroidSdkDocumentationTarget<T>(
  internal val targetElement: T,
  private val sourceElement: PsiElement?,
  private val url: String,
  private val localJavaDocInfo: String?,
) : DocumentationTarget where T : PsiElement, T : Navigatable {
  /** A [String] we can use to refer to this element. */
  protected abstract val displayName: String?

  override fun computePresentation() = targetPresentation(targetElement)

  override val navigatable = targetElement

  override fun computeDocumentationHint(): String? {
    return SingleTargetElementInfo.generateInfo(
      targetElement,
      sourceElement,
      isNavigatableQuickDoc(sourceElement, targetElement),
    )
  }

  override fun computeDocumentation(): DocumentationResult {
    val deferredPathAndStats =
      UrlFileCache.getInstance(targetElement.project).getWithStats(url, maxFileAge = 1.days) {
        it.filterStream()
      }
    return if (deferredPathAndStats.isCompleted) {
      DocumentationResult.documentation(getDocumentationHtml(deferredPathAndStats)).externalUrl(url)
    } else {
      DocumentationResult.asyncDocumentation {
        deferredPathAndStats.join() // It will be completed after this.
        DocumentationResult.documentation(getDocumentationHtml(deferredPathAndStats))
          .externalUrl(url)
      }
    }
  }

  override fun createPointer(): Pointer<out DocumentationTarget> {
    val targetElementPointer = targetElement.createSmartPointer()
    val sourceElementPointer = sourceElement?.createSmartPointer()
    val url = this.url
    val localJavaDocInfo = this.localJavaDocInfo
    return Pointer {
      val targetElement = targetElementPointer.dereference() ?: return@Pointer null
      val sourceElement = sourceElementPointer?.dereference()
      create(targetElement, sourceElement, url, localJavaDocInfo)
    }
  }

  /**
   * Takes the input from [reader] and writes some of it into [stringBuilder], possibly modifying it
   * along the way. The default implementation uses the platform's
   * [JavaDocExternalFilter.doBuildFromStream] to do this work.
   */
  protected open fun filter(reader: BufferedReader, stringBuilder: StringBuilder) {
    MyFilter(targetElement.project).filterFromStream(reader, stringBuilder)
  }

  /** Creates a new instance of this class with the given parameters. */
  protected abstract fun create(
    targetElement: T,
    sourceElement: PsiElement?,
    url: String,
    localJavaDocInfo: String?,
  ): AndroidSdkDocumentationTarget<T>?

  /**
   * Retrieves the documentation HTML from the file pointed to by [deferredPathAndStats], which must
   * be a completed [Deferred]. We wait to unwrap the [Deferred] until inside this method so we need
   * not write the exception-handling logic twice.
   *
   * This method also logs metrics related to the fetch/display.
   */
  private fun getDocumentationHtml(
    completedDeferredPathAndStats: Deferred<Pair<Path, FetchStats>>
  ): String {
    require(completedDeferredPathAndStats.isCompleted) { "Can only pass a completed Deferred!" }
    @OptIn(ExperimentalCoroutinesApi::class)
    try {
      val (path, stats) = completedDeferredPathAndStats.getCompleted()
      val text = path.readText()
      logFetchStats(stats, text.toByteArray().size)
      path.readText().let { if (it.isNotEmpty()) return it }
    } catch (e: RemoteFileCacheException) {
      logFetchStats(e.fetchStats, numDisplayedHtmlBytes = 0)
      thisLogger().warn("Failure fetching documentation URL.", e.cause)
    } catch (e: Exception) {
      thisLogger().error("Unexpected failure fetching documentation URL.", e)
    }

    if (localJavaDocInfo != null) return localJavaDocInfo

    thisLogger().error("Couldn't get local java docs for $displayName.")
    return "Unable to load documentation for <code>$displayName</code>."
  }

  /**
   * Filters `this` [InputStream] to a new [InputStream] by invoking [filter] and fixing up URL
   * links.
   */
  private fun InputStream.filterStream(): InputStream {
    val stringBuilder = StringBuilder()
    filter(BufferedReader(InputStreamReader(this)), stringBuilder)
    // This is an ugly hack to replace relative URL links with absolute links so that the platform
    // can correctly follow them. It would be best not to do this, but it's equivalent to what the
    // platform was already doing in JavaDocExternalFilter. Once we are rendering doc pages on the
    // server formatted specifically for Studio, we should be able to remove this.
    val corrected = HREF_REGEX.replace(stringBuilder.toString(), "$1$DEV_SITE_ROOT$2")
    return ByteArrayInputStream(corrected.toByteArray())
  }

  /** This class exists only to expose [JavaDocExternalFilter.doBuildFromStream]. */
  private inner class MyFilter(project: Project) : JavaDocExternalFilter(project) {
    /** Exposes [JavaDocExternalFilter.doBuildFromStream]. */
    fun filterFromStream(reader: Reader, stringBuilder: StringBuilder) {
      doBuildFromStream(url, reader, stringBuilder, true, true)
    }
  }

  private fun logFetchStats(fetchStats: FetchStats, numDisplayedHtmlBytes: Int) {
    val builder =
      AndroidStudioEvent.newBuilder().setKind(EDITING_METRICS_EVENT).apply {
        editingMetricsEventBuilder.apply {
          externalQuickDocEventBuilder.apply {
            fileType =
              sourceElement?.language?.id?.let(::getEditorFileTypeForAnalytics)
                ?: EditorFileType.UNKNOWN
            fetchDurationMs = fetchStats.fetchDuration.inWholeMilliseconds
            success = fetchStats.success
            cacheHit = fetchStats.cacheHit
            serverNotModified = fetchStats.notModified
            numBytesFetched = fetchStats.numBytesFetched
            numBytesCached = fetchStats.numBytesCached
            numBytesDisplayed = numDisplayedHtmlBytes.toLong()
          }
        }
      }
    UsageTracker.log(builder)
  }

  companion object {
    /** Returns the standard URL for the documentation for `this` [PsiClass]. */
    @JvmStatic
    protected fun PsiClass.documentationUrl(): String? {
      val relPath = qualifiedName?.replace('.', '/') ?: return null
      return "$DEV_SITE_ROOT/reference/$relPath.html"
    }

    /**
     * Creates the appropriate kind of [AndroidSdkDocumentationTarget] for [element], or `null` if
     * there is none.
     */
    fun create(element: PsiElement, originalElement: PsiElement?) =
      when (element) {
        is PsiClass -> AndroidSdkClassDocumentationTarget.create(element, originalElement)
        is PsiMethod -> AndroidSdkMethodDocumentationTarget.create(element, originalElement)
        is PsiField -> AndroidSdkFieldDocumentationTarget.create(element, originalElement)
        else -> null
      }
  }
}
