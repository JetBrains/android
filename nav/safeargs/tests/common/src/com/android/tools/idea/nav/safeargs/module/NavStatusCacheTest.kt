// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package com.android.tools.idea.nav.safeargs.module

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.nav.safeargs.psi.SafeArgsFeatureVersions
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.EnumSet

class NavStatusCacheTest {
  @get:Rule val safeArgsRule = SafeArgsRule(SafeArgsMode.KOTLIN)

  private val changeReasons: MutableSet<NavInfoChangeReason> =
    EnumSet.noneOf(NavInfoChangeReason::class.java)
  private val fetcher = mock<NavInfoFetcherBase>()
  private lateinit var invalidate: (NavInfoChangeReason) -> Unit
  private lateinit var computeStatus: (NavInfo) -> Any
  private lateinit var cache: NavStatusCache<Any>

  @Before
  fun setUp() {
    whenever(fetcher.isEnabled).thenReturn(true)
    cache =
      NavStatusCache(
        onCacheInvalidate = { changeReasons.add(it) },
        update = { computeStatus(it) },
        navInfoFetcherFactory = factory@{
            invalidate = it
            return@factory fetcher
          },
      )
    setNavInfo(0L)
  }

  @Test
  fun cachesValues() {
    computeStatus = { "foo" }
    assertThat(cache.currentStatus).isEqualTo("foo")

    computeStatus = { "bar" }
    assertThat(cache.currentStatus).isEqualTo("foo")

    invalidate(NavInfoChangeReason.NAVIGATION_RESOURCE_CHANGED)
    assertThat(cache.currentStatus).isEqualTo("bar")
  }

  @Test
  fun readsValuesFromFetcher() {
    computeStatus = { it.modificationCount }
    assertThat(cache.currentStatus).isEqualTo(0L)

    setNavInfo(42L)
    invalidate(NavInfoChangeReason.NAVIGATION_RESOURCE_CHANGED)
    assertThat(cache.currentStatus).isEqualTo(42L)
  }

  @Test
  fun handlesFetcherDisabled() {
    computeStatus = { "enabled" }
    assertThat(cache.currentStatus).isEqualTo("enabled")

    computeStatus = { "disabled" }
    whenever(fetcher.isEnabled).thenReturn(false)
    invalidate(NavInfoChangeReason.SAFE_ARGS_MODE_CHANGED)
    assertThat(cache.currentStatus).isNull()
  }

  @Test
  fun handlesFetcherNull() {
    computeStatus = { "not null" }
    assertThat(cache.currentStatus).isEqualTo("not null")

    computeStatus = { "null" }
    whenever(fetcher.getCurrentNavInfo()).thenReturn(null)
    invalidate(NavInfoChangeReason.DUMB_MODE_CHANGED)
    assertThat(cache.currentStatus).isEqualTo("not null")

    setNavInfo(1L)
    assertThat(cache.currentStatus).isEqualTo("null")
  }

  @Test
  fun delegate() {
    val currentStatus by cache

    computeStatus = { "status" }
    assertThat(currentStatus).isEqualTo("status")

    computeStatus = { "other status" }
    invalidate(NavInfoChangeReason.NAVIGATION_RESOURCE_CHANGED)
    assertThat(currentStatus).isEqualTo("other status")
  }

  @Test
  fun passesInvalidateEvent() {
    assertThat(changeReasons).isEmpty()
    invalidate(NavInfoChangeReason.GRADLE_SYNC)
    assertThat(changeReasons).containsExactly(NavInfoChangeReason.GRADLE_SYNC)
  }

  private fun setNavInfo(modificationCount: Long) {
    whenever(fetcher.modificationCount).thenReturn(modificationCount)
    whenever(fetcher.getCurrentNavInfo())
      .thenReturn(
        NavInfo(
          facet = safeArgsRule.androidFacet,
          packageName = "foo.bar",
          entries = emptyList(),
          navVersion = SafeArgsFeatureVersions.TO_SAVED_STATE_HANDLE,
          modificationCount = modificationCount,
        )
      )
    assertThat(cache.modificationTracker.modificationCount).isEqualTo(modificationCount)
  }
}
