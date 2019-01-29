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

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.gradleproject.toProto
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.GlobalLibraryMap
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.Variant
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.repackage.com.google.protobuf.util.JsonFormat
import org.gradle.tooling.model.GradleProject

fun GradleProject.toJson(converter: PathConverter) = JsonFormat.printer().print(toProto(converter))!!
fun Variant.toJson(converter: PathConverter) = JsonFormat.printer().print(toProto(converter))!!
fun AndroidProject.toJson(converter: PathConverter) = JsonFormat.printer().print(toProto(converter))!!
fun GlobalLibraryMap.toJson(converter: PathConverter) = JsonFormat.printer().print(toProto(converter))!!
