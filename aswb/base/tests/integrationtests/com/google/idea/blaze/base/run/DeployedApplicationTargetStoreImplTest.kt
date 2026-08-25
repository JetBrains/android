/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.common.Label
import com.intellij.openapi.util.SimpleModificationTracker
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Integration/unit tests for [DeployedApplicationTargetStoreImpl]. */
@RunWith(JUnit4::class)
class DeployedApplicationTargetStoreImplTest {

  @get:Rule val tmpDir = TemporaryFolder()
  private lateinit var applicationIdsDirectory: Path
  private lateinit var targetStore: DeployedApplicationTargetStoreImpl

  @Before
  fun setUp() {
    applicationIdsDirectory = tmpDir.root.toPath().resolve(".application-ids")
    targetStore = DeployedApplicationTargetStoreImpl.createForTest(applicationIdsDirectory)
  }

  @Test
  fun trackTargetForApplication_recordsMappingAndReadsBack() {
    val applicationId = "com.example.app"
    val target = Label.of("//java/com/example:app")

    targetStore.trackTargetForApplication(applicationId, target)

    val mappingsFile = applicationIdsDirectory.resolve("application_ids.txt")
    assertThat(mappingsFile.isRegularFile()).isTrue()
    assertThat(mappingsFile.readText().trim()).isEqualTo("$target $applicationId")

    assertThat(targetStore.getTargetForApplication(applicationId)).isEqualTo(target)
  }

  @Test
  fun trackTargetForApplication_updatesExistingMapping() {
    val applicationId = "com.example.app"
    val target1 = Label.of("//java/com/example:app1")
    val target2 = Label.of("//java/com/example:app2")

    targetStore.trackTargetForApplication(applicationId, target1)
    assertThat(targetStore.getTargetForApplication(applicationId)).isEqualTo(target1)

    targetStore.trackTargetForApplication(applicationId, target2)
    assertThat(targetStore.getTargetForApplication(applicationId)).isEqualTo(target2)

    val mappingsFile = applicationIdsDirectory.resolve("application_ids.txt")
    assertThat(mappingsFile.readText().trim()).isEqualTo("$target2 $applicationId")
  }

  @Test
  fun trackTargetForApplication_dropsOldApplicationIdWhenTargetReused() {
    val target = Label.of("//java/com/example:app")
    val oldAppId = "com.example.oldapp"
    val newAppId = "com.example.newapp"

    targetStore.trackTargetForApplication(oldAppId, target)
    assertThat(targetStore.getTargetForApplication(oldAppId)).isEqualTo(target)
    assertThat(targetStore.getAllApplicationIds()).containsExactly(oldAppId)

    targetStore.trackTargetForApplication(newAppId, target)
    assertThat(targetStore.getTargetForApplication(newAppId)).isEqualTo(target)
    assertThat(targetStore.getTargetForApplication(oldAppId)).isNull()
    assertThat(targetStore.getAllApplicationIds()).containsExactly(newAppId)

    val mappingsFile = applicationIdsDirectory.resolve("application_ids.txt")
    assertThat(mappingsFile.readText().trim()).isEqualTo("$target $newAppId")
  }

  @Test
  fun trackTargetForApplication_writesSortedEntriesByTarget() {
    targetStore.trackTargetForApplication("com.b.app", Label.of("//java/b:app"))
    targetStore.trackTargetForApplication("com.a.app", Label.of("//java/a:app"))
    targetStore.trackTargetForApplication("com.c.app", Label.of("//java/c:app"))

    val mappingsFile = applicationIdsDirectory.resolve("application_ids.txt")
    val lines = mappingsFile.readText().lines().filter { it.isNotBlank() }
    assertThat(lines)
      .containsExactly(
        "//java/a:app com.a.app",
        "//java/b:app com.b.app",
        "//java/c:app com.c.app",
      )
      .inOrder()
  }

  @Test
  fun getTargetForApplication_returnsNullWhenUncached() {
    assertThat(targetStore.getTargetForApplication("non.existent.app")).isNull()
  }

  @Test
  fun getAllApplicationIds_returnsEmptyWhenDirectoryDoesNotExist() {
    assertThat(targetStore.getAllApplicationIds()).isEmpty()
  }

  @Test
  fun getAllApplicationIds_returnsAllTrackedApplicationIds() {
    targetStore.trackTargetForApplication("com.example.app1", Label.of("//java/com/example:app1"))
    targetStore.trackTargetForApplication("com.example.app2", Label.of("//java/com/example:app2"))
    targetStore.trackTargetForApplication("com.example.app3", Label.of("//java/com/example:app3"))

    assertThat(targetStore.getAllApplicationIds()).containsExactly("com.example.app1", "com.example.app2", "com.example.app3")
  }

  @Test
  fun cleanupOnAccess_removesStaleTargetsWhenModificationTrackerChanges() {
    val modTracker = SimpleModificationTracker()
    var knownTargets: Set<Label>? = null
    val store =
      DeployedApplicationTargetStoreImpl(
        applicationIdsDirectory,
        knownTargetsSupplier = { knownTargets },
        modificationTracker = modTracker,
      )

    val target1 = Label.of("//java/com/example:app1")
    val target2 = Label.of("//java/com/example:app2")
    val target3 = Label.of("//java/com/example:app3")

    store.trackTargetForApplication("com.example.app1", target1)
    store.trackTargetForApplication("com.example.app2", target2)
    store.trackTargetForApplication("com.example.app3", target3)

    // Modification count increments after sync
    knownTargets = setOf(target1, target3)
    modTracker.incModificationCount()

    // Access triggers cleanup
    assertThat(store.getAllApplicationIds()).containsExactly("com.example.app1", "com.example.app3")
    assertThat(store.getTargetForApplication("com.example.app2")).isNull()

    val mappingsFile = applicationIdsDirectory.resolve("application_ids.txt")
    val lines = mappingsFile.readText().lines().filter { it.isNotBlank() }
    assertThat(lines)
      .containsExactly(
        "//java/com/example:app1 com.example.app1",
        "//java/com/example:app3 com.example.app3",
      )
      .inOrder()
  }

  @Test
  fun existingMappingsFile_isLoadedIntoCacheOnFirstAccess() {
    Files.createDirectories(applicationIdsDirectory)
    val mappingsFile = applicationIdsDirectory.resolve("application_ids.txt")
    mappingsFile.writeText(
      """
      # Comment line

      //java/com/example:app1 com.example.app1
      malformed_line_without_app_id
      //java/com/example:app2 com.example.app2
      """
        .trimIndent()
    )

    val newStore = DeployedApplicationTargetStoreImpl.createForTest(applicationIdsDirectory)
    assertThat(newStore.getAllApplicationIds()).containsExactly("com.example.app1", "com.example.app2")
    assertThat(newStore.getTargetForApplication("com.example.app1")).isEqualTo(Label.of("//java/com/example:app1"))
    assertThat(newStore.getTargetForApplication("com.example.app2")).isEqualTo(Label.of("//java/com/example:app2"))
  }

  @Test
  fun getTargetForApplication_returnsNullWhenFileEmpty() {
    Files.createDirectories(applicationIdsDirectory)
    val mappingsFile = applicationIdsDirectory.resolve("application_ids.txt")
    mappingsFile.writeText("   \n\n  # only comments\n")

    val newStore = DeployedApplicationTargetStoreImpl.createForTest(applicationIdsDirectory)
    assertThat(newStore.getAllApplicationIds()).isEmpty()
    assertThat(newStore.getTargetForApplication("any.app")).isNull()
  }
}
