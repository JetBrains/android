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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.helpers.booleanValues
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.KnownValues
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.gradle.structure.model.meta.annotateWithError
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.util.concurrent.ListenableFuture
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assume.assumeThat
import java.math.BigDecimal

class PsVariablesTest : AndroidGradleTestCase() {

  fun testGetBuildScriptVariables() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val variables = psProject.buildScriptVariables
    assertThat(
      variables.map { it.name to it.value },
      equalTo(listOf(
        "scriptVar1" to 1.asParsed(),
        "scriptVar2" to "2".asParsed(),
        "scriptBool" to true.asParsed(),
        "agpVersionX" to "3.4.x".asParsed()
      ))
    )
  }

  fun testGetProjectVariables() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val variables = psProject.variables
    assertThat(
      variables.map { it.name to it.value },
      equalTo(listOf(
        "someVar" to "Present".asParsed<Any>(),
        "rootBool" to ("scriptBool" to true).asParsed(),
        "rootBool3" to ("rootBool" to true).asParsed(),
        "rootBool2" to ("rootBool3" to true).asParsed(),
        "rootFloat" to BigDecimal("3.14").asParsed(),
        "listProp" to listOf(15.asParsed(),16.asParsed(),45.asParsed()).asParsed(),
        "mapProp" to mapOf("key1" to "val1".asParsed(), "key2" to "val2".asParsed()).asParsed(),
        "boolRoot" to true.asParsed(),
        "dependencyVersion" to "28.0.0".asParsed()
      ))
    )
  }

  fun testGetModuleVariables() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    val variables = psAppModule.variables
    assertThat(
      variables.map { it.name },
      equalTo(listOf(
        "myVariable",
        "variable1",
        "anotherVariable",
        "varInt",
        "varBool",
        "varRefString",
        "varProGuardFiles",
        "localList",
        "localMap",
        "valVersion",
        "versionVal",
        "moreVariable",
        "mapVariable"))
    )
  }

  fun testListVariables() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    val variables = psAppModule.variables
    val listVariable = variables.findElement("varProGuardFiles")!!

    assertThat(listVariable.listItems.map { it.value },
               equalTo(listOf("proguard-rules.txt".asParsed<Any>(), "proguard-rules2.txt".asParsed<Any>())))

    listVariable.listItems.findElement(0)!!.delete()

    assertThat(listVariable.listItems.map { it.value },
               equalTo(listOf("proguard-rules2.txt".asParsed<Any>())))
  }

  fun testAddingListVariables() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    val variables = psAppModule.variables
    var refreshed = 0
    variables.onChange(testRootDisposable) { refreshed++ }
    val listVar = variables.addNewListVariable("newList1")
    assertThat(refreshed, equalTo(1))
    listVar.addListValue("v1".asParsed())
    listVar.addListValue("v2".asParsed())
    assertThat(listVar.listItems.map { it.value }, equalTo(listOf("v1".asParsed<Any>(), "v2".asParsed<Any>())))
  }

  fun testAddingListVariables_viaEmptyItemState() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    val variables = psAppModule.variables
    var refreshed = 0
    variables.onChange(this.testRootDisposable) { refreshed++ }
    val listVar = variables.addNewListVariable("newList1")
    listVar.listItems.onChange(testRootDisposable) { refreshed++ }
    assertThat(refreshed, equalTo(1))

    val newItem1 = listVar.addListValue(ParsedValue.NotSet)
    // The new item is not added until a value is set.
    assertThat(listVar.listItems.map { it.value }, equalTo(listOf()))
    assertThat(refreshed, equalTo(1))

    newItem1.value = "v1".asParsed()
    assertThat(listVar.listItems.map { it.value }, equalTo(listOf("v1".asParsed<Any>())))
    assertThat(refreshed, equalTo(2))

    val newItem2 = listVar.addListValue(ParsedValue.NotSet)
    assertThat(listVar.listItems.map { it.value }, equalTo(listOf("v1".asParsed<Any>())))
    assertThat(refreshed, equalTo(2))

    newItem2.value = "v2".asParsed()
    assertThat(listVar.listItems.map { it.value }, equalTo(listOf("v1".asParsed<Any>(), "v2".asParsed<Any>())))
    assertThat(refreshed, equalTo(3))
  }

  fun testAddingMapVariables() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    val variables = psAppModule.variables
    var refreshed = 0
    variables.onChange(testRootDisposable) { refreshed++ }
    val mapVar = variables.addNewMapVariable("newMap1")
    assertThat(refreshed, equalTo(1))
    mapVar.addMapValue("a")?.value = 1.asParsed()
    mapVar.addMapValue("b")?.value = 2.asParsed()
    assertThat(mapVar.mapEntries.entries.map { it.key to it.value.value },
               equalTo(listOf("a" to 1.asParsed<Any>(), "b" to 2.asParsed<Any>())))
  }

  fun testMapVariables() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    val variables = psAppModule.variables
    val mapVariable = variables.findElement("mapVariable")!!

    assertThat(mapVariable.mapEntries.entries.mapValues { it.value.value },
               equalTo(mapOf("a" to "\"double\" quotes".asParsed<Any>(), "b" to "'single' quotes".asParsed<Any>())))

    mapVariable.mapEntries.findElement("b")!!.setName("Z")

    assertThat(mapVariable.mapEntries.entries.mapValues { it.value.value },
               equalTo(mapOf("a" to "\"double\" quotes".asParsed<Any>(), "Z" to "'single' quotes".asParsed<Any>())))
  }

  fun testVariableWellKnownValues() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val rootVariables = psProject.variables
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    val variables = psAppModule.variables
    // Variable's possible values are inferred from its usage in config files.
    val rootVariableKnownValues =
      PsVariable.Descriptors.variableValue.bindContext(rootVariables.getOrCreateVariable("rootBool")).getKnownValues().get()
    val rootVariable2KnownValues =
      PsVariable.Descriptors.variableValue.bindContext(rootVariables.getOrCreateVariable("rootBool2")).getKnownValues().get()
    val rootVariable3KnownValues =
      PsVariable.Descriptors.variableValue.bindContext(rootVariables.getOrCreateVariable("rootBool3")).getKnownValues().get()
    val variableKnownValues =
      PsVariable.Descriptors.variableValue.bindContext(variables.getOrCreateVariable("varBool")).getKnownValues().get()

    assertThat(rootVariableKnownValues.literals, equalTo<List<ValueDescriptor<Any>>>(booleanValues(null).get()))
    assertThat(rootVariable2KnownValues.literals, equalTo<List<ValueDescriptor<Any>>>(booleanValues(null).get()))
    assertThat(rootVariable3KnownValues.literals, equalTo<List<ValueDescriptor<Any>>>(booleanValues(null).get()))
    assertThat(variableKnownValues.literals, equalTo<List<ValueDescriptor<Any>>>(booleanValues(null).get()))
  }

  fun testGetAvailableVariablesForType() {
    val stringWithDotsProperty: ModelPropertyContext<String> = object : ModelPropertyContext<String> {
      override fun parse(value: String): Annotated<ParsedValue<String>> = when {
        value.contains(".") -> ParsedValue.Set.Parsed(value, DslText.Literal).annotated()
        else -> ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText(value)).annotateWithError("invalid")
      }

      override fun format(value: String): String = throw UnsupportedOperationException()

      override fun getKnownValues(): ListenableFuture<KnownValues<String>> =
        throw UnsupportedOperationException()
    }
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    run {
      val variables = psAppModule.variables.getAvailableVariablesFor(stringWithDotsProperty).toSet()
      assertThat(
        variables,
        equalTo(
          setOf(
            ("agpVersionX" to "3.4.x").asParsed().annotated(),
            ("myVariable" to "26.1.0").asParsed().annotated(),
            ("variable1" to "1.3").asParsed().annotated(),
            ("anotherVariable" to "3.0.1").asParsed().annotated(),
            ("rootFloat" to "3.14").asParsed().annotated(),
            ("varRefString" to "1.3").asParsed().annotated(),
            ("localMap.KTSApp" to "com.example.text.KTSApp").asParsed().annotated(),
            ("localMap.LocalApp" to "com.android.localApp").asParsed().annotated(),
            ("versionVal" to "28.0.0").asParsed().annotated(),
            ("dependencyVersion" to "28.0.0").asParsed().annotated()
          )
        )
      )
    }
  }

  fun testGetVariableScopes() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    val scopes = psAppModule.variables.getVariableScopes()
    assertThat(
      scopes.map { it.name },
      equalTo(listOf("testGetVariableScopes (build script)", "testGetVariableScopes (project)", ":app"))
    )
    assertThat(
      scopes.map { it.title },
      equalTo(listOf("Build Script: testGetVariableScopes", "Project: testGetVariableScopes", "Module: app"))
    )
  }

  fun testGetNewVariableName() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    assertThat(psProject.variables.getNewVariableName("someVar"), equalTo("someVar1"))
    assertThat(psProject.variables.getNewVariableName("otherVar"), equalTo("otherVar"))
  }

  fun testGetOrCreateVariable() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    val variables = psAppModule.variables
    val tmp123 = variables.getOrCreateVariable("tmp123")
    tmp123.setName("tmp321")
    tmp123.value = "123".asParsed()
    val secondTmp123 = variables.getVariable("tmp123")
    assertThat(secondTmp123, nullValue())
    val tmp321 = variables.getOrCreateVariable("tmp321")
    assertThat(tmp321.value, equalTo("123".asParsed<Any>()))
    val float271 = variables.getOrCreateVariable("float271")
    float271.value = BigDecimal("2.71").asParsed<Any>()
    assertThat(float271.value, equalTo(BigDecimal("2.71").asParsed<Any>()))
  }

  fun testRefresh() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    val psProject = PsProjectImpl(project)
    val variables = psProject.variables
    val otherVariables = PsVariables(psProject, "other", "other", null)

    assumeThat(otherVariables.entries.keys, equalTo(setOf("someVar", "rootBool", "rootBool2", "rootBool3", "rootFloat", "listProp",
                                                          "mapProp", "boolRoot", "dependencyVersion")))

    val someVar = variables.getVariable("someVar")
    someVar?.setName("tmp321")
    val rootBool2 = variables.getVariable("rootBool2")
    rootBool2?.delete()
    val tmp999 = variables.getOrCreateVariable("tmp999")
    tmp999.value = 999.asParsed()
    assertThat(variables.map { it.name }.toSet(), equalTo(setOf("tmp321", "rootBool", "rootBool3", "tmp999", "rootFloat", "listProp",
                                                                "mapProp", "boolRoot", "dependencyVersion")))

    assumeThat(otherVariables.entries.keys, equalTo(setOf("someVar", "rootBool", "rootBool2", "rootBool3", "rootFloat", "listProp",
                                                          "mapProp", "boolRoot", "dependencyVersion")))
    otherVariables.refresh()
    assertThat(otherVariables.entries.keys, equalTo(setOf("tmp321", "rootBool", "rootBool3", "tmp999", "rootFloat", "listProp",
                                                          "mapProp", "boolRoot", "dependencyVersion")))
  }
}
