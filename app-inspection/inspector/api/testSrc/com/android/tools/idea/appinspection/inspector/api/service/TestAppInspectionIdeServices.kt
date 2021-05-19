package com.android.tools.idea.appinspection.inspector.api.service

import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices.CodeLocation
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices.Severity

/**
 * Simple adapter class that provides stubs / simple defaults for test versions of this class.
 */
open class TestAppInspectionIdeServices : AppInspectionIdeServices {
  override fun showToolWindow(callback: () -> Unit) {}
  override fun showNotification(content: String, title: String, severity: Severity, hyperlinkClicked: () -> Unit) {}
  override suspend fun navigateTo(codeLocation: CodeLocation) {}
  override fun createFileService() = TestFileService()
}