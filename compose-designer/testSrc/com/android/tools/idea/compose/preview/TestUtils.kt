/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.preview.util.PreviewElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod

/**
 * List of variations of namespaces to be tested by the Compose tests. This is done
 * to support the name migration. We test the old/new preview annotation names with the
 * old/new composable annotation names.
 */
internal val namespaceVariations = listOf(
  arrayOf("androidx.ui.tooling.preview", "androidx.compose"),
  arrayOf("androidx.ui.tooling.preview", "androidx.compose.runtime"),
  arrayOf("androidx.compose.ui.tooling.preview", "androidx.compose"),
  arrayOf("androidx.compose.ui.tooling.preview", "androidx.compose.runtime")
)

internal fun UFile.declaredMethods(): Sequence<UMethod> =
  classes
    .asSequence()
    .flatMap { it.methods.asSequence() }

internal fun UFile.method(name: String): UMethod? =
  declaredMethods()
    .filter { it.name == name }
    .singleOrNull()

internal class StaticPreviewProvider<P : PreviewElement>(private val collection: Collection<P>) : PreviewElementProvider<P> {
  override val previewElements: Sequence<P>
    get() = collection.asSequence()
}

/**
 * Invalidates the file document to ensure it is reloaded from scratch. This will ensure that we run the code path that requires
 * the read lock and we ensure that the handling of files is correctly done in the right thread.
 */
private fun PsiFile.invalidateDocumentCache() = ApplicationManager.getApplication().invokeAndWait {
  val cachedDocument = PsiDocumentManager.getInstance(project).getCachedDocument(this) ?: return@invokeAndWait
  // Make sure it is invalidated
  cachedDocument.putUserData(FileDocumentManagerImpl.NOT_RELOADABLE_DOCUMENT_KEY, true)
  FileDocumentManager.getInstance().reloadFiles(virtualFile)
}

/**
 * Same as [CodeInsightTestFixture.addFileToProject] but invalidates immediately the cached document.
 * This ensures that the code immediately after this does not work with a cached version and reloads it from disk. This
 * ensures that the loading from disk is executed and the code path that needs the read lock will be executed.
 * The idea is to help detecting code paths that require the [ReadAction] during testing.
 */
fun CodeInsightTestFixture.addFileToProjectAndInvalidate(relativePath: String, fileText: String): PsiFile =
  addFileToProject(relativePath, fileText).also {
    it.invalidateDocumentCache()
  }
