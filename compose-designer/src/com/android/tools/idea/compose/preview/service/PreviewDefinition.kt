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
package com.android.tools.idea.compose.preview.service

import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * A lightweight definition of a single `@Preview` annotation found on a `@Composable` function.
 * This can be a direct annotation or one resolved through a multipreview annotation.
 *
 * @param displayName A user-friendly name for the preview, combining the function name and the
 *   preview annotation name if available.
 * @param functionPointer A smart pointer to the `@Composable` function.
 * @param annotationPointer A smart pointer to the specific "leaf" `@Preview` annotation.
 */
data class PreviewDefinition(
  val displayName: String,
  val functionPointer: SmartPsiElementPointer<KtNamedFunction>,
  val annotationPointer: SmartPsiElementPointer<KtAnnotationEntry>,
)
