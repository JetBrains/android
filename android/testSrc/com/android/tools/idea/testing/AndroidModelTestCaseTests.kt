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
package com.android.tools.idea.testing

import com.android.tools.idea.testing.fixtures.AndroidModelTestCase
import com.android.tools.idea.testing.fixtures.CommonModelFactories
import com.android.tools.idea.testing.fixtures.ModelToTestProjectConverter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager

class SingleModuleTest : AndroidModelTestCase(CommonModelFactories.SINGLE_LIB_MODULE, ModelToTestProjectConverter.Mode.DISK) {
  fun testFacade() {
    fixture.addFileToProject(
      "lib/src/main/com/example/MyActivity.java",
      "package com.example; public class MyActivity {}"
    )
    assertNotNull(
      fixture.javaFacade.findClass(
        "com.example.MyActivity",
        fixture.module.getModuleWithDependenciesAndLibrariesScope(false)
      )
    )
    assertNotNull(
      fixture.javaFacade.findClass(
        "android.app.Activity",
        fixture.module.getModuleWithDependenciesAndLibrariesScope(false)
      )
    )
  }
}

class MultiModuleTest : AndroidModelTestCase(CommonModelFactories.APP_AND_LIB, ModelToTestProjectConverter.Mode.DISK) {
  fun testFacade() {
    fixture.addFileToProject(
      "app/src/main/com/example/AppClass.java",
      "package com.example; public class AppClass {}"
    )
    fixture.addFileToProject(
      "lib/src/main/com/example/LibClass.java",
      "package com.example; public class LibClass {}"
    )

    val app = fixture.module
    val lib = runReadAction { ModuleManager.getInstance(project).findModuleByName("lib")!! }

    assertNotNull(
      fixture.javaFacade.findClass(
        "com.example.AppClass",
        app.getModuleWithDependenciesAndLibrariesScope(false)
      )
    )
    assertNotNull(
      fixture.javaFacade.findClass(
        "com.example.AppClass",
        app.getModuleWithDependenciesAndLibrariesScope(false)
      )
    )

    assertNotNull(
      fixture.javaFacade.findClass(
        "com.example.LibClass",
        lib.getModuleWithDependenciesAndLibrariesScope(false)
      )
    )
    assertNotNull(
      fixture.javaFacade.findClass(
        "android.app.Activity",
        lib.getModuleWithDependenciesAndLibrariesScope(false)
      )
    )

    // Dependency is set up correctly:
    assertNotNull(
      fixture.javaFacade.findClass(
        "com.example.LibClass",
        app.getModuleWithDependenciesAndLibrariesScope(false)
      )
    )
  }
}
