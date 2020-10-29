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
package com.android.tools.idea.appinspection.ide.resolver

const val TASK_ID = "resolveArtifacts"
const val ARTIFACT_PREFIX = "APP_INSPECTOR: "

/**
 * A builder class for building a simple gradle script that downloads a list of maven artifacts.
 */
class GradleScriptBuilder {
  private val inspectors = mutableListOf<String>()

  fun addArtifact(inspectorArtifactId: String) {
    inspectors.add(inspectorArtifactId)
  }

  fun build() = """
apply plugin: "java"
repositories {
  google()
}
configurations {
  inspectors
}
dependencies {
${inspectors.joinToString("\n") { "  inspectors \"$it\"" }}
}
task ${TASK_ID}() {
    configurations.inspectors.resolve().each { file ->
        println('${ARTIFACT_PREFIX}' + file.getAbsolutePath());
    }
}
""".trimIndent()
}