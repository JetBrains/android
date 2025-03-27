/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.lang.parser

import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeElement
import com.google.common.reflect.ClassPath
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat

class DeclarativePsiTest: TestCase() {
  fun testAllPsiInterfacesHasDeclarativeParent(){
    val classes =
      ClassPath
        .from(DeclarativePsiTest::class.java.classLoader)
        // it's a guarantee that impl has all generated files
        .getTopLevelClasses("com.android.tools.idea.gradle.dcl.lang.psi.impl")

    assertThat(classes).isNotEmpty()
    val map = classes.map { it.name to DeclarativeElement::class.java.isAssignableFrom(it.load()) }
    assertThat(map).allMatch { it.second }
  }
}