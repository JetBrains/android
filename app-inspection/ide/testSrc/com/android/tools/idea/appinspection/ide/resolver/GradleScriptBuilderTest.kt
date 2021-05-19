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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GradleScriptBuilderTest {

  @Test
  fun buildScript() {
    val builder = GradleScriptBuilder()
    builder.addArtifact("androidx.work:work-runtime:2.5.0-alpha01:inspector@jar")
    builder.addArtifact("androidx.sqlite:sqlite:2.1.0:inspector@jar")

    assertThat(builder.build()).isEqualTo("""
      apply plugin: "java"
      repositories {
        google()
      }
      configurations {
        inspectors
      }
      dependencies {
        inspectors "androidx.work:work-runtime:2.5.0-alpha01:inspector@jar"
        inspectors "androidx.sqlite:sqlite:2.1.0:inspector@jar"
      }
      task resolveArtifacts() {
          configurations.inspectors.resolve().each { file ->
              println('APP_INSPECTOR: ' + file.getAbsolutePath());
          }
      }
    """.trimIndent())
  }
}