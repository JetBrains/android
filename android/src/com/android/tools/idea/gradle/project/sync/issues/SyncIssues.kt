/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("SyncIssues")
package com.android.tools.idea.gradle.project.sync.issues

import com.android.builder.model.SyncIssue
import com.google.common.collect.ImmutableList
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.project.Project
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOGGER = Logger.getInstance(SyncIssueRegistry::class.java)

/**
 * A project based component that stores a map from modules to sync issues. These are registered during sync (module setup) and are reported
 * shortly afterward. The register is cleared at the start of each sync.
 *
 * [SyncIssue]s should not be read until this object has been sealed (it is sealed at the end of sync) as there still may be additional
 * [SyncIssue]s that have not been reported, attempting to do so will log an error.
 *
 * Likewise once this object is sealed any attempt to register additional issues will also log an error. These errors will be converted
 * into exception at a later date.
 */
internal open class SyncIssueRegistry<Component> : Sealable by BaseSealable() {
  private val lock = ReentrantLock()
  private val syncIssueList = mutableListOf<SyncIssue>()

  fun register(syncIssues: Collection<SyncIssue>) {
    lock.withLock {
      if (checkSeal()) LOGGER.error("Attempted to add more sync issues when the SyncIssueRegistry was sealed!", IllegalStateException())
      syncIssueList.addAll(syncIssues)
    }
  }

  fun get(): List<SyncIssue> {
    return lock.withLock {
      if (!checkSeal()) LOGGER.error("Attempted to read sync issues before the SyncIssuesRegister was sealed!", IllegalStateException())
      ImmutableList.copyOf(syncIssueList)
    }
  }

  fun unsealAndClear() {
    lock.withLock {
      unseal()
      syncIssueList.clear()
    }
  }
}

internal class ModuleSyncIssueRegistry : SyncIssueRegistry<Module>()
private fun Module.syncIssueRegistry() = ModuleServiceManager.getService(this, ModuleSyncIssueRegistry::class.java)!!

@JvmName("forModule")
fun Module.syncIssues() = syncIssueRegistry().get()
fun Module.registerSyncIssues(issues: Collection<SyncIssue>) = syncIssueRegistry().register(issues)

@JvmName("byModule")
fun Project.getSyncIssuesByModule() = ModuleManager.getInstance(this).modules.associateBy({ module -> module }, Module::syncIssues)

/**
 * Seals all [SyncIssueRegistry]s for this project.
 */
@JvmName("seal")
fun Project.sealSyncIssues() {
  ModuleManager.getInstance(this).modules.forEach { module ->
    module.syncIssueRegistry().also { it.seal() }
  }
}

/**
 * Clears and unseals all of the [SyncIssueRegistry]s for this project.
 */
fun Project.clearSyncIssues() {
  ModuleManager.getInstance(this).modules.forEach { module ->
    module.syncIssueRegistry().also { it.unsealAndClear() }
  }
}