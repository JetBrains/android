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

import com.android.testutils.TestUtils
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil

/**
 * Path to the compose-runtime jar. Note that unlike all other dependencies, we
 * don't need to load that into the test's runtime classpath. Instead, we just
 * need to make sure it is in the classpath input of the compiler invocation
 * Live Edit uses. Aside from things like references to the @Composable
 * annotation, we actually don't need anything from that runtime during the compiler.
 * The main reason to include that is because the compose compiler plugin expects
 * the runtime to be path of the classpath or else it'll throw an error.
 */
private val composeRuntimePath = TestUtils.getWorkspaceRoot().resolve(
  "tools/adt/idea/compose-ide-plugin/testData/lib/compose-runtime-1.4.0-SNAPSHOT.jar").toString()

/**
 * Loads the Compose runtime into the project class path. This allows for tests using the compiler (Live Edit/FastPreview)
 * to correctly invoke the compiler as they would do in prod.
 */
fun com.intellij.openapi.module.Module.loadComposeRuntimeInClassPath() {
  // Load the compose runtime into the main module's library dependency.
  LocalFileSystem.getInstance().refreshAndFindFileByPath(composeRuntimePath)
  PsiTestUtil.addLibrary(this, composeRuntimePath)
}