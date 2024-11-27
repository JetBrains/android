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
package com.android.tools.idea.editing.documentation

import com.android.SdkConstants
import com.android.tools.idea.downloads.UrlFileCache
import com.android.tools.idea.editing.documentation.AndroidJavaDocExternalFilter.Companion.filterTo
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.codeInsight.navigation.SingleTargetElementInfo
import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.model.Pointer
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.days

private const val DEV_SITE_ROOT = "http://developer.android.com"
private val HREF_REGEX = Regex("(<A.*?HREF=\")(/[^/])", RegexOption.IGNORE_CASE)

private fun isNavigatableQuickDoc(source: PsiElement?, target: PsiElement) =
  target !== source && target !== source?.parent

/**
 * A [PsiDocumentationTargetProvider] which can fetch documentation for classes defined in the
 * Android SDK from developer.android.com. This only runs when javadocs are not available from
 * locally-downloaded SDK sources, and only for class documentation (not methods, fields, etc).
 */
class AndroidSdkDocumentationTargetProvider : PsiDocumentationTargetProvider {
  override fun documentationTarget(
    element: PsiElement,
    originalElement: PsiElement?,
  ): DocumentationTarget? {
    if (!element.project.getProjectSystem().isAndroidProject()) return null

    // This handler only deals with external documentation for framework classes.
    if (element !is PsiClass) return null

    // If sources exist (and therefore have javadocs), the navigation element will not be in
    // android.jar. In that case, fall back to the standard java documentation handler.
    if (!element.navigationElement.isInAndroidSdkJar()) return null

    return AndroidSdkClassDocumentationTarget.create(element, originalElement)
  }

  private fun PsiElement.isInAndroidSdkJar(): Boolean {
    val virtualFile = containingFile?.virtualFile ?: return false
    return JarFileSystem.getInstance().getVirtualFileForJar(virtualFile)?.name ==
      SdkConstants.FN_FRAMEWORK_LIBRARY
  }
}

private fun InputStream.filter(): InputStream {
  val stringBuilder = StringBuilder()
  BufferedReader(InputStreamReader(this)).filterTo(stringBuilder)
  // This is an ugly hack to replace relative URL links with absolute links so that the platform
  // can correctly follow them. It would be best not to do this, but it's equivalent to what the
  // platform was already doing in JavaDocExternalFilter. Once we are rendering doc pages on the
  // server formatted specifically for Studio, we should be able to remove this.
  val corrected = HREF_REGEX.replace(stringBuilder.toString(), "$1$DEV_SITE_ROOT$2")
  return ByteArrayInputStream(corrected.toByteArray())
}

/** A [DocumentationTarget] representing a class in the Android SDK. */
private class AndroidSdkClassDocumentationTarget
private constructor(
  val targetElement: PsiClass,
  val sourceElement: PsiElement?,
  val url: String,
  val localJavaDocInfo: String?,
) : DocumentationTarget {
  override fun computePresentation() = targetPresentation(targetElement)

  override fun createPointer(): Pointer<out DocumentationTarget> {
    val targetElementPointer = targetElement.createSmartPointer()
    val sourceElementPointer = sourceElement?.createSmartPointer()
    val url = this.url
    val localJavaDocInfo = this.localJavaDocInfo
    return Pointer {
      val targetElement = targetElementPointer.dereference() ?: return@Pointer null
      val sourceElement = sourceElementPointer?.dereference()
      AndroidSdkClassDocumentationTarget(targetElement, sourceElement, url, localJavaDocInfo)
    }
  }

  override val navigatable: Navigatable
    get() = targetElement

  override fun computeDocumentationHint(): String? {
    return SingleTargetElementInfo.generateInfo(
      targetElement,
      sourceElement,
      isNavigatableQuickDoc(sourceElement, targetElement),
    )
  }

  override fun computeDocumentation(): DocumentationResult {
    val deferredPath =
      UrlFileCache.getInstance(targetElement.project).get(url, maxFileAge = 1.days) { it.filter() }
    return if (deferredPath.isCompleted) {
      DocumentationResult.documentation(runBlocking { getDocumentationHtml(deferredPath) })
        .externalUrl(url)
    } else {
      DocumentationResult.asyncDocumentation {
        DocumentationResult.documentation(getDocumentationHtml(deferredPath)).externalUrl(url)
      }
    }
  }

  private suspend fun getDocumentationHtml(deferredPath: Deferred<Path>): String {
    try {
      deferredPath.await().readText().let { if (it.isNotEmpty()) return it }
    } catch (e: Exception) {
      thisLogger().warn("Failed to fetch documentation URL.", e)
    }

    if (localJavaDocInfo != null) return localJavaDocInfo

    thisLogger().error("Couldn't get local java docs for ${targetElement.qualifiedName}.")
    return "Unable to load documentation for <code>${targetElement.qualifiedName}</code>."
  }

  companion object {
    /**
     * Creates [DocumentationTarget] representing a class in the Android SDK. [targetElement] points
     * to the class in the Android SDK that needs documentation, and [sourceElement] represents the
     * original reference to that class from which the user is requesting the documentation.
     */
    fun create(targetElement: PsiClass, sourceElement: PsiElement?): DocumentationTarget? {
      val relPath = targetElement.qualifiedName?.replace('.', '/') ?: return null
      val url = "$DEV_SITE_ROOT/reference/$relPath.html"

      val localJavaDocInfo =
        JavaDocInfoGenerator(targetElement.project, targetElement).generateDocInfo(null)

      return AndroidSdkClassDocumentationTarget(targetElement, sourceElement, url, localJavaDocInfo)
    }
  }
}
