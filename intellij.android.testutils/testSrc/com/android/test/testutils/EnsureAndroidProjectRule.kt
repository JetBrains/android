// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.test.testutils

import com.android.tools.idea.util.CommonAndroidUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService

/**
 * This test rule ensures the project is an Android project.
 * This rule can be used in AOSP's tests where Google implicitly assumes this.
 */
class EnsureAndroidProjectRule : DisposableRule() {
  private var utils: CommonAndroidUtil? = null

  private class MockAndroidUtil : CommonAndroidUtil() {
    override fun isAndroidProject(project: Project): Boolean = true
  }

  override fun before() {
    super.before()
    utils = service()
    ApplicationManager.getApplication().replaceService(CommonAndroidUtil::class.java, MockAndroidUtil(), disposable)
  }

  override fun after() {
    utils?.let { ApplicationManager.getApplication().replaceService(CommonAndroidUtil::class.java, it, disposable) }
    super.after()
  }
}
