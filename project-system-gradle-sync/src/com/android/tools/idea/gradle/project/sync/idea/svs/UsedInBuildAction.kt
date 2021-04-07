/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.svs

/**
 * Marks a given CLASS or FUNCTION to indicate that it is used in a Gradle tooling API BuildAction.
 * This means that extra care should be taken when adding dependencies or changing the code. Calls to methods in this
 * code may only be valid when running with specific versions of Gradle or the Android Gradle Plugin.
 *
 * Also no references should be held to any objects from the Gradle tooling API. This API returns proxy objects from Gradle which can
 * cause memory leaks if kept around in the IDE.
 *
 * Classes marked with this annotation must also implement [java.io.Serializable] along with all their non-primitive members.
 * This is so that they can be passed to the Gradle process using Gradle's serialization methods.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class UsedInBuildAction