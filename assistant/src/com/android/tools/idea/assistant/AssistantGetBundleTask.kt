package com.android.tools.idea.assistant

import com.android.tools.idea.assistant.datamodel.TutorialBundleData
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.apache.http.concurrent.FutureCallback

class AssistantGetBundleTask(
  project: Project,
  private val bundleCreator: AssistantBundleCreator,
  private val uiCallback: FutureCallback<TutorialBundleData>,
) : Task.Backgroundable(project, "Loading assistant content...") {
  private lateinit var bundleData: TutorialBundleData

  override fun run(indicator: ProgressIndicator) {
    bundleData = bundleCreator.getBundle(project) ?: throw IllegalStateException()
  }

  override fun onSuccess() {
    super.onSuccess()
    uiCallback.completed(bundleData)
  }

  override fun onCancel() {
    super.onCancel()
    uiCallback.cancelled()
  }

  override fun onThrowable(error: Throwable) {
    uiCallback.failed(RuntimeException(error))
  }
}
