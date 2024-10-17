/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.android.ide.common.gradle.Component
import com.android.ide.common.gradle.Dependency
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.nullable
import org.mockito.kotlin.whenever
import java.security.InvalidParameterException
import kotlin.test.assertFailsWith

class ResolveDependencyTest {
  @Test
  fun resolveInvalidDependency() {
    assertFailsWith(InvalidParameterException::class) {
      doTest("bla", "1.0.0", "com.android:lib:1.0.1", "bla")
    }
    assertFailsWith(InvalidParameterException::class) {
      doTest("bla:bla", "1.0.0", "com.android:lib:1.0.1", "bla")
    }
    assertFailsWith(InvalidParameterException::class) {
      doTest("bla", "1.0.0", "not_found", "bla")
    }
    assertFailsWith(InvalidParameterException::class) {
      doTest("bla", "bla2", "not_found", "bla")
    }
  }

  @Test
  fun useMaxRevision() {
    doTest("com.android:lib:+", "1.0.0", "com.android:lib:1.0.1", "com.android:lib:1.0.1")
    doTest("com.android:lib:+", "1.0.2", "com.android:lib:2.0.0", "com.android:lib:2.0.0")
    doTest("com.android:lib:+", "1.0.2", "com.android:lib:1.0.1", "com.android:lib:1.0.2")
    doTest("com.android:lib:+", "1.1.+", "com.android:lib:1.0.1", "com.android:lib:1.1.+")
    doTest("com.android:lib:+", "1.1.+", "com.android:lib:1.1.1", "com.android:lib:1.1.1")
    doTest("com.android:lib:+", "1.1.+", "com.android:lib:1.2.1", "com.android:lib:1.2.1") // 1.1.+ accepts (but does not contain) 1.2.1
    doTest("com.android:lib:1.0.+", "1.0.2", "com.android:lib:1.0.1", "com.android:lib:1.0.2")
    doTest("com.android:lib:1.0.+", null, "com.android:lib:1.0.1", "com.android:lib:1.0.1")
    doTest("com.android:lib:1.0.2", "1.0.0", "notfound", "com.android:lib:1.0.0") // Not found, return default
    doTest("com.android:lib:1.0.2", null, "notfound", "com.android:lib:1.0.2") // Not found, no default, return given dependency
    doTest("com.android:lib:+", null, "notfound", "com.android:lib:+") // Not found, no default, return given dependency
  }

  // From mockito-kotlin
  private fun <T> any(type: Class<T>): T = Mockito.any<T>(type)

  private fun doTest(dependency: String, minRevision: String?, resolved: String, expectResultString: String) {
    val mockRepo = Mockito.mock(RepositoryUrlManager::class.java)
    whenever(mockRepo.resolveDependency(any(Dependency::class.java), nullable(Project::class.java), nullable(AndroidSdkHandler::class.java)))
      .thenReturn(Component.tryParse(resolved))

    val expectResult = Dependency.parse(expectResultString)
    assertThat(resolveDependency(mockRepo, dependency, minRevision)).isEqualTo(expectResult)
  }
}