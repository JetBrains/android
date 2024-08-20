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
package com.android.tools.compose.debug

import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.psi.KtFile

/**
 * Compute the name of the ComposableSingletons class for the given file.
 *
 * The Compose compiler plugin creates per-file ComposableSingletons classes to cache composable
 * lambdas without captured variables. We need to locate these classes in order to search them for
 * breakpoint locations.
 *
 * NOTE: The pattern for ComposableSingletons classes needs to be kept in sync with the code in
 * `ComposerLambdaMemoization.getOrCreateComposableSingletonsClass`. The optimization was introduced
 * in I8c967b14c5d9bf67e5646e60f630f2e29e006366
 */
internal fun computeComposableSingletonsClassName(file: KtFile): String {
  // The code in `ComposerLambdaMemoization` always uses the file short name and
  // ignores `JvmName` annotations, but (implicitly) respects `JvmPackageName`
  // annotations.
  val filePath = file.virtualFile?.path ?: file.name
  val fileName = filePath.split('/').last()
  val shortName = PackagePartClassUtils.getFilePartShortName(fileName)
  val fileClassFqName =
    runReadAction { JvmFileClassUtil.getFileClassInfoNoResolve(file) }.facadeClassFqName

  return buildString {
    val pgk = fileClassFqName.parent()
    if (!pgk.isRoot) {
      append(pgk.asString())
      append(".")
    }
    append("ComposableSingletons")
    append("\$")
    append(shortName)
  }
}

/**
 * Compute the class name for a given lambda
 *
 * @param composeSingletonsClassName: The 'ComposableSingletons' class: See
 *   [computeComposableSingletonsClassName]
 * @param lambdaIndex The index of the current lambda (first lambda has index 0)
 */
internal fun computeComposableSingletonsLambdaClassName(
  composeSingletonsClassName: String,
  lambdaIndex: Int,
): String {
  return "$composeSingletonsClassName\$lambda-${lambdaIndex + 1}\$1"
}
