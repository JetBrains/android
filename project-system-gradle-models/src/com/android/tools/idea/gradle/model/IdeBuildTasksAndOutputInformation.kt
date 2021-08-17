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
package com.android.tools.idea.gradle.model

interface IdeBuildTasksAndOutputInformation {

    /**
     * Returns the name of the task used to generate the artifact output(s).
     *
     * @return the name of the task.
     */
    val assembleTaskName: String

    /**
     * Returns the absolute path for the listing file that will get updated after each build. The
     * model file will contain deployment related information like applicationId, list of APKs.
     *
     * @return the path to a json file.
     */

    val assembleTaskOutputListingFile: String?

    /**
     * Returns the name of the task used to generate the bundle file (.aab), or null if the task is
     * not supported.
     */
    val bundleTaskName: String?

    /**
     * Returns the path to the listing file generated after each [bundleTaskName] task
     * execution. The listing file will contain a reference to the produced bundle file (.aab).
     * Returns null when [bundleTaskName] returns null.
     */
    val bundleTaskOutputListingFile: String?

    /**
     * Returns the name of the task used to generate APKs via the bundle file (.aab), or null if the
     * task is not supported.
     */
    val apkFromBundleTaskName: String?

    /**
     * Returns the path to the model file generated after each [apkFromBundleTaskName]
     * task execution. The model will contain a reference to the folder where APKs from bundle are
     * placed into. Returns null when [apkFromBundleTaskName] returns null.
     */
    val apkFromBundleTaskOutputListingFile: String?
}
