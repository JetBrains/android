package com.android.tools.idea.sdk

import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.ApkFacetChecker
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.ProjectFacetManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

/**
 * Tests for [AndroidOrApkFacetChecker]
 */
class AndroidOrApkFacetCheckerTest {
  private val projectRule = ProjectRule()
  private val project get() = projectRule.project
  private val disposableRule = DisposableRule()

  private val mockProjectFacetManager by lazy { Mockito.spy(ProjectFacetManager.getInstance(project)) }
  private val mockApkFacetChecker = MockitoKt.mock<ApkFacetChecker>()

  @get:Rule
  val rule = RuleChain(projectRule, disposableRule)

  @Before
  fun setUp() {
    project.registerOrReplaceServiceInstance(ProjectFacetManager::class.java, mockProjectFacetManager, disposableRule.disposable)
    project.registerOrReplaceServiceInstance(ApkFacetChecker::class.java, mockApkFacetChecker, disposableRule.disposable)
  }

  @Test
  fun isLibraryExists_hasAndroidFacet() {
    whenever(mockProjectFacetManager.hasFacets(AndroidFacet.ID)).thenReturn(true)
    whenever(mockApkFacetChecker.hasApkFacet()).thenReturn(false)

    assertThat(AndroidOrApkFacetChecker().isLibraryExists(project)).isTrue()
  }

  @Test
  fun isLibraryExists_hasApkFacet() {
    whenever(mockProjectFacetManager.hasFacets(AndroidFacet.ID)).thenReturn(false)
    whenever(mockApkFacetChecker.hasApkFacet()).thenReturn(true)

    assertThat(AndroidOrApkFacetChecker().isLibraryExists(project)).isTrue()
  }

  @Test
  fun isLibraryExists_withoutFacets() {
    whenever(mockProjectFacetManager.hasFacets(AndroidFacet.ID)).thenReturn(false)
    whenever(mockApkFacetChecker.hasApkFacet()).thenReturn(false)

    assertThat(AndroidOrApkFacetChecker().isLibraryExists(project)).isFalse()
  }
}