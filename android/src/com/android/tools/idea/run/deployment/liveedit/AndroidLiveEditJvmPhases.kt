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
/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.android.tools.idea.run.deployment.liveedit

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.phaser.NamedCompilerPhase
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.common.phaser.performByIrFile
import org.jetbrains.kotlin.backend.common.phaser.then
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.MultifileFacadeFileEntry
import org.jetbrains.kotlin.backend.jvm.codegen.ClassCodegen
import org.jetbrains.kotlin.backend.jvm.jvmLoweringPhases
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.render

private fun codegenPhase(generateMultifileFacade: Boolean): NamedCompilerPhase<JvmBackendContext, IrModuleFragment> {
  val suffix = if (generateMultifileFacade) "MultifileFacades" else "Regular"
  val descriptionSuffix = if (generateMultifileFacade) ", multifile facades" else ", regular files"
  return performByIrFile(
    name = "CodegenByIrFile$suffix",
    description = "Code generation by IrFile$descriptionSuffix",
    copyBeforeLowering = false,
    lower = listOf(
      makeIrFilePhase(
        { context -> FileCodegen(context, generateMultifileFacade) },
        name = "Codegen$suffix",
        description = "Code generation"
      )
    )
  )
}

private class FileCodegen(private val context: JvmBackendContext, private val generateMultifileFacade: Boolean) : FileLoweringPass {
  override fun lower(irFile: IrFile) {
    val isMultifileFacade = irFile.fileEntry is MultifileFacadeFileEntry
    if (isMultifileFacade == generateMultifileFacade) {
      for (loweredClass in irFile.declarations) {
        if (loweredClass !is IrClass) {
          throw AssertionError("File-level declaration should be IrClass after JvmLower, got: " + loweredClass.render())
        }
        ClassCodegen.getOrCreate(loweredClass, context).generate()
      }
    }
  }
}

// Generate multifile facades first, to compute and store JVM signatures of const properties which are later used
// when serializing metadata in the multifile parts.
// TODO: consider dividing codegen itself into separate phases (bytecode generation, metadata serialization) to avoid this
internal val jvmCodegenPhases = NamedCompilerPhase(
  name = "Codegen",
  description = "Code generation",
  nlevels = 1,
  lower = codegenPhase(generateMultifileFacade = true) then
    codegenPhase(generateMultifileFacade = false)
)

// This property is needed to avoid dependencies from "leaf" modules (cli, tests-common-new) on backend.jvm:lower.
// It's used to create PhaseConfig and is the only thing needed from lowerings in the leaf modules.
val jvmPhases: NamedCompilerPhase<JvmBackendContext, IrModuleFragment>
  get() = jvmLoweringPhases
