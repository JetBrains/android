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
package com.android.tools.idea.nav.safeargs.kotlin.k2

import com.android.ide.common.gradle.Version
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.nav.safeargs.extensions.replaceWithoutSaving
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesModificationListener
import com.android.tools.idea.nav.safeargs.psi.SafeArgsFeatureVersions
import com.android.tools.idea.nav.safeargs.psi.xml.findChildTagElementByNameAttr
import com.android.tools.idea.nav.safeargs.psi.xml.findXmlTagById
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.psi
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.psi.KtElement
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(KaExperimentalApi::class)
@RunWith(Parameterized::class)
@RunsInEdt
class ArgsClassResolveExtensionTest(
  @Suppress("UNUSED_PARAMETER") navVersionName: String,
  private val navVersion: Version,
) : AbstractSafeArgsResolveExtensionTest() {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0} ({1})")
    fun parameters(): Collection<Array<Any>> =
      KNOWN_SAFE_ARGS_VERSIONS.map { (name, version) -> arrayOf(name, version) }
  }

  @Before
  fun setUp() {
    assume().that(KotlinPluginModeProvider.currentPluginMode).isEqualTo(KotlinPluginMode.K2)
    if (navVersion > SafeArgsFeatureVersions.MINIMUM_VERSION) {
      safeArgsRule.addFakeNavigationDependency(navVersion)
    }
    NavigationResourcesModificationListener.ensureSubscribed(safeArgsRule.project)
  }

  @Test
  fun createsFragmentArgsClassWithConverters() {
    addNavXml(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:id="@+id/main"
          app:startDestination="@id/fragment1">
        <fragment
            android:id="@+id/fragment1"
            android:name="test.safeargs.Fragment1"
            android:label="Fragment1">
          <argument
              android:name="some_argument"
              app:argType="string"/>
        </fragment>
      </navigation>
    """
        .trimIndent()
    )

    analyzeFileContent(
      """
      package test.safeargs

      val x: ${caret}Fragment1Args = TODO()
      """
        .trimIndent()
    ) { symbol: KaNamedClassSymbol ->
      assertThat(symbol.psi<KtElement>().isFromResolveExtension).isTrue()
      assertThat(symbol.classId?.asFqNameString()).isEqualTo("test.safeargs.Fragment1Args")
      assertThat(symbol.render(RENDERER))
        .isEqualTo(
          "data class Fragment1Args(someArgument: kotlin.String) : androidx.navigation.NavArgs"
        )

      assertThat(getRenderedMemberFunctions(symbol, RENDERER))
        .containsExactlyElementsIn(
          listOfNotNull(
            "fun toBundle(): android.os.Bundle",
            "fun toSavedStateHandle(): androidx.lifecycle.SavedStateHandle"
              .takeIf { navVersion >= SafeArgsFeatureVersions.TO_SAVED_STATE_HANDLE },
          )
        )

      assertThat(getRenderedMemberFunctions(symbol.companionObject!!, RENDERER))
        .containsExactlyElementsIn(
          listOfNotNull(
            """
            @kotlin.jvm.JvmStatic
            fun fromBundle(bundle: android.os.Bundle): test.safeargs.Fragment1Args
          """
              .trimIndent(),
            """
            @kotlin.jvm.JvmStatic
            fun fromSavedStateHandle(handle: androidx.lifecycle.SavedStateHandle): test.safeargs.Fragment1Args
          """
              .trimIndent()
              .takeIf { navVersion >= SafeArgsFeatureVersions.FROM_SAVED_STATE_HANDLE },
          )
        )
    }
  }

  @Test
  fun handlesModification() {
    val xmlFile =
      addNavXml(
        """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:id="@+id/main"
          app:startDestination="@id/fragment1">
        <fragment
            android:id="@+id/fragment1"
            android:name="test.safeargs.Fragment1"
            android:label="Fragment1">
          <argument
              android:name="some_argument"
              app:argType="string"/>
        </fragment>
      </navigation>
    """
          .trimIndent()
      )

    analyzeFileContent(
      """
        package test.safeargs
        val x: Fragment1Args = TODO()
        val y = x.${caret}someArgument
      """
        .trimIndent()
    ) { symbol: KaPropertySymbol ->
      assertThat(symbol.returnType.isStringType).isTrue()
    }

    val project = safeArgsRule.project
    WriteCommandAction.runWriteCommandAction(project) {
      xmlFile.virtualFile.replaceWithoutSaving("some_argument", "some_other_argument", project)
    }
    safeArgsRule.waitForPendingUpdates()

    analyzeFileContent(
      """
        package test.safeargs
        val x: Fragment1Args = TODO()
        val y = x.${caret}someOtherArgument
      """
        .trimIndent()
    ) { symbol: KaPropertySymbol ->
      assertThat(symbol.returnType.isStringType).isTrue()
    }
  }

  @Test
  fun handlesModeChange() {
    val xmlFile =
      addNavXml(
        """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:id="@+id/main"
          app:startDestination="@id/fragment1">
        <fragment
            android:id="@+id/fragment1"
            android:name="test.safeargs.Fragment1"
            android:label="Fragment1">
          <argument
              android:name="some_argument"
              app:argType="string"/>
        </fragment>
      </navigation>
    """
          .trimIndent()
      )

    analyzeFileContent(
      """
        package test.safeargs
        val x: Fragment1Args = TODO()
        val y = ${caret}x
      """
        .trimIndent()
    ) { symbol: KaPropertySymbol ->
      assertThat(symbol.returnType).isNotInstanceOf(KaErrorType::class.java)
    }

    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.NONE

    analyzeFileContent(
      """
        package test.safeargs
        val x: Fragment1Args = TODO()
        val y = ${caret}x
      """
        .trimIndent()
    ) { symbol: KaPropertySymbol ->
      assertThat(symbol.returnType).isInstanceOf(KaErrorType::class.java)
    }

    // This change should be picked up when the mode changes back.
    WriteCommandAction.runWriteCommandAction(safeArgsRule.project) {
      xmlFile.virtualFile.replaceWithoutSaving(
        "some_argument",
        "some_other_argument",
        safeArgsRule.project,
      )
    }
    safeArgsRule.waitForPendingUpdates()
    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.KOTLIN

    analyzeFileContent(
      """
        package test.safeargs
        val x: Fragment1Args = TODO()
        val y = x.${caret}someOtherArgument
      """
        .trimIndent()
    ) { symbol: KaPropertySymbol ->
      assertThat(symbol.returnType.isStringType).isTrue()
    }
  }

  @Test
  fun mapsScalarArgumentTypes() {
    addKotlinSource(
      """
        package other

        enum class ArgEnum { FOO, BAR }
      """
        .trimIndent(),
      fileName = "otherPackage.kt",
    )
    addNavXml(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:id="@+id/main"
          app:startDestination="@id/fragment1">
        <fragment
            android:id="@+id/fragment1"
            android:name="test.safeargs.Fragment1"
            android:label="Fragment1">
          <argument
              android:name="string_arg"
              app:argType="string"/>
          <argument
              android:name="int_arg"
              app:argType="integer"/>
          <argument
              android:name="reference_arg"
              app:argType="reference"/>
          <argument
              android:name="long_arg"
              app:argType="long"/>
          <argument
              android:name="float_arg"
              app:argType="float"/>
          <argument
              android:name="boolean_arg"
              app:argType="boolean"/>
          <argument
              android:name="in_package_enum_arg"
              app:argType=".ArgEnum"/>
          <argument
              android:name="out_of_package_enum_arg"
              app:argType="other.ArgEnum"/>
        </fragment>
      </navigation>
    """
        .trimIndent()
    )

    analyzeFileContent(
      """
      package test.safeargs

      val x: ${caret}Fragment1Args = TODO()
    """
        .trimIndent()
    ) { symbol: KaNamedClassSymbol ->
      assertThat(symbol.classId?.asFqNameString()).isEqualTo("test.safeargs.Fragment1Args")
      assertThat(symbol.psi<KtElement>().isFromResolveExtension).isTrue()

      val paramsToTypeNames = getValueParameterNamesAndTypes(getPrimaryConstructorSymbol(symbol))

      assertThat(paramsToTypeNames)
        .containsExactlyEntriesIn(
          mapOf(
            "stringArg" to "kotlin.String",
            "intArg" to "kotlin.Int",
            "referenceArg" to "kotlin.Int",
            "longArg" to "kotlin.Long",
            "floatArg" to "kotlin.Float",
            "booleanArg" to "kotlin.Boolean",
            "inPackageEnumArg" to "test.safeargs.ArgEnum",
            "outOfPackageEnumArg" to "other.ArgEnum",
          )
        )
    }
  }

  @Test
  fun mapsArrayArgumentTypes() {
    addKotlinSource(
      """
        package other

        enum class ArgEnum { FOO, BAR }
      """
        .trimIndent(),
      fileName = "otherPackage.kt",
    )
    addNavXml(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:id="@+id/main"
          app:startDestination="@id/fragment1">
        <fragment
            android:id="@+id/fragment1"
            android:name="test.safeargs.Fragment1"
            android:label="Fragment1">
          <argument
              android:name="string_array"
              app:argType="string[]"/>
          <argument
              android:name="int_array"
              app:argType="integer[]"/>
          <argument
              android:name="reference_array"
              app:argType="reference[]"/>
          <argument
              android:name="long_array"
              app:argType="long[]"/>
          <argument
              android:name="float_array"
              app:argType="float[]"/>
          <argument
              android:name="boolean_array"
              app:argType="boolean[]"/>
          <argument
              android:name="in_package_enum_array"
              app:argType=".ArgEnum[]"/>
          <argument
              android:name="out_of_package_enum_array"
              app:argType="other.ArgEnum[]"/>
        </fragment>
      </navigation>
    """
        .trimIndent()
    )

    analyzeFileContent(
      """
      package test.safeargs

      val x: ${caret}Fragment1Args = TODO()
    """
        .trimIndent()
    ) { symbol: KaNamedClassSymbol ->
      assertThat(symbol.classId?.asFqNameString()).isEqualTo("test.safeargs.Fragment1Args")
      assertThat(symbol.psi<KtElement>().isFromResolveExtension).isTrue()

      val paramsToTypeNames = getValueParameterNamesAndTypes(getPrimaryConstructorSymbol(symbol))

      assertThat(paramsToTypeNames)
        .containsExactlyEntriesIn(
          mapOf(
            "stringArray" to "kotlin.Array<kotlin.String>",
            "intArray" to "kotlin.IntArray",
            "referenceArray" to "kotlin.IntArray",
            "longArray" to "kotlin.LongArray",
            "floatArray" to "kotlin.FloatArray",
            "booleanArray" to "kotlin.BooleanArray",
            "inPackageEnumArray" to "kotlin.Array<test.safeargs.ArgEnum>",
            "outOfPackageEnumArray" to "kotlin.Array<other.ArgEnum>",
          )
        )
    }
  }

  @Test
  fun handlesNullables() {
    addNavXml(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:id="@+id/main"
          app:startDestination="@id/fragment1">
        <fragment
            android:id="@+id/fragment1"
            android:name="test.safeargs.Fragment1"
            android:label="Fragment1">
          <argument
              android:name="nullable"
              app:argType="string"
              app:nullable="true"/>
          <argument
              android:name="implicit_nullable"
              app:argType="string"
              android:defaultValue="@null"/>
        </fragment>
      </navigation>
    """
        .trimIndent()
    )

    analyzeFileContent(
      """
      package test.safeargs

      val x: ${caret}Fragment1Args = TODO()
      """
        .trimIndent()
    ) { symbol: KaNamedClassSymbol ->
      assertThat(symbol.render(RENDERER))
        .isEqualTo(
          "data class Fragment1Args(nullable: kotlin.String?, implicitNullable: kotlin.String? = ...) : androidx.navigation.NavArgs"
        )
    }
  }

  @Test
  @SafeArgsRule.PackageName("in.test.package")
  fun handlesEscaping() {
    addNavXml(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:id="@+id/main"
          app:startDestination="@id/fragment1">
        <fragment
            android:id="@+id/fragment1"
            android:name="in.test.package.Fragment1"
            android:label="Fragment1">
          <argument
            android:name="object"
            app:argType="string"/>
          <argument
            android:name="enum"
            app:argType=".ArgEnum"/>
          <argument
            android:name="enumArray"
            app:argType=".ArgEnum[]"/>
          <argument
            android:name="defaultValueWithNewline"
            app:argType="string"
            android:defaultValue="foo&#xA;`If you can see this, escaping has failed`: Nothing?,"/>
        </fragment>
      </navigation>
      """
        .trimIndent()
    )

    analyzeFileContent(
      """
        package `in`.test.`package`

        val x: ${caret}Fragment1Args = TODO()
      """
        .trimIndent()
    ) { symbol: KaNamedClassSymbol ->
      assertThat(symbol.classId?.asFqNameString()).isEqualTo("in.test.package.Fragment1Args")
      val parametersByName = getValueParameterNamesAndTypes(getPrimaryConstructorSymbol(symbol))

      assertThat(parametersByName)
        .containsExactlyEntriesIn(
          mapOf(
            "object" to "kotlin.String",
            "enum" to "`in`.test.`package`.ArgEnum",
            "enumArray" to "kotlin.Array<`in`.test.`package`.ArgEnum>",
            "defaultValueWithNewline" to "kotlin.String",
          )
        )
    }
  }

  @Test
  fun handlesDefaultValues() {
    addNavXml(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:id="@+id/main"
          app:startDestination="@id/fragment1">
        <fragment
            android:id="@+id/fragment1"
            android:name="test.safeargs.Fragment1"
            android:label="Fragment1">
          <argument
              android:name="arg1"
              app:argType="integer"/>
          <argument
              android:name="arg2"
              app:argType="string"
              android:defaultValue="foo"/>
          <argument
              android:name="arg3"
              app:argType="reference"/>
        </fragment>
      </navigation>
    """
        .trimIndent()
    )

    analyzeFileContent(
      """
      package test.safeargs

      val x: ${caret}Fragment1Args = TODO()
      """
        .trimIndent()
    ) { symbol: KaNamedClassSymbol ->
      val renderedValueParameters =
        getPrimaryConstructorSymbol(symbol).valueParameters.map { it.render(RENDERER) }

      val expectedOrder =
        if (navVersion >= SafeArgsFeatureVersions.ADJUST_PARAMS_WITH_DEFAULTS) {
          listOf("arg1: kotlin.Int", "arg3: kotlin.Int", "arg2: kotlin.String = ...")
        } else {
          listOf("arg1: kotlin.Int", "arg2: kotlin.String = ...", "arg3: kotlin.Int")
        }

      assertThat(renderedValueParameters).containsExactlyElementsIn(expectedOrder).inOrder()
    }
  }

  @Test
  fun navigationElements() {
    val xmlFile =
      addNavXml(
        """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:id="@+id/main"
          app:startDestination="@id/fragment1">
        <fragment
            android:id="@+id/fragment1"
            android:name="test.safeargs.Fragment1"
            android:label="Fragment1">
          <argument
              android:name="arg1"
              app:argType="integer"/>
        </fragment>
      </navigation>
    """
          .trimIndent()
      )
    val fragmentTag = xmlFile.findXmlTagById("fragment1")!!
    assertThat(fragmentTag.localName).isEqualTo("fragment")
    val argumentTag = fragmentTag.findChildTagElementByNameAttr("argument", "arg1")!!

    analyzeFileContent(
      """
      package test.safeargs

      val x: ${caret}Fragment1Args = TODO()
      """
        .trimIndent()
    ) { symbol: KaNamedClassSymbol ->
      assertThat(getResolveExtensionPsiNavigationTargets(symbol)).containsExactly(fragmentTag)
    }

    analyzeFileContent(
      """
      package test.safeargs

      val x = Fragment1Args(${caret}arg1 = 42)
      """
        .trimIndent()
    ) { symbol: KaParameterSymbol ->
      assertThat(getResolveExtensionPsiNavigationTargets(symbol)).containsExactly(argumentTag)
    }

    analyzeFileContent(
      """
      package test.safeargs

      val x: Fragment1Args = TODO()
      val y = x.${caret}arg1
      """
        .trimIndent()
    ) { symbol: KaPropertySymbol ->
      assertThat(getResolveExtensionPsiNavigationTargets(symbol)).containsExactly(argumentTag)
    }

    analyzeFileContent(
      """
      package test.safeargs

      val x = Fragment1Args.fromBundle(${caret}bundle = TODO())
      """
        .trimIndent()
    ) { symbol: KaParameterSymbol ->
      assertThat(getResolveExtensionPsiNavigationTargets(symbol)).containsExactly(fragmentTag)
    }

    analyzeFileContent(
      """
      package test.safeargs

      val x: Fragment1Args = TODO()
      val y = x.${caret}toBundle()
      """
        .trimIndent()
    ) { symbol: KaNamedFunctionSymbol ->
      assertThat(getResolveExtensionPsiNavigationTargets(symbol)).containsExactly(fragmentTag)
    }
  }
}
