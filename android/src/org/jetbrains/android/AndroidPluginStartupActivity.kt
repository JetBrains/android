package org.jetbrains.android

import com.android.tools.idea.util.VirtualFileSystemOpener.mount
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private class AndroidPluginStartupActivity : ProjectActivity {

  override suspend fun execute(project: Project) = mount()
}
