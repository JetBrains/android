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
import com.android.tools.idea.nav.safeargs.extensions.replaceWithoutSaving
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesModificationListener
import com.android.tools.idea.nav.safeargs.psi.SafeArgsFeatureVersions
import com.android.tools.idea.nav.safeargs.psi.xml.findChildTagElementById
import com.android.tools.idea.nav.safeargs.psi.xml.findChildTagElementByNameAttr
import com.android.tools.idea.nav.safeargs.psi.xml.findXmlTagById
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.RunsInEdt
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.psi
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.descriptors.Visibilities
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
class DirectionsClassResolveExtensionTest(
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
  fun createsFunctionsForDestinations() {
    addNavXml(
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/main"
            app:startDestination="@id/fragment1">
          <action
              android:id="@+id/action_inherited"/>
          <fragment
              android:id="@+id/fragment1"
              android:name=".Fragment1"
              android:label="Fragment1">
            <action
                android:id="@+id/action_fragment1_to_fragment2"
                app:destination="@+id/fragment2"/>
            <action
                android:id="@+id/action_fragment1_to_main"
                app:popUpTo="@+id/main"/>
          </fragment>
          <fragment
              android:id="@+id/fragment2">
          </fragment>
        </navigation>
      """
        .trimIndent()
    )

    analyzeFileContent(
      """
        package test.safeargs

        val x: ${caret}Fragment1Directions = TODO()
      """
        .trimIndent()
    ) { symbol: KtNamedClassOrObjectSymbol ->
      assertThat(symbol.psi<KtElement>().isFromResolveExtension).isTrue()
      assertThat(symbol.classIdIfNonLocal?.asFqNameString())
        .isEqualTo("test.safeargs.Fragment1Directions")
      assertThat(getPrimaryConstructorSymbol(symbol).visibility).isEqualTo(Visibilities.Private)
      assertThat(getRenderedMemberFunctions(symbol, RENDERER)).isEmpty()
      assertThat(getRenderedMemberFunctions(symbol.companionObject!!, RENDERER))
        .containsExactly(
          "fun actionFragment1ToFragment2(): androidx.navigation.NavDirections",
          "fun actionFragment1ToMain(): androidx.navigation.NavDirections",
          "fun actionInherited(): androidx.navigation.NavDirections",
        )
    }
  }

  @Test
  fun handlesModifications() {
    val xmlFile =
      addNavXml(
        """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/main"
            app:startDestination="@id/fragment1">
          <action
              android:id="@+id/action_inherited"/>
          <fragment
              android:id="@+id/fragment1"
              android:name=".Fragment1"
              android:label="Fragment1">
            <action
                android:id="@+id/action_fragment1_to_fragment2"
                app:destination="@+id/fragment2"/>
            <action
                android:id="@+id/action_fragment1_to_main"
                app:popUpTo="@+id/main"/>
          </fragment>
          <fragment
              android:id="@+id/fragment2">
          </fragment>
        </navigation>
      """
          .trimIndent()
      )

    analyzeFileContent(
      """
        package test.safeargs

        val x = Fragment1Directions::${caret}actionFragment1ToFragment2
      """
        .trimIndent()
    ) { symbol: KtFunctionSymbol ->
      assertThat(symbol.valueParameters).isEmpty()
    }

    WriteCommandAction.runWriteCommandAction(safeArgsRule.project) {
      xmlFile.virtualFile.replaceWithoutSaving(
        "@+id/action_fragment1_to_fragment2",
        "@+id/some_other_action",
        safeArgsRule.project,
      )
    }
    safeArgsRule.waitForPendingUpdates()

    analyzeFileContent(
      """
        package test.safeargs

        val x = Fragment1Directions::${caret}someOtherAction
      """
        .trimIndent()
    ) { symbol: KtFunctionSymbol ->
      assertThat(symbol.valueParameters).isEmpty()
    }
  }

  @Test
  fun handlesModeChange() {
    val xmlFile =
      addNavXml(
        """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/main"
            app:startDestination="@id/fragment1">
          <action
              android:id="@+id/action_inherited"/>
          <fragment
              android:id="@+id/fragment1"
              android:name=".Fragment1"
              android:label="Fragment1">
            <action
                android:id="@+id/action_fragment1_to_fragment2"
                app:destination="@+id/fragment2"/>
            <action
                android:id="@+id/action_fragment1_to_main"
                app:popUpTo="@+id/main"/>
          </fragment>
          <fragment
              android:id="@+id/fragment2">
          </fragment>
        </navigation>
      """
          .trimIndent()
      )

    analyzeFileContent(
      """
        package test.safeargs

        val x: Fragment1Directions = TODO()
        val y = ${caret}x
      """
        .trimIndent()
    ) { symbol: KtPropertySymbol ->
      assertThat(symbol.returnType).isNotInstanceOf(KtErrorType::class.java)
    }

    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.NONE

    analyzeFileContent(
      """
        package test.safeargs

        val x: Fragment1Directions = TODO()
        val y = ${caret}x
      """
        .trimIndent()
    ) { symbol: KtPropertySymbol ->
      assertThat(symbol.returnType).isInstanceOf(KtErrorType::class.java)
    }

    // Change should be picked up after we change modes again.
    WriteCommandAction.runWriteCommandAction(safeArgsRule.project) {
      xmlFile.virtualFile.replaceWithoutSaving(
        "@+id/action_fragment1_to_fragment2",
        "@+id/some_other_action",
        safeArgsRule.project,
      )
    }
    safeArgsRule.waitForPendingUpdates()
    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.KOTLIN

    analyzeFileContent(
      """
        package test.safeargs

        val x = Fragment1Directions::${caret}someOtherAction
      """
        .trimIndent()
    ) { symbol: KtFunctionSymbol ->
      assertThat(symbol.valueParameters).isEmpty()
    }
  }

  @Test
  fun mapsArguments() {
    addKotlinSource(
      """
        package other

        enum class ArgEnum { FOO, BAR }
      """
        .trimIndent()
    )
    addNavXml(
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/main"
            app:startDestination="@id/fragment1">
          <fragment
              android:id="@+id/fragment1"
              android:name=".Fragment1"
              android:label="Fragment1">
            <action
                android:id="@+id/action_scalar"
                app:destination="@+id/fragment2">
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
            </action>
            <action
                android:id="@+id/action_array"
                app:destination="@+id/fragment2">
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
            </action>
          </fragment>
          <fragment
              android:id="@+id/fragment2">
          </fragment>
        </navigation>
      """
        .trimIndent()
    )

    analyzeFileContent(
      """
        package test.safeargs

        enum class ArgEnum { BAZ, QUUX }

        val x: ${caret}Fragment1Directions = TODO()
      """
        .trimIndent()
    ) { symbol: KtNamedClassOrObjectSymbol ->
      assertThat(getRenderedMemberFunctions(symbol.companionObject!!, RENDERER))
        .containsExactly(
          "fun actionScalar(" +
            "stringArg: kotlin.String, " +
            "intArg: kotlin.Int, " +
            "referenceArg: kotlin.Int, " +
            "longArg: kotlin.Long, " +
            "floatArg: kotlin.Float, " +
            "booleanArg: kotlin.Boolean, " +
            "inPackageEnumArg: test.safeargs.ArgEnum, " +
            "outOfPackageEnumArg: other.ArgEnum" +
            "): androidx.navigation.NavDirections",
          "fun actionArray(" +
            "stringArray: kotlin.Array<kotlin.String>, " +
            "intArray: kotlin.IntArray, " +
            "referenceArray: kotlin.IntArray, " +
            "longArray: kotlin.LongArray, " +
            "floatArray: kotlin.FloatArray, " +
            "booleanArray: kotlin.BooleanArray, " +
            "inPackageEnumArray: kotlin.Array<test.safeargs.ArgEnum>, " +
            "outOfPackageEnumArray: kotlin.Array<other.ArgEnum>" +
            "): androidx.navigation.NavDirections",
        )
    }
  }

  @Test
  fun combinesArgumentsFromActionAndDestination() {
    addNavXml(
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/main"
            app:startDestination="@id/fragment1">
          <fragment
              android:id="@+id/fragment1"
              android:name=".Fragment1"
              android:label="Fragment1">
            <action
                android:id="@+id/action_fragment1_to_fragment2"
                app:destination="@+id/fragment2">
              <argument
                  android:name="argument_from_action"
                  app:argType="integer"/>
            </action>
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name=".Fragment2"
              android:label="Fragment2">
            <argument
                android:name="argument_from_destination"
                app:argType="integer"/>
          </fragment>
        </navigation>
      """
        .trimIndent()
    )

    analyzeFileContent(
      """
        package test.safeargs

        val x = Fragment1Directions::${caret}actionFragment1ToFragment2
      """
        .trimIndent()
    ) { symbol: KtFunctionSymbol ->
      assertThat(symbol.render(RENDERER))
        .isEqualTo(
          "fun actionFragment1ToFragment2(" +
            "argumentFromAction: kotlin.Int, " +
            "argumentFromDestination: kotlin.Int" +
            "): androidx.navigation.NavDirections"
        )
    }
  }

  @Test
  fun overridesDefaultArgumentsFromDestinationWithAction() {
    addNavXml(
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/main"
            app:startDestination="@id/fragment1">
          <fragment
              android:id="@+id/fragment1"
              android:name=".Fragment1"
              android:label="Fragment1">
            <action
                android:id="@+id/action_fragment1_to_fragment2"
                app:destination="@+id/fragment2">
              <argument
                  android:name="argument"
                  app:argType="integer"
                  android:defaultValue="42"/>
            </action>
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name=".Fragment2"
              android:label="Fragment2">
            <argument
                android:name="argument"
                app:argType="integer"/>
          </fragment>
        </navigation>
      """
        .trimIndent()
    )

    analyzeFileContent(
      """
        package test.safeargs

        val x = Fragment1Directions::${caret}actionFragment1ToFragment2
      """
        .trimIndent()
    ) { symbol: KtFunctionSymbol ->
      assertThat(symbol.render(RENDERER))
        .isEqualTo(
          "fun actionFragment1ToFragment2(argument: kotlin.Int = ...): androidx.navigation.NavDirections"
        )
    }
  }

  @Test
  fun adjustsDefaultArgumentOrder() {
    addNavXml(
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/main"
            app:startDestination="@id/fragment1">
          <fragment
              android:id="@+id/fragment1"
              android:name=".Fragment1"
              android:label="Fragment1">
            <action
                android:id="@+id/action_fragment1_to_fragment2"
                app:destination="@+id/fragment2">
              <argument
                  android:name="argument_before"
                  app:argType="integer"/>
              <argument
                  android:name="argument_with_default"
                  app:argType="integer"
                  android:defaultValue="42"/>
              <argument
                  android:name="argument_after"
                  app:argType="integer"/>
            </action>
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name=".Fragment2"
              android:label="Fragment2">
          </fragment>
        </navigation>
      """
        .trimIndent()
    )

    analyzeFileContent(
      """
        package test.safeargs

        val x = Fragment1Directions::${caret}actionFragment1ToFragment2
      """
        .trimIndent()
    ) { symbol: KtFunctionSymbol ->
      val argumentBody =
        if (navVersion >= SafeArgsFeatureVersions.ADJUST_PARAMS_WITH_DEFAULTS) {
          "argumentBefore: kotlin.Int, argumentAfter: kotlin.Int, argumentWithDefault: kotlin.Int = ..."
        } else {
          "argumentBefore: kotlin.Int, argumentWithDefault: kotlin.Int = ..., argumentAfter: kotlin.Int"
        }
      assertThat(symbol.render(RENDERER))
        .isEqualTo(
          "fun actionFragment1ToFragment2($argumentBody): androidx.navigation.NavDirections"
        )
    }
  }

  @Test
  fun navigationElements() {
    val xmlFile =
      addNavXml(
        """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/main"
            app:startDestination="@id/fragment1">
          <fragment
              android:id="@+id/fragment1"
              android:name=".Fragment1"
              android:label="Fragment1">
            <action
                android:id="@+id/some_action"
                app:destination="@+id/fragment2">
              <argument
                  android:name="overridden"
                  app:argType="integer"
                  android:defaultValue="42"/>
              <argument
                  android:name="from_action"
                  app:argType="integer"
                  android:defaultValue="@null"/>
            </action>
          </fragment>
          <fragment
              android:id="@+id/fragment2"
              android:name=".Fragment2"
              android:label="Fragment2">
            <argument
                android:name="overridden"
                app:argType="integer"/>
            <argument
                android:name="from_destination"
                app:argType="integer"
                android:defaultValue="@null"/>
          </fragment>
        </navigation>
      """
          .trimIndent()
      )

    val fragment1Tag = xmlFile.findXmlTagById("fragment1")!!
    val actionTag = fragment1Tag.findChildTagElementById("action", "some_action")!!
    val argOverriddenInActionTag =
      actionTag.findChildTagElementByNameAttr("argument", "overridden")!!
    val argFromActionTag = actionTag.findChildTagElementByNameAttr("argument", "from_action")!!
    val fragment2Tag = xmlFile.findXmlTagById("fragment2")!!
    val argFromDestinationTag =
      fragment2Tag.findChildTagElementByNameAttr("argument", "from_destination")!!

    "val x: ${caret}Fragment1Directions" navigatesTo fragment1Tag
    "val x = Fragment1Directions.${caret}someAction()" navigatesTo actionTag
    "val x = Fragment1Directions.someAction(${caret}fromAction = 42)" navigatesTo argFromActionTag
    "val x = Fragment1Directions.someAction(${caret}overridden = 42)" navigatesTo
      argOverriddenInActionTag
    "val x = Fragment1Directions.someAction(${caret}fromDestination = 42)" navigatesTo
      argFromDestinationTag
  }

  private infix fun @receiver:Language("kotlin") String.navigatesTo(target: PsiElement?) {
    analyzeFileContent(
      """
        package test.safeargs

        ${this}
      """
        .trimIndent()
    ) { symbol: KtSymbol ->
      assertThat(getResolveExtensionPsiNavigationTargets(symbol))
        .containsExactlyElementsIn(listOfNotNull(target))
    }
  }
}
