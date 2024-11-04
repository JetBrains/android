/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.deployment.liveedit.tokens

import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.projectsystem.ClassContent
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.android.tools.idea.run.deployment.liveedit.tokens.BuildSystemLiveEditServices
import com.google.idea.blaze.android.projectsystem.BazelProjectSystem
import com.google.idea.blaze.android.projectsystem.BazelToken
import com.google.idea.blaze.android.run.BazelApplicationProjectContext
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeImportSettings
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile

class BazelBuildSystemLiveEditServices :
  BuildSystemLiveEditServices<BazelProjectSystem, BazelApplicationProjectContext>, BazelToken {
  override fun isApplicable(
    applicationProjectContext: ApplicationProjectContext
  ): Boolean {
    if (applicationProjectContext is BazelApplicationProjectContext) {
      return Blaze.getProjectType(applicationProjectContext.project) == BlazeImportSettings.ProjectType.QUERY_SYNC
    }
    return false
  }

  override fun getApplicationServices(
    bazelApplicationProjectContext: BazelApplicationProjectContext
  ): ApplicationLiveEditServices {
    return object: ApplicationLiveEditServices {
      override fun getClassContent(
        file: VirtualFile,
        className: String,
      ): ClassContent? {
        throw UnsupportedOperationException()
      }

      override fun getKotlinCompilerConfiguration(ktFile: KtFile): CompilerConfiguration {
        throw UnsupportedOperationException()
      }
    }
  }
}
