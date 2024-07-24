// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package com.android.tools.idea.nav.safeargs.module.gradle

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.TestDataPaths
import com.android.tools.idea.nav.safeargs.extensions.replaceWithoutSaving
import com.android.tools.idea.nav.safeargs.module.NavInfoChangeReason
import com.android.tools.idea.nav.safeargs.module.NavInfoFetcher
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesModificationListener
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.android.tools.idea.nav.safeargs.waitForPendingUpdates
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.fileUnderGradleRoot
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.util.EnumSet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class NavInfoFetcherTest {
  val projectRule = AndroidGradleProjectRule()

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @get:Rule val flagRule = FlagRule(StudioFlags.SKIP_NAV_INFO_DUMB_MODE_CHECK)

  private val changeReasons: MutableSet<NavInfoChangeReason> =
    EnumSet.noneOf(NavInfoChangeReason::class.java)
  private var baselineModificationCount = 0L
  private lateinit var module: Module
  private lateinit var fetcher: NavInfoFetcher

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(TestDataPaths.SIMPLE_KOTLIN_PROJECT)
    NavigationResourcesModificationListener.ensureSubscribed(projectRule.project)

    module = projectRule.getModule("app.main")
    fetcher =
      NavInfoFetcher(projectRule.fixture.testRootDisposable, module, SafeArgsMode.KOTLIN) {
        changeReason ->
        changeReasons.add(changeReason)
      }
    baselineModificationCount = fetcher.modificationCount
    changeReasons.clear()
  }

  @Test
  fun containsCorrectEntries() {
    val navInfo = fetcher.getCurrentNavInfo()
    assertThat(navInfo).isNotNull()
    navInfo!!
    assertThat(navInfo.packageName).isEqualTo("com.example.myapplication")
    assertThat(navInfo.facet.module).isEqualTo(module)
    assertThat(navInfo.modificationCount).isEqualTo(fetcher.modificationCount)

    assertThat(navInfo.entries).hasSize(1)
    val entry = navInfo.entries.single()

    assertThat(entry.file.name).isEqualTo("nav_graph.xml")
    assertThat(entry.facet).isSameAs(navInfo.facet)

    entry.data.root.apply {
      assertThat(id).isEqualTo("nav_graph")
      assertThat(startDestination).isEqualTo("FirstFragment")
      assertThat(actions).isEmpty()
      assertThat(arguments).isEmpty()
      assertThat(navigations).isEmpty()

      val destinations = potentialDestinations.mapNotNull { it.toDestination() }
      assertThat(destinations).hasSize(2)
      val (firstDest, secondDest) =
        if (destinations[0].id == "FirstFragment") {
          destinations
        } else {
          destinations.reversed()
        }

      firstDest.apply {
        assertThat(id).isEqualTo("FirstFragment")
        assertThat(name).isEqualTo("com.example.mylibrary.FirstFragment")

        assertThat(arguments).hasSize(2)
        val (arg1, arg2) = firstDest.arguments.sortedBy { it.name }
        arg1.apply {
          assertThat(name).isEqualTo("arg1")
          assertThat(type).isEqualTo("integer")
          assertThat(defaultValue).isNull()
        }
        arg2.apply {
          assertThat(name).isEqualTo("arg2")
          assertThat(type).isEqualTo("integer")
          assertThat(defaultValue).isNull()
        }

        assertThat(actions).hasSize(1)
        actions.single().apply {
          assertThat(id).isEqualTo("action_FirstFragment_to_SecondFragment")
          assertThat(destination).isEqualTo("SecondFragment")
          assertThat(popUpTo).isNull()
        }
      }

      secondDest.apply {
        assertThat(id).isEqualTo("SecondFragment")
        assertThat(name).isEqualTo("com.example.myapplication.SecondFragment")

        assertThat(arguments).hasSize(1)
        arguments.single().apply {
          assertThat(name).isEqualTo("arg1")
          assertThat(type).isEqualTo("integer")
        }

        assertThat(actions).hasSize(1)
        actions.single().apply {
          assertThat(id).isEqualTo("action_SecondFragment_to_FirstFragment")
          assertThat(destination).isEqualTo("FirstFragment")
          assertThat(popUpTo).isNull()
        }
      }
    }
  }

  @Test
  fun updatesOnNavFileChange() {
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      module
        .fileUnderGradleRoot("src/main/res/navigation/nav_graph.xml")!!
        .replaceWithoutSaving("@+id/FirstFragment", "@+id/FirstFragmentChanged", module.project)
    }
    waitForPendingUpdates(module)

    assertModified(NavInfoChangeReason.NAVIGATION_RESOURCE_CHANGED)
    assertThat(
        fetcher.getCurrentNavInfo()!!.entries.single().data.resolvedDestinations.singleOrNull {
          it.id == "FirstFragmentChanged"
        }
      )
      .isNotNull()
  }

  @Test
  fun updatesOnSafeArgsModeChange() {
    module.androidFacet!!.safeArgsMode = SafeArgsMode.JAVA

    assertModified(NavInfoChangeReason.SAFE_ARGS_MODE_CHANGED)
    assertThat(fetcher.getCurrentNavInfo()).isNull()
    assertThat(fetcher.isEnabled).isFalse()

    module.androidFacet!!.safeArgsMode = SafeArgsMode.KOTLIN

    assertModified(NavInfoChangeReason.SAFE_ARGS_MODE_CHANGED)
    assertThat(fetcher.getCurrentNavInfo()).isNotNull()
    assertThat(fetcher.isEnabled).isTrue()
  }

  @Test
  fun updatesOnProjectSync() {
    module.project.messageBus
      .syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC)
      .syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS)

    assertModified(NavInfoChangeReason.GRADLE_SYNC)
  }

  @Test
  fun updatesOnDumbModeChange_dumbModeCheckOn() = runBlocking {
    StudioFlags.SKIP_NAV_INFO_DUMB_MODE_CHECK.override(false)

    DumbModeTestUtils.runInDumbModeSynchronously(module.project) {
      assertModified(NavInfoChangeReason.DUMB_MODE_CHANGED)
      assertThat(fetcher.isEnabled).isTrue()
      assertThat(fetcher.getCurrentNavInfo()).isNull()
    }

    assertModified(NavInfoChangeReason.DUMB_MODE_CHANGED)
    assertThat(fetcher.getCurrentNavInfo()).isNotNull()
  }

  @Test
  fun updatesOnDumbModeChange_dumbModeCheckOff() = runBlocking {
    StudioFlags.SKIP_NAV_INFO_DUMB_MODE_CHECK.override(true)

    DumbModeTestUtils.runInDumbModeSynchronously(module.project) {
      assertModified(NavInfoChangeReason.DUMB_MODE_CHANGED)
      assertThat(fetcher.isEnabled).isTrue()

      assertThrows(IndexNotReadyException::class.java) { fetcher.getCurrentNavInfo() }
    }
  }

  private fun assertModified(vararg changeReasons: NavInfoChangeReason) {
    assertThat(fetcher.modificationCount).isGreaterThan(baselineModificationCount)
    baselineModificationCount = fetcher.modificationCount
    assertThat(this.changeReasons).containsExactlyElementsIn(changeReasons)
    this.changeReasons.clear()
  }
}
