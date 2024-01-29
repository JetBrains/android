package com.android.tools.idea.sdk

import com.android.testutils.MockitoKt.whenever
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
 * Tests for [AndroidProjectChecker]
 */
class AndroidProjectCheckerTest {
  private val projectRule = ProjectRule()
  private val project get() = projectRule.project
  private val disposableRule = DisposableRule()

  private val mockProjectFacetManager by lazy { Mockito.spy(ProjectFacetManager.getInstance(project)) }

  @get:Rule
  val rule = RuleChain(projectRule, disposableRule)

  @Before
  fun setUp() {
    project.registerOrReplaceServiceInstance(ProjectFacetManager::class.java, mockProjectFacetManager, disposableRule.disposable)
  }

  @Test
  fun isLibraryExists() {
    whenever(mockProjectFacetManager.hasFacets(AndroidFacet.ID)).thenReturn(true)

    assertThat(AndroidProjectChecker().isLibraryExists(project)).isTrue()
  }

  @Test
  fun isLibraryExists_withoutAndroidFacet() {
    whenever(mockProjectFacetManager.hasFacets(AndroidFacet.ID)).thenReturn(false)

    assertThat(AndroidProjectChecker().isLibraryExists(project)).isFalse()
  }
}