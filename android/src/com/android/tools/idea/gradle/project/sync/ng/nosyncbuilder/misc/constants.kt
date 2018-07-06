/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc

/**
 * We have only support variant and we have a "fake" build type with source provider.
 * The reason is that there are no such concept as build types in the new models and source provider is tied to the artifact.
 * However, in the current codebase AndroidModuleModel is trying to find build type by variant.buildType to access its source provider.
 * This constant is used in a workaround.
 */
const val BUILD_TYPE_NAME = "buildType"