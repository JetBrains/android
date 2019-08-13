/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.model

import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Modal
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import org.jetbrains.android.util.AndroidBundle.message

/**
 * Sometimes there are several separate classes which want to render templates, in some order, but the whole process should be aborted if
 * any of them fail a validation pass. This class acts as a central way to coordinate such render request.
 */
class MultiTemplateRenderer(private var project: Project?, private val projectSyncInvoker: ProjectSyncInvoker) {
  interface TemplateRendererListener {
    /**
     * Called just before rendering multiple templates. Since rendering typically involves adding quite a lot of files
     * to the project, this callback is useful to prevent other file-intensive operations such as indexing.
     */
    fun multiRenderingStarted() {}

    /**
     * Called when the last template in the series has been rendered.
     */
    fun multiRenderingFinished() {}
  }

  interface TemplateRenderer {
    /**
     * Runs any needed Model pre-initialisation, for example, setting Template default values.
     */
    @WorkerThread
    fun init() {
    }

    /**
     * Run validation, but don't write any file
     *
     * @return true if the validation succeeded. Returning false will stop any call to [render]
     */
    @WorkerThread
    @Slow
    fun doDryRun(): Boolean

    /**
     * Do the actual work of writing the files.
     */
    @WorkerThread
    @Slow
    fun render()

    /**
     * Runs any needed Model finalization, for example, after generating a project, import it or open generated files on the editor.
     */
    @UiThread
    fun finish() {
    }
  }

  private val templateRenderers: MutableList<TemplateRenderer> = mutableListOf()
  private var requestCount = 1
  /**
   * When creating a new Project, the new Project instance is only available after the [MultiTemplateRenderer] is created.
   * Use this method to set Project instance later.
   *
   * @param project
   */
  fun setProject(project: Project) {
    this.project = project
  }

  /**
   * Call this method to indicate that one more render is available. Every call to this method needs to be later matched by a
   * call to either [requestRender] or [skipRender]
   */
  fun incrementRenders() {
    requestCount++
  }

  /**
   * Enqueue a template render request, batching it into a collection that will all be validated and, if all valid, rendered at some
   * later time.
   * Note: This class is intended to be used once and discarded. If you enqueue renderers after the previous renderers have executed,
   * this method's behavior may not work as expected.
   */
  fun requestRender(templateRenderer: TemplateRenderer) {
    templateRenderers.add(templateRenderer)
    countDown()
  }

  /**
   * Skip a template render request, any pending batching collection will be all validated and, if all valid, rendered at some
   * later time.
   * Note: This class is intended to be used once and discarded. If you enqueue renderers after the previous renderers have executed,
   * this method's behavior may not work as expected.
   */
  fun skipRender() = countDown()

  /**
   * Process batched requests. When all requests are accounted ([incrementRenders] == [requestRender] + [skipRender]), we check that all
   * requests are valid, and if they are, run render them all.
   */
  private fun countDown() {
    check(requestCount != 0) { "Invalid extra call to MultiTemplateRenderer#countDown" }
    requestCount--
    if (requestCount != 0 || templateRenderers.isEmpty()) {
      return
    }
    checkNotNull(project) { "Project instance is always expected to be not null at this point." }
    object : Modal(project, message("android.compile.messages.generating.r.java.content.name"), false) {
      override fun run(indicator: ProgressIndicator) {
        multiRenderingStarted(myProject)

        // Some models need to access other models data, during doDryRun/render phase. By calling init() in all of them first,
        // we make sure they are properly initialized when doDryRun/render is called bellow.
        with(templateRenderers) {
          forEach(TemplateRenderer::init)
          if (!all(TemplateRenderer::doDryRun)) return
          forEach(TemplateRenderer::render)
        }

        if (myProject.isInitialized) {
          projectSyncInvoker.syncProject(myProject)
        }
      }

      override fun onFinished() {
        multiRenderingFinished(myProject)
        templateRenderers.forEach(TemplateRenderer::finish)
      }
    }.queue()
  }

  companion object {
    private val TEMPLATE_RENDERER_TOPIC =
      Topic("Template rendering", TemplateRendererListener::class.java)

    fun subscribe(project: Project, listener: TemplateRendererListener): MessageBusConnection =
      project.messageBus.connect(project).apply {
        subscribe(TEMPLATE_RENDERER_TOPIC, listener)
      }

    fun multiRenderingStarted(project: Project) = project.messageBus.syncPublisher(TEMPLATE_RENDERER_TOPIC).multiRenderingStarted()

    fun multiRenderingFinished(project: Project) = project.messageBus.syncPublisher(TEMPLATE_RENDERER_TOPIC).multiRenderingFinished()
  }
}

