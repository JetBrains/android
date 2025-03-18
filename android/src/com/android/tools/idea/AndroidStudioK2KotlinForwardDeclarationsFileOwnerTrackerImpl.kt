/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.KotlinForwardDeclarationsFileOwnerTracker
import org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.KotlinModuleInfoBasedForwardDeclarationsFileOwnerTrackerImpl
import org.jetbrains.kotlin.idea.base.projectStructure.useNewK2ProjectStructureProvider

/**
 * A temporary replacement for [org.jetbrains.kotlin.idea.base.fir.projectStructure.kmp.K2KotlinForwardDeclarationsFileOwnerTrackerImpl]
 * to fix a project leak related to the [delegate] instance never being disposed properly. This is only applicable in Kotlin K2 mode
 * when using the old Kotlin project structure (registry key kotlin.use.new.project.structure.provider=false).
 *
 * TODO(b/404571134): delete this class when enabling the new Kotlin project structure.
 */
@Suppress("UnstableApiUsage")
class AndroidStudioK2KotlinForwardDeclarationsFileOwnerTrackerImpl(project: Project) : KotlinForwardDeclarationsFileOwnerTracker, Disposable {
  private val delegate: KotlinForwardDeclarationsFileOwnerTracker? = if (useNewK2ProjectStructureProvider) {
    thisLogger().error("This class should be deleted once the new Kotlin project structure is enabled")
    null
  } else {
    val delegate = KotlinModuleInfoBasedForwardDeclarationsFileOwnerTrackerImpl(project)
    Disposer.register(this, delegate)
    delegate
  }

  override fun getFileOwner(virtualFile: VirtualFile): KaModule? {
    return delegate?.getFileOwner(virtualFile)
  }

  override fun registerFileOwner(virtualFile: VirtualFile, owner: KaModule) {
    delegate?.registerFileOwner(virtualFile, owner)
  }

  override fun dispose() {}
}
