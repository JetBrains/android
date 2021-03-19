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

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.uast.UAnnotation

private const val UI_PACKAGE = "androidx.ui"
private const val COMPOSE_PACKAGE = "androidx.compose.ui"

/** Preview element name */
const val COMPOSE_PREVIEW_ANNOTATION_NAME = "Preview"

const val COMPOSABLE_ANNOTATION_NAME = "Composable"

const val COMPOSE_ALIGNMENT = "${COMPOSE_PACKAGE}.Alignment"
const val COMPOSE_ALIGNMENT_HORIZONTAL = "${COMPOSE_ALIGNMENT}.Horizontal"
const val COMPOSE_ALIGNMENT_VERTICAL = "${COMPOSE_ALIGNMENT}.Vertical"

const val COMPOSE_ARRANGEMENT = "androidx.compose.foundation.layout.Arrangement"
const val COMPOSE_ARRANGEMENT_HORIZONTAL = "${COMPOSE_ARRANGEMENT}.Horizontal"
const val COMPOSE_ARRANGEMENT_VERTICAL = "${COMPOSE_ARRANGEMENT}.Vertical"

val COMPOSABLE_FQ_NAMES = setOf(
  "androidx.compose.$COMPOSABLE_ANNOTATION_NAME",
  "androidx.compose.runtime.$COMPOSABLE_ANNOTATION_NAME"
)

/**
 * Represents the Jetpack Compose library package name. The compose libraries will move from
 * `androidx.ui` to `androidx.compose` and this enum encapsulates the naming for the uses in tools.
 */
enum class ComposeLibraryNamespace(val packageName: String) {
  ANDROIDX_UI(UI_PACKAGE),
  ANDROIDX_COMPOSE(COMPOSE_PACKAGE);

  /** Package containing the preview definitions */
  val previewPackage: String = "$packageName.tooling.preview"

  /**
   * Name of the `ComposeViewAdapter` object that is used by the preview surface to hold
   * the previewed `@Composable`s.
   */
  val composableAdapterName: String = "$previewPackage.ComposeViewAdapter"

  val composeModifierClassName: String = "$packageName.Modifier"

  /** Only composables with this annotations will be rendered to the surface. */
  val previewAnnotationName = "$previewPackage.$COMPOSE_PREVIEW_ANNOTATION_NAME"

  /** Same as [previewAnnotationName] but in [FqName] form. */
  val previewAnnotationNameFqName = FqName(previewAnnotationName)

  /** Annotation FQN for `Preview` annotated parameters. */
  val previewParameterAnnotationName = "$previewPackage.PreviewParameter"

  /** FqName of @Composable function that loads a string resource. **/
  val stringResourceFunctionFqName = "$packageName.res.stringResource"
}

/** Only composables with this annotations will be rendered to the surface. */
@JvmField
val COMPOSE_VIEW_ADAPTER_FQNS = setOf(ComposeLibraryNamespace.ANDROIDX_UI.composableAdapterName,
                                      ComposeLibraryNamespace.ANDROIDX_COMPOSE.composableAdapterName)

/** FQNs for the `@Preview` annotation. Only composables with this annotations will be rendered to the surface. */
@JvmField
val PREVIEW_ANNOTATION_FQNS = setOf(ComposeLibraryNamespace.ANDROIDX_UI.previewAnnotationName,
                                    ComposeLibraryNamespace.ANDROIDX_COMPOSE.previewAnnotationName)

/** Annotations FQNs for `Preview` annotated parameters. */
@JvmField
val PREVIEW_PARAMETER_FQNS = setOf(ComposeLibraryNamespace.ANDROIDX_UI.previewParameterAnnotationName,
                                   ComposeLibraryNamespace.ANDROIDX_COMPOSE.previewParameterAnnotationName)

/**
 * Utility method to find the [ComposeLibraryNamespace] for a given annotation.
 */
fun UAnnotation.findComposeLibraryNamespace(): ComposeLibraryNamespace =
  if (qualifiedName?.startsWith(UI_PACKAGE) == true) {
    ComposeLibraryNamespace.ANDROIDX_UI
  }
  else {
    ComposeLibraryNamespace.ANDROIDX_COMPOSE
  }
