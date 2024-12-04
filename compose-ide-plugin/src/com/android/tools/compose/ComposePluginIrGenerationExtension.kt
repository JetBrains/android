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

package com.android.tools.compose

import androidx.compose.compiler.plugins.kotlin.ComposeIrGenerationExtension
import androidx.compose.compiler.plugins.kotlin.FeatureFlag
import androidx.compose.compiler.plugins.kotlin.FeatureFlags
import androidx.compose.compiler.plugins.kotlin.IncompatibleComposeRuntimeVersionException
import com.android.tools.idea.run.deployment.liveedit.CompileScope
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

private val liveEditPackageName = "${CompileScope::class.java.packageName}."

@Suppress("INVISIBLE_REFERENCE", "EXPERIMENTAL_IS_NOT_ENABLED")
@OptIn(org.jetbrains.kotlin.extensions.internal.InternalNonStableExtensionPoints::class)
class ComposePluginIrGenerationExtension : IrGenerationExtension {
  private val messageCollector = ComposePluginMessageCollector()

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    try {
      ComposeIrGenerationExtension(
          // Enable FunctionKeyMeta annotation to be generated wherever possible to help live-edit & previews.
          generateFunctionKeyMetaAnnotations = true,
          useK2 = KotlinPluginModeProvider.isK2Mode(),
          messageCollector = messageCollector,
          featureFlags = FeatureFlags().apply { setFeature(FeatureFlag.IntrinsicRemember, false) },
          // Loading the compose version class from the compose runtime will cause an exception for
          // a non-compose module because of the missing dependency on compose runtime. This option
          // prevents the compose compiler plugin from throwing an exception when the compose
          // version class is not found.
          skipIfRuntimeNotFound = true,
        )
        .generate(moduleFragment, pluginContext)
    } catch (e: ProcessCanceledException) {
      // From ProcessCanceledException javadoc: "Usually, this exception should not be caught,
      // swallowed, logged, or handled in any way.
      // Instead, it should be rethrown so that the infrastructure can handle it correctly."
      throw e
    } catch (versionError: IncompatibleComposeRuntimeVersionException) {
      // We only rethrow version incompatibility when we are trying to CodeGen for Live Edit.
      for (s in versionError.stackTrace) {
        if (s.className.startsWith(liveEditPackageName)) {
          throw versionError
        }
      }
      versionError.printStackTrace()
    } catch (t: Throwable) {
      t.printStackTrace()
    }
  }

  private class ComposePluginMessageCollector : MessageCollector {
    private val logger = Logger.getInstance(ComposePluginIrGenerationExtension::class.java)
    private var hasError = false

    override fun clear() {
      hasError = false
    }

    override fun hasErrors(): Boolean = hasError

    override fun report(
      severity: CompilerMessageSeverity,
      message: String,
      location: CompilerMessageSourceLocation?,
    ) {
      val messageWithLocation =
        location?.let { "$message (${it.path}: ${it.line}, ${it.column})" } ?: message
      when (severity) {
        CompilerMessageSeverity.OUTPUT,
        CompilerMessageSeverity.LOGGING,
        CompilerMessageSeverity.INFO -> logger.info(messageWithLocation)
        CompilerMessageSeverity.WARNING,
        CompilerMessageSeverity.STRONG_WARNING -> logger.warn(messageWithLocation)
        CompilerMessageSeverity.EXCEPTION,
        CompilerMessageSeverity.ERROR -> {
          logger.warn(messageWithLocation)
          hasError = true
        }
      }
    }
  }
}
