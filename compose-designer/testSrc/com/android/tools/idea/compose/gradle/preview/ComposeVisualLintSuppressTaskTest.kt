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
package com.android.tools.idea.compose.gradle.preview

import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.AccessibilityModelUpdater
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.gradle.getPsiFile
import com.android.tools.idea.compose.preview.ComposeAdapterLightVirtualFile
import com.android.tools.idea.compose.preview.ComposeVisualLintIssueProvider
import com.android.tools.idea.compose.preview.ComposeVisualLintSuppressTask
import com.android.tools.idea.compose.preview.PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.getPreviewNodes
import com.android.tools.idea.preview.rendering.createRenderTaskFuture
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.NlModelHierarchyUpdater
import com.android.tools.idea.uibuilder.scene.accessibilityBasedHierarchyParser
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.ButtonSizeAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.TextFieldSizeAnalyzer
import com.android.tools.preview.applyTo
import com.android.tools.rendering.RenderResult
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.toUElementOfType
import org.junit.Rule
import org.junit.Test

class ComposeVisualLintSuppressTaskTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  @Test
  fun testSuppressIssue() {
    val facet = projectRule.androidFacet(":app")
    val (targetFile, previewElement) =
      runBlocking {
        val psiFile =
          getPsiFile(
            projectRule.project,
            "app/src/main/java/google/simpleapplication/VisualLintPreview.kt",
          )
        psiFile.virtualFile to
          runReadAction {
              PsiTreeUtil.findChildrenOfType(psiFile, KtAnnotationEntry::class.java)
                .asSequence()
                .mapNotNull { it.psiOrParent.toUElementOfType<UAnnotation>() }
                .mapNotNull { it.getContainingUMethod() }
                .toSet()
                .flatMap { getPreviewNodes(it, null, false) }
                .filterIsInstance<PsiComposePreviewElementInstance>()
                .toList()
            }
            .first {
              it.methodFqn == "google.simpleapplication.VisualLintPreviewKt.VisualLintErrorPreview"
            }
      }
    val file =
      ComposeAdapterLightVirtualFile(
        "compose-model.xml",
        previewElement.toPreviewXml().buildString(),
        targetFile,
      )
    val renderTaskFuture =
      createRenderTaskFuture(
        facet = facet,
        file = file,
        privateClassLoader = false,
        useLayoutScanner = false,
        classesToPreload = emptyList(),
        customViewInfoParser = accessibilityBasedHierarchyParser,
        configure = previewElement::applyTo,
      )

    val renderResultFuture =
      CompletableFuture.supplyAsync(
          { renderTaskFuture.get() },
          AppExecutorUtil.getAppExecutorService(),
        )
        .thenCompose { it?.render() ?: CompletableFuture.completedFuture(null as RenderResult?) }
    renderResultFuture.handle { _, _ -> renderTaskFuture.get().dispose() }
    val renderResult = renderResultFuture.get()!!
    val nlModel =
      SyncNlModel.create(projectRule.fixture.testRootDisposable, NlComponentRegistrar, facet, file)
    nlModel.dataContext = DataContext {
      when (it) {
        PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name -> previewElement
        else -> null
      }
    }
    nlModel.setModelUpdater(AccessibilityModelUpdater())
    NlModelHierarchyUpdater.updateHierarchy(renderResult, nlModel)

    val issueProvider = ComposeVisualLintIssueProvider(projectRule.fixture.testRootDisposable)
    val buttonIssues =
      ButtonSizeAnalyzer.analyze(renderResult, nlModel, HighlightSeverity.WARNING, false)
    assertEquals(1, buttonIssues.size)
    val textFieldIssues =
      TextFieldSizeAnalyzer.analyze(renderResult, nlModel, HighlightSeverity.WARNING, false)
    assertEquals(1, textFieldIssues.size)
    issueProvider.addAllIssues(buttonIssues)
    issueProvider.addAllIssues(textFieldIssues)

    assertEquals(2, issueProvider.getIssues().size)
    assertEquals(2, issueProvider.getUnsuppressedIssues().size)

    ComposeVisualLintSuppressTask(
        facet,
        projectRule.project,
        previewElement,
        VisualLintErrorType.BUTTON_SIZE,
      )
      .run()
    assertEquals(2, issueProvider.getIssues().size)
    assertEquals(1, issueProvider.getUnsuppressedIssues().size)
    assertEquals(VisualLintErrorType.TEXT_FIELD_SIZE, issueProvider.getUnsuppressedIssues()[0].type)

    ComposeVisualLintSuppressTask(
        facet,
        projectRule.project,
        previewElement,
        VisualLintErrorType.TEXT_FIELD_SIZE,
      )
      .run()
    assertEquals(2, issueProvider.getIssues().size)
    assertEquals(0, issueProvider.getUnsuppressedIssues().size)
  }

  @Test
  fun testShowSuppressAction() {
    val facet = projectRule.androidFacet(":app")
    val (targetFile, previewElement) =
      runBlocking {
        val psiFile =
          getPsiFile(
            projectRule.project,
            "app/src/main/java/google/simpleapplication/VisualLintPreview.kt",
          )
        psiFile.virtualFile to
          runReadAction {
              PsiTreeUtil.findChildrenOfType(psiFile, KtAnnotationEntry::class.java)
                .asSequence()
                .mapNotNull { it.psiOrParent.toUElementOfType<UAnnotation>() }
                .mapNotNull { it.getContainingUMethod() }
                .toSet()
                .flatMap { getPreviewNodes(it, null, false) }
                .filterIsInstance<PsiComposePreviewElementInstance>()
                .toList()
            }
            .first {
              it.methodFqn == "google.simpleapplication.VisualLintPreviewKt.VisualLintErrorPreview"
            }
      }
    val file =
      ComposeAdapterLightVirtualFile(
        "compose-model.xml",
        previewElement.toPreviewXml().buildString(),
        targetFile,
      )
    val renderTaskFuture =
      createRenderTaskFuture(
        facet = facet,
        file = file,
        privateClassLoader = false,
        useLayoutScanner = false,
        classesToPreload = emptyList(),
        customViewInfoParser = accessibilityBasedHierarchyParser,
        configure = previewElement::applyTo,
      )

    val renderResultFuture =
      CompletableFuture.supplyAsync(
          { renderTaskFuture.get() },
          AppExecutorUtil.getAppExecutorService(),
        )
        .thenCompose { it?.render() ?: CompletableFuture.completedFuture(null as RenderResult?) }
    renderResultFuture.handle { _, _ -> renderTaskFuture.get().dispose() }
    val renderResult = renderResultFuture.get()!!
    val nlModel =
      SyncNlModel.create(projectRule.fixture.testRootDisposable, NlComponentRegistrar, facet, file)
    nlModel.dataContext = DataContext {
      when (it) {
        PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name -> previewElement
        else -> null
      }
    }
    nlModel.setModelUpdater(AccessibilityModelUpdater())
    NlModelHierarchyUpdater.updateHierarchy(renderResult, nlModel)

    val issueProvider = ComposeVisualLintIssueProvider(projectRule.fixture.testRootDisposable)
    val buttonIssues =
      ButtonSizeAnalyzer.analyze(renderResult, nlModel, HighlightSeverity.WARNING, false)
    assertEquals(1, buttonIssues.size)
    val textFieldIssues =
      TextFieldSizeAnalyzer.analyze(renderResult, nlModel, HighlightSeverity.WARNING, false)
    assertEquals(1, textFieldIssues.size)
    issueProvider.addAllIssues(buttonIssues)
    issueProvider.addAllIssues(textFieldIssues)

    run {
      val buttonSuppressTasks = buttonIssues[0].suppresses.toList()
      assertEquals(1, buttonSuppressTasks.size)
    }

    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      previewElement.previewElementDefinition!!.element?.delete()
    }
    run {
      val buttonSuppressTasks = buttonIssues[0].suppresses.toList()
      assertEquals(0, buttonSuppressTasks.size)
    }
  }
}
