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

import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.calls.components.hasDefaultValue
import org.jetbrains.kotlin.types.KotlinType
import org.objectweb.asm.ClassReader
import java.nio.file.Files
import java.nio.file.Paths

const val SLOTS_PER_INT = 10
const val BITS_PER_INT = 31

/**
 * Determine an annotation signifies the function to be a Composable.
 */
fun Annotated.hasComposableAnnotation() = this.annotations.hasAnnotation(FqName("androidx.compose.runtime.Composable"))

/**
 * Determine number of "state" parameter the compiler will add to a Composable function.
 */
fun calcStateParamCount(realValueParamsCount : Int, numDefaults : Int = 0) : Int {
  // The number of synthetic int param added to a function is the total of:
  // 1. max (1, ceil(numParameters / 10))
  // 2. 0 default int parameters if none of the N parameters have default expressions
  // 3. ceil(N / 31) N parameters have default expressions if there are any defaults
  //
  // The formula follows the one found in ComposableFunctionBodyTransformer.kt
  var totalSyntheticParamCount = 0
  if (realValueParamsCount == 0) {
    totalSyntheticParamCount += 1;
  } else {
    val totalParams = realValueParamsCount
    totalSyntheticParamCount += Math.ceil(totalParams.toDouble() / SLOTS_PER_INT.toDouble()).toInt()
  }

  if (realValueParamsCount != 0 && numDefaults != 0) {
    totalSyntheticParamCount += Math.ceil(realValueParamsCount.toDouble() / BITS_PER_INT.toDouble()).toInt()
  }
  return totalSyntheticParamCount;
}

/**
 * Given an output file, check to see if it fits the naming convention of a group key metadata class.
 */
fun isKeyMetaClass(output: OutputFile) = output.relativePath.endsWith("\$KeyMeta.class")

/**
 * Given a composable function and a list of compiler output, compute the invalidate group key of that function by looking
 * up the meta key classes in the compiler output.
 */
fun getGroupKey(compilerOutput: List<OutputFile>, function: KtFunction, parentGroups: List<KtFunction>? = null) : Int? {
  fun computeStartOffset(target: KtFunction) : Int {
    return if (target is KtNamedFunction && target.funKeyword != null) {
      target.funKeyword!!.startOffset
    } else {
      target.startOffset
    }
  }

  for (c in compilerOutput) {
    if (!isKeyMetaClass(c)) {
      continue
    }

    val (file, groupsOffSets) = computeGroups(ClassReader(c.asByteArray()))

    // On Mac OS, canonicalPath return paths under `/Volumes/google/src` instead of `/google/src` as its internally a symlink, so it causes a mismatch. Fix is to check if the files at both paths are the same. Refer b/152076083.
    if (!Files.isSameFile(Paths.get(function.containingFile.virtualFile.canonicalPath), Paths.get(file))) {
      continue
    }

    var startOffset = computeStartOffset(function)
    var endOffset = function.endOffset

    groupsOffSets.find { it.startOffset == startOffset && it.endOffSet == endOffset }?.let { return it.key }

    parentGroups?.let {
      it.forEach { parentGroup ->
        startOffset = computeStartOffset(parentGroup)
        endOffset = parentGroup.endOffset
        groupsOffSets.find { entry -> entry.startOffset == startOffset && entry.endOffSet == endOffset }
          ?.let { offset -> return offset.key }
      }
    }
    return null
  }
  return null
}

/**
 * Computes the function signature of a given function after being processed by the compose compiler.
 *
 * The compose compiler needs to change the function types of all Composable function as it appends
 * state parameters to the end.
 */
fun remapFunctionSignatureIfNeeded(desc: SimpleFunctionDescriptor, mapper: KotlinTypeMapper) : String {
  var target = "${desc.name}("
  for (param in desc.valueParameters) {
    target += remapComposableFunctionType(param.type, mapper)
  }

  var additionalParams = ""
  if (desc.hasComposableAnnotation()) {
    val totalSyntheticParamCount = calcStateParamCount(desc.valueParameters.size, desc.valueParameters.count { it.hasDefaultValue() })
    // Add the Composer parameter as well as number of additional ints computed above.
    additionalParams = "Landroidx/compose/runtime/Composer;"
    for (x in 1 .. totalSyntheticParamCount) {
      additionalParams += "I"
    }
  }
  target += "$additionalParams)"
  // We are done with parameters, last thing to do is append return type.
  target += remapComposableFunctionType(desc.returnType, mapper)
  return target
}

/**
 * Computes the function type of a given function after being processed by the compose compiler.
 *
 * The compose compiler needs to change the function types of all Composable function as it appends
 * state parameters to the end.
 */
fun remapComposableFunctionType(type: KotlinType?, mapper: KotlinTypeMapper) : String {
  val funInternalNamePrefix = "Lkotlin/jvm/functions/Function"
  if (type == null) {
    return "Lkotlin/Unit;"
  }
  val originalType = mapper.mapType(type).toString()
  val numParamStart = originalType.indexOf(funInternalNamePrefix)
  if (!type.hasComposableAnnotation() || numParamStart < 0) {
    return originalType
  }
  var numParam = originalType.substring(numParamStart + funInternalNamePrefix.length, originalType.length - 1).toInt()
  numParam += calcStateParamCount(numParam) + 1 // Add the one extra param for Composer.
  return "$funInternalNamePrefix$numParam;"
}