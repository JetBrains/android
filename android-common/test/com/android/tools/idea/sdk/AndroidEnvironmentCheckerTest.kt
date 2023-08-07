package com.android.tools.idea.sdk

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.ApkFacetChecker
import com.android.tools.idea.IdeInfo
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.testFramework.replaceService
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

/**
 * Tests for [AndroidEnvironmentChecker]
 */
class AndroidEnvironmentCheckerTest {
  private val projectRule = ProjectRule()
  private val project get() = projectRule.project
  private val disposableRule = DisposableRule()
  private val disposable get() = disposableRule.disposable

  @get:Rule
  val rule = RuleChain(projectRule, disposableRule)

  private val mockIdeInfo by lazy { Mockito.spy(IdeInfo.getInstance()) }
  private val mockApkFacetChecker = mock<ApkFacetChecker>()

  @Test
  fun isLibraryExists() {
    assertThat(AndroidEnvironmentChecker().isLibraryExists(project)).isTrue()
  }

  @Test
  fun isLibraryExists_notAndroidStudio_withoutAndroidFacet() {
    whenever(mockIdeInfo.isAndroidStudio).thenReturn(false)
    whenever(mockApkFacetChecker.hasApkFacet()).thenReturn(false)

    ApplicationManager.getApplication().replaceService(IdeInfo::class.java, mockIdeInfo, disposable)
    project.registerOrReplaceServiceInstance(ApkFacetChecker::class.java, mockApkFacetChecker, disposable)

    assertThat(AndroidEnvironmentChecker().isLibraryExists(project)).isFalse()
  }

  @Test
  fun isLibraryExists_notAndroidStudio_withAndroidFacet() {
    whenever(mockIdeInfo.isAndroidStudio).thenReturn(false)
    whenever(mockApkFacetChecker.hasApkFacet()).thenReturn(true)

    ApplicationManager.getApplication().replaceService(IdeInfo::class.java, mockIdeInfo, disposable)
    project.registerOrReplaceServiceInstance(ApkFacetChecker::class.java, mockApkFacetChecker, disposable)

    assertThat(AndroidEnvironmentChecker().isLibraryExists(project)).isTrue()
  }
}