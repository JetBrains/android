/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.nav.safeargs.project

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.nav.safeargs.extensions.replaceWithSaving
import com.android.tools.idea.nav.safeargs.extensions.replaceWithoutSaving
import com.android.tools.idea.nav.safeargs.module.ModuleNavigationResourcesModificationTracker
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class NavigationResourcesModificationListenerTest {
  @get:Rule val safeArgsRule = SafeArgsRule()

  private lateinit var myModuleNavResourcesTracker: ModuleNavigationResourcesModificationTracker
  private lateinit var myProjectNavResourcesTracker: ProjectNavigationResourceModificationTracker
  private val project: Project
    get() = safeArgsRule.project

  private val listener = mock<NavigationResourcesChangeListener>()

  private var lastModificationCount = 0L

  @Before
  fun setUp() {
    // Ensure that the resource repository is initialized before we start testing, since
    // NavigationResourcesModificationListener will only
    // used an already-cached version of the repository and won't trigger creation if it doesn't
    // already exist.
    safeArgsRule.fixture.addFileToProject("res/values/strings.xml", "")
    safeArgsRule.waitForPendingUpdates()

    project.messageBus.connect().subscribe(NAVIGATION_RESOURCES_CHANGED, listener)
    NavigationResourcesModificationListener.ensureSubscribed(project)
    myModuleNavResourcesTracker =
      ModuleNavigationResourcesModificationTracker.getInstance(safeArgsRule.module)
    myProjectNavResourcesTracker = ProjectNavigationResourceModificationTracker.getInstance(project)
    assertThat(myModuleNavResourcesTracker.modificationCount).isEqualTo(0L)
    assertThat(myProjectNavResourcesTracker.modificationCount).isEqualTo(0L)
  }

  @Test
  fun addFilesToNavigationResourcesFolder() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      // language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment1"
              android:name="test.safeargs.Fragment1"
              android:label="Fragment1">
            <argument
                android:name="arg"
                app:argType="string" />
          </fragment>
        </navigation>
      """
        .trimIndent(),
    )

    safeArgsRule.waitForPendingUpdates()
    // picked up 1 document change and 1 vfs change
    verifyModuleChangeEventsFired(2)

    safeArgsRule.fixture.addFileToProject(
      "res/navigation/other.xml",
      // language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/other"
            app:startDestination="@id/fragment2">

          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2">
            <argument
                android:name="arg"
                app:argType="integer" />
          </fragment>
        </navigation>
      """
        .trimIndent(),
    )

    safeArgsRule.waitForPendingUpdates()
    // picked up 1 document change and 1 vfs change
    verifyModuleChangeEventsFired(2)
  }

  @Test
  fun deleteNavigationResourceFolder() {
    val navFile =
      safeArgsRule.fixture.addFileToProject(
        "res/navigation/main.xml",
        // language=XML
        """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment1"
              android:name="test.safeargs.Fragment1"
              android:label="Fragment1">
            <argument
                android:name="arg"
                app:argType="string" />
          </fragment>
        </navigation>
      """
          .trimIndent(),
      )

    safeArgsRule.waitForPendingUpdates()
    // picked up 1 document change and 1 vfs change
    verifyModuleChangeEventsFired(2)

    WriteCommandAction.runWriteCommandAction(project) { navFile.virtualFile.parent.delete(this) }
    safeArgsRule.waitForPendingUpdates()
    // picked up 1 vfs change
    verifyModuleChangeEventsFired(1)
  }

  @Test
  fun modifyNavResourceFile() {
    val navFile =
      safeArgsRule.fixture.addFileToProject(
        "res/navigation/main.xml",
        // language=XML
        """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment1"
              android:name="test.safeargs.Fragment1"
              android:label="Fragment1">
            <argument
                android:name="arg"
                app:argType="string" />
          </fragment>
        </navigation>
      """
          .trimIndent(),
      )
    safeArgsRule.waitForPendingUpdates()
    // picked up 1 document change and 1 vfs change
    verifyModuleChangeEventsFired(2)

    // modify without saving
    WriteCommandAction.runWriteCommandAction(project) {
      navFile.virtualFile.replaceWithoutSaving("fragment1", "fragment2", project)
    }
    safeArgsRule.waitForPendingUpdates()
    // picked up 1 document change
    verifyModuleChangeEventsFired(1)

    // modify and save afterwards
    WriteCommandAction.runWriteCommandAction(project) {
      navFile.virtualFile.replaceWithSaving("fragment2", "fragment3", project)
    }
    safeArgsRule.waitForPendingUpdates()
    // picked up 1 vfs change
    verifyModuleChangeEventsFired(1)
  }

  private fun verifyModuleChangeEventsFired(count: Int) {
    verify(listener, times(count)).onNavigationResourcesChanged(safeArgsRule.module)
    verifyNoMoreInteractions(listener)
    reset(listener)
    lastModificationCount += count
    assertThat(myModuleNavResourcesTracker.modificationCount).isEqualTo(lastModificationCount)
    assertThat(myProjectNavResourcesTracker.modificationCount).isEqualTo(lastModificationCount)
  }
}
