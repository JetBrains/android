package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.preview.util.DelayedLruActionQueue
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.time.Duration

@Service
internal class PreviewProjectService(project: Project) {
  internal val deactivationQueue = DelayedLruActionQueue(5, Duration.ofMinutes(5))
}