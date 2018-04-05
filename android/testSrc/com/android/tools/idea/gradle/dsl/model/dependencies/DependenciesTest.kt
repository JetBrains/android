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
package com.android.tools.idea.gradle.dsl.model.dependencies

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class DependenciesTest : GradleFileModelTestCase() {
  @Test
  fun testAllDependencies() {
    val text = """
               dependencies {
                 api fileTree(dir: 'libs', include: ['*.jar'])
                 implementation 'com.example.libs:lib1:0.+'
                 api 'com.android.support:appcompat-v7:+'
                 compile files('lib1.jar')
                 debugImplementation project(':javalib1')
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    val deps = buildModel.dependencies().all()
    assertSize(5, deps)
    run {
      val dep = deps[0] as FileTreeDependencyModel
      assertThat(dep.configurationName(), equalTo("api"))
      assertThat(dep.dir().value(), equalTo("libs"))
    }
    run {
      // Note: all() will group all dependencies by configuration name.
      val dep = deps[1] as ArtifactDependencyModel
      assertThat(dep.configurationName(), equalTo("api"))
      assertThat(dep.compactNotation(), equalTo("com.android.support:appcompat-v7:+"))
    }
    run {
      val dep = deps[2] as ArtifactDependencyModel
      assertThat(dep.configurationName(), equalTo("implementation"))
      assertThat(dep.compactNotation(), equalTo("com.example.libs:lib1:0.+"))
    }
    run {
      val dep = deps[3] as FileDependencyModel
      assertThat(dep.configurationName(), equalTo("compile"))
      assertThat(dep.file().value(), equalTo("lib1.jar"))
    }
    run {
      val dep = deps[4] as ModuleDependencyModel
      assertThat(dep.configurationName(), equalTo("debugImplementation"))
      assertThat(dep.name(), equalTo("javalib1"))
    }
  }
}