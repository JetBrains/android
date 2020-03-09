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
package com.android.tools.idea.nav.safeargs.psi

import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.nav.safeargs.extensions.Parameter
import com.android.tools.idea.nav.safeargs.extensions.checkSignaturesAndReturnType
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class LightArgsBuilderClassTest {
  @get:Rule
  val safeArgsRule = SafeArgsRule()

  @Test
  fun canFindArgsBuilderClasses() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment1"
              android:name="test.safeargs.Fragment1"
              android:label="Fragment1">
            <argument
                android:name="arg"
                app:argType="string" />
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name="test.safeargs.Fragment2"
              android:label="Fragment2">
            <argument
                android:name="arg"
                app:argType="string" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment1 {}")

    // Classes can be found with context
    assertThat(safeArgsRule.fixture.findClass("test.safeargs.Fragment1Args.Builder", context)).isInstanceOf(
      LightArgsBuilderClass::class.java)
    assertThat(safeArgsRule.fixture.findClass("test.safeargs.Fragment2Args.Builder", context)).isInstanceOf(
      LightArgsBuilderClass::class.java)

    // ... but cannot be found without context
    val psiFacade = JavaPsiFacade.getInstance(safeArgsRule.project)
    assertThat(psiFacade.findClass("test.safeargs.Fragment1Args.Builder", GlobalSearchScope.allScope(safeArgsRule.project))).isNull()
  }

  @Test
  fun expectedBuilderConstructorsAndMethodsAreCreated_withIntArg() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment"
              android:name="test.safeargs.Fragment"
              android:label="Fragment">
            <argument
                android:name="arg1"
                app:argType="integer" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    builderClass.constructors.let { constructors ->
      assertThat(constructors.size).isEqualTo(2)
      constructors[0].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("original", "FragmentArgs")
        )
      )

      constructors[1].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("arg1", PsiType.INT.name)
        )
      )
    }

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    // class) and an arg constructor (which takes all arguments specified by <argument> tags)
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)

      methods[0].checkSignaturesAndReturnType(
        name = "setArg1",
        returnType = "Builder",
        parameters = listOf(
          Parameter("arg1", PsiType.INT.name)
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = PsiType.INT.name
      )

      methods[2].checkSignaturesAndReturnType(
        name = "build",
        returnType = "FragmentArgs"
      )
    }
  }

  @Test
  fun expectedBuilderConstructorsAndMethodsAreCreated_withFloatArg() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment"
              android:name="test.safeargs.Fragment"
              android:label="Fragment">
            <argument
                android:name="arg1"
                app:argType="float" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    builderClass.constructors.let { constructors ->
      assertThat(constructors.size).isEqualTo(2)
      constructors[0].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("original", "FragmentArgs")
        )
      )

      constructors[1].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("arg1", PsiType.FLOAT.name)
        )
      )
    }

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    // class) and an arg constructor (which takes all arguments specified by <argument> tags)
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)

      methods[0].checkSignaturesAndReturnType(
        name = "setArg1",
        returnType = "Builder",
        parameters = listOf(
          Parameter("arg1", PsiType.FLOAT.name)
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = PsiType.FLOAT.name
      )

      methods[2].checkSignaturesAndReturnType(
        name = "build",
        returnType = "FragmentArgs"
      )
    }
  }

  @Test
  fun expectedBuilderConstructorsAndMethodsAreCreated_withLongArg() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment"
              android:name="test.safeargs.Fragment"
              android:label="Fragment">
            <argument
                android:name="arg1"
                app:argType="long" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    builderClass.constructors.let { constructors ->
      assertThat(constructors.size).isEqualTo(2)
      constructors[0].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("original", "FragmentArgs")
        )
      )

      constructors[1].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("arg1", PsiType.LONG.name)
        )
      )
    }

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    // class) and an arg constructor (which takes all arguments specified by <argument> tags)
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)

      methods[0].checkSignaturesAndReturnType(
        name = "setArg1",
        returnType = "Builder",
        parameters = listOf(
          Parameter("arg1", PsiType.LONG.name)
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = PsiType.LONG.name
      )

      methods[2].checkSignaturesAndReturnType(
        name = "build",
        returnType = "FragmentArgs"
      )
    }
  }

  @Test
  fun expectedBuilderConstructorsAndMethodsAreCreated_withBooleanArg() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment"
              android:name="test.safeargs.Fragment"
              android:label="Fragment">
            <argument
                android:name="arg1"
                app:argType="boolean" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    builderClass.constructors.let { constructors ->
      assertThat(constructors.size).isEqualTo(2)
      constructors[0].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("original", "FragmentArgs")
        )
      )

      constructors[1].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("arg1", PsiType.BOOLEAN.name)
        )
      )
    }

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    // class) and an arg constructor (which takes all arguments specified by <argument> tags)
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)

      methods[0].checkSignaturesAndReturnType(
        name = "setArg1",
        returnType = "Builder",
        parameters = listOf(
          Parameter("arg1", PsiType.BOOLEAN.name)
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = PsiType.BOOLEAN.name
      )

      methods[2].checkSignaturesAndReturnType(
        name = "build",
        returnType = "FragmentArgs"
      )
    }
  }

  @Test
  fun expectedBuilderConstructorsAndMethodsAreCreated_withStringArg() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment"
              android:name="test.safeargs.Fragment"
              android:label="Fragment">
            <argument
                android:name="arg1"
                app:argType="string" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    builderClass.constructors.let { constructors ->
      assertThat(constructors.size).isEqualTo(2)
      constructors[0].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("original", "FragmentArgs")
        )
      )

      constructors[1].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("arg1", "String")
        )
      )
    }

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    // class) and an arg constructor (which takes all arguments specified by <argument> tags)
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)

      methods[0].checkSignaturesAndReturnType(
        name = "setArg1",
        returnType = "Builder",
        parameters = listOf(
          Parameter("arg1", "String")
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = "String"
      )

      methods[2].checkSignaturesAndReturnType(
        name = "build",
        returnType = "FragmentArgs"
      )
    }
  }

  @Test
  fun expectedBuilderConstructorsAndMethodsAreCreated_withReferenceArg() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment"
              android:name="test.safeargs.Fragment"
              android:label="Fragment">
            <argument
                android:name="arg1"
                app:argType="reference" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    builderClass.constructors.let { constructors ->
      assertThat(constructors.size).isEqualTo(2)
      constructors[0].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("original", "FragmentArgs")
        )
      )

      constructors[1].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("arg1", PsiType.INT.name)
        )
      )
    }

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    // class) and an arg constructor (which takes all arguments specified by <argument> tags)
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)

      methods[0].checkSignaturesAndReturnType(
        name = "setArg1",
        returnType = "Builder",
        parameters = listOf(
          Parameter("arg1", PsiType.INT.name)
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = PsiType.INT.name
      )

      methods[2].checkSignaturesAndReturnType(
        name = "build",
        returnType = "FragmentArgs"
      )
    }
  }

  @Test
  fun expectedBuilderConstructorsAndMethodsAreCreated_withParcelableArg() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment"
              android:name="test.safeargs.Fragment"
              android:label="Fragment">
            <argument
                android:name="arg1"
                app:argType="test.safeargs.MyParcelable" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    builderClass.constructors.let { constructors ->
      assertThat(constructors.size).isEqualTo(2)
      constructors[0].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("original", "FragmentArgs")
        )
      )

      constructors[1].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("arg1", "MyParcelable")
        )
      )
    }

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    // class) and an arg constructor (which takes all arguments specified by <argument> tags)
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)

      methods[0].checkSignaturesAndReturnType(
        name = "setArg1",
        returnType = "Builder",
        parameters = listOf(
          Parameter("arg1", "MyParcelable")
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = "MyParcelable"
      )

      methods[2].checkSignaturesAndReturnType(
        name = "build",
        returnType = "FragmentArgs"
      )
    }
  }

  @Test
  fun expectedBuilderConstructorsAndMethodsAreCreated_withSerializableArg() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment"
              android:name="test.safeargs.Fragment"
              android:label="Fragment">
            <argument
                android:name="arg1"
                app:argType="test.safeargs.MySerializable" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    builderClass.constructors.let { constructors ->
      assertThat(constructors.size).isEqualTo(2)
      constructors[0].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("original", "FragmentArgs")
        )
      )

      constructors[1].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("arg1", "MySerializable")
        )
      )
    }

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    // class) and an arg constructor (which takes all arguments specified by <argument> tags)
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)

      methods[0].checkSignaturesAndReturnType(
        name = "setArg1",
        returnType = "Builder",
        parameters = listOf(
          Parameter("arg1", "MySerializable")
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = "MySerializable"
      )

      methods[2].checkSignaturesAndReturnType(
        name = "build",
        returnType = "FragmentArgs"
      )
    }
  }

  @Test
  fun expectedBuilderConstructorsAndMethodsAreCreated_withEnumArg() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment"
              android:name="test.safeargs.Fragment"
              android:label="Fragment">
            <argument
                android:name="arg1"
                app:argType="test.safeargs.MyEnum" />
          </fragment>
        </navigation>
      """.trimIndent())

    // Initialize repository after creating resources, needed for codegen to work
    ResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources

    val context = safeArgsRule.fixture.addClass("package test.safeargs; public class Fragment {}")

    // Classes can be found with context
    val builderClass = safeArgsRule.fixture.findClass("test.safeargs.FragmentArgs.Builder", context) as LightArgsBuilderClass

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    builderClass.constructors.let { constructors ->
      assertThat(constructors.size).isEqualTo(2)
      constructors[0].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("original", "FragmentArgs")
        )
      )

      constructors[1].checkSignaturesAndReturnType(
        name = "Builder",
        returnType = PsiType.NULL.name,
        parameters = listOf(
          Parameter("arg1", "MyEnum")
        )
      )
    }

    // We expect two constructors - a copy constructor (which is initialized with the parent args
    // class) and an arg constructor (which takes all arguments specified by <argument> tags)
    builderClass.methods.let { methods ->
      assertThat(methods.size).isEqualTo(3)

      methods[0].checkSignaturesAndReturnType(
        name = "setArg1",
        returnType = "Builder",
        parameters = listOf(
          Parameter("arg1", "MyEnum")
        )
      )

      methods[1].checkSignaturesAndReturnType(
        name = "getArg1",
        returnType = "MyEnum"
      )

      methods[2].checkSignaturesAndReturnType(
        name = "build",
        returnType = "FragmentArgs"
      )
    }
  }
}