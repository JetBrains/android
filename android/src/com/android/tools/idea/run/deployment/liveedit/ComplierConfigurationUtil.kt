/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.arguments.toLanguageVersionSettings
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.psi.KtFile

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

fun getCompilerConfiguration(
  module: Module,
  file: KtFile
): CompilerConfiguration {
  if (file.module != module) {
    // *Note*: currently all 3 callers satisfy this condition and [module] parameter is going to be removed once both compose previews and
    // live edit migration to build system specific extension points is completed.
    error("$file must belong to $module")
  }
  val compilerConfiguration = CompilerConfiguration().apply {
    put(
      CommonConfigurationKeys.MODULE_NAME,
      module.project.getProjectSystem().getModuleSystem(module).getModuleNameForCompilation(file.originalFile.virtualFile)
    )
    KotlinFacet.get(module)?.let { kotlinFacet ->
      val moduleName = when (val compilerArguments = kotlinFacet.configuration.settings.compilerArguments) {
        is K2JVMCompilerArguments -> compilerArguments.moduleName
        is K2MetadataCompilerArguments -> compilerArguments.moduleName
        else -> null
      }
      moduleName?.let {
        put(CommonConfigurationKeys.MODULE_NAME, it)
      }
    }

    if (StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_COMPILER_FLAGS.isOverridden) {
      val flags = StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_COMPILER_FLAGS.get().split(" ")
      val mainKotlinCompilerOptions = parseCommandLineArguments<K2JVMCompilerArguments>(flags)
      val languageSettings = mainKotlinCompilerOptions.toLanguageVersionSettings(MessageCollector.NONE)
      setOptions(languageSettings)
    } else {
      setOptions(file.languageVersionSettings)
    }

    // TODO(b/367786795): We met an exception from JVM IR CodeGen in the middle of K2 LiveEdit. It was caused by an
    //  optimization similar to constant propagation. As explained in https://youtrack.jetbrains.com/issue/KT-70261,
    //  "It is kind of experimental (because of -X) but only because the whole interpretation and optimization
    //  thing is experimental.", we simply pass `-Xignore-const-optimization-errors`. When the optimization is
    //  stable, we can drop this.
    put(CommonConfigurationKeys.IGNORE_CONST_OPTIMIZATION_ERRORS, true)
  }
  return compilerConfiguration
}
