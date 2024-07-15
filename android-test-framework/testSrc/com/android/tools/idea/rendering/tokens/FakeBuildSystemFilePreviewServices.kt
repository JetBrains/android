/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.rendering.tokens

import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildTargets
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import org.jetbrains.annotations.TestOnly

/**
 * An implementation of [BuildSystemFilePreviewServices] for use in tests that allows simulating custom scenarios.
 */
@TestOnly
class FakeBuildSystemFilePreviewServices : BuildSystemFilePreviewServices<AndroidProjectSystem> {
  override val buildTargets: BuildTargets = object : BuildTargets {
    override fun from(module: Module, targetFile: VirtualFile): BuildTargetReference {
      return FakeBuildTargetReference(module)
    }

    override fun fromModuleOnly(module: Module): BuildTargetReference {
      return FakeBuildTargetReference(module)
    }
  }

  override fun isApplicable(projectSystem: AndroidProjectSystem): Boolean = true

  /**
   * Registers this fake implementation for the lifespan of [parentDisposable] for all project systems.
   */
  fun register(parentDisposable: Disposable) {
    ExtensionTestUtil.maskExtensions(BuildSystemFilePreviewServices.EP_NAME, listOf(this), parentDisposable)
  }
}

private data class FakeBuildTargetReference(override val module: Module) : BuildTargetReference