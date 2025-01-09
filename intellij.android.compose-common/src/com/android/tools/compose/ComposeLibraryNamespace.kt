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

const val COMPOSE_UI_PACKAGE = "androidx.compose.ui"
const val COMPOSE_UI_TOOLING_PACKAGE = "$COMPOSE_UI_PACKAGE.tooling"
const val COMPOSE_UI_TOOLING_PREVIEW_PACKAGE = "$COMPOSE_UI_TOOLING_PACKAGE.preview"

/** Preview element name */
const val COMPOSE_PREVIEW_ANNOTATION_NAME = "Preview"
const val COMPOSE_PREVIEW_ANNOTATION_FQN = "$COMPOSE_UI_TOOLING_PREVIEW_PACKAGE.$COMPOSE_PREVIEW_ANNOTATION_NAME"
const val COMPOSE_PREVIEW_PARAMETER_ANNOTATION_FQN = "$COMPOSE_UI_TOOLING_PREVIEW_PACKAGE.PreviewParameter"
const val COMPOSE_PREVIEW_ACTIVITY_FQN = "$COMPOSE_UI_TOOLING_PACKAGE.PreviewActivity"
const val COMPOSE_VIEW_ADAPTER_FQN = "$COMPOSE_UI_TOOLING_PACKAGE.ComposeViewAdapter"

const val MULTIPLATFORM_PREVIEW_ANNOTATION_FQN = "org.jetbrains.compose.ui.tooling.preview.Preview"

const val COMPOSE_MODIFIER_FQN = "$COMPOSE_UI_PACKAGE.Modifier"
const val COMPOSE_STRING_RESOURCE_FQN = "$COMPOSE_UI_PACKAGE.res.stringResource"

const val COMPOSABLE_ANNOTATION_NAME = "Composable"
const val COMPOSABLE_ANNOTATION_FQ_NAME ="androidx.compose.runtime.$COMPOSABLE_ANNOTATION_NAME"
