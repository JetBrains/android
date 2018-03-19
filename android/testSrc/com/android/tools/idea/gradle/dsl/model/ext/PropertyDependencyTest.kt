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
package com.android.tools.idea.gradle.dsl.model.ext

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr
import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType.PLAIN_TEXT
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

/**
 * The aim of these tests is to ensure the state changes for element's dependencies and dependents are correct.
 * We cover 5 different cases relating to properties and variables:
 * <ul>
 *   <li>Parsing</li>
 *   <li>Adding</li>
 *   <li>Changing the value</li>
 *   <li>Renaming</li>
 *   <li>Deleting</li>
 * </ul>
 */
class PropertyDependencyTest : GradleFileModelTestCase() {
  // To test the internal state of the Dsl tree we use reflection to access the information we need in the model.
  fun GradlePropertyModel.element(): GradleDslElement {
    val unresolved = unresolvedModel
    assert(unresolved is GradlePropertyModelImpl)
    val field = GradlePropertyModelImpl::class.java.getDeclaredField("myElement")
    field.isAccessible = true
    return field.get(unresolved) as GradleDslElement
  }

  private fun GradlePropertyModel.fileModelToElement() : GradleDslElement {
    val methodCallElement = element() as GradleDslMethodCall
    assertSize(1, methodCallElement.arguments)
    return methodCallElement.arguments[0]
  }

  private fun setupSingleFile() {
    val text = """
               ext {
                 N0 = 24
                 N1 = 25
                 O  = 27
                 def versions = [N0, N1, O]
                 def files = [key: 'proguard.txt', key2: 'coolguard.txt', passwordFile: "passwordFile.pass"]
                 prop1 = versions
                 prop2 = files
               }

               android {
                 defaultConfig {
                    maxSdkVersion versions[1]
                    minSdkVersion android.defaultConfig.maxSdkVersion
                 }
                 signingConfigs {
                   myConfig {
                     storeFile file(prop2['passwordFile'])
                     storePassword prop2['key3']
                   }
                 }
               }
               """.trimIndent()
    writeToBuildFile(text)
  }

  private fun setupParentAndAppliedFiles() {
    val parentText = """
                     ext {
                       // Some global properties.
                       numbers = [0, 1, 2, 3, 4, 5, 6]
                       letters = ['a', 'b', 'c', 'd', 'e']
                       bool = true
                     }
                     """.trimIndent()
    val defaults = """
                   def signing = [
                     storeF : "store.txt",
                     storeP : ["fed", "grt"],
                     keyF   : "key.file"
                     keyP   : ["cfv", "bnn"]
                   ]

                   ext.vars = [
                     "minSdk" : 17,
                     "maxSdk" : ext.vars.minSdk,
                     "signing": signing
                   ]
                   """.trimIndent()
    val childText = """
                    apply from: "../defaults.gradle"

                    // Local overrides
                    ext {
                      varInt = numbers[1]
                      varBool = bool
                      varRefString = "${'$'}{numbers[3]}"
                      varProGuardFiles = [test: 'proguard-rules.txt', debug: 'proguard-rules2.txt']
                    }

                    android {
                       compileSdkVersion varInt
                       buildToolsVersion varRefString

                       signingConfigs {
                         myConfig {
                           storeFile file(vars['signing'].storeF)
                           storePassword vars["signing"].storeP
                           keyPassword "${'$'}{vars["signing"].keyP}${'$'}{letters[3]}"
                         }
                       }

                       defaultConfig {
                         applicationId appId
                         testApplicationId testId
                         maxSdkVersion vars.maxSdk
                         minSdkVersion vars.minSdk
                       }
                    }
                    """.trimIndent()
    writeToBuildFile(parentText)
    writeToSubModuleBuildFile(childText)
    writeToNewProjectFile("defaults.gradle", defaults)
    writeToSettingsFile("include ':${SUB_MODULE_NAME}'")
  }

  /**
   * Note: [numResolvedDependencies] includes the dependencies of the children as well, whereas
   * [numTotalDependencies] and [numUnresolvedDependencies] do not.
   */
  private fun assertDependencyNumbers(
    element: GradleDslElement,
    numResolvedDependencies: Int,
    numTotalDependencies : Int,
    numUnresolvedDependencies: Int,
    numDependents: Int
  ) {
    assertSize(numResolvedDependencies, element.resolvedVariables)
    assertSize(numTotalDependencies, element.dependencies)
    assertSize(numUnresolvedDependencies, element.dependencies.filter { e -> !e.isResolved })
    assertSize(numDependents, element.dependents)
  }

  private fun assertDependencyNumbers(
    model: GradlePropertyModel,
    numResolvedDependencies: Int,
    numTotalDependencies : Int,
    numUnresolvedDependencies: Int,
    numDependents: Int
  ) {
    assertDependencyNumbers(model.element(), numResolvedDependencies, numTotalDependencies, numUnresolvedDependencies, numDependents)
  }

  private fun assertDependencyBetween(origin: GradlePropertyModel, dependency: GradlePropertyModel, dependencyName: String) {
    assertDependencyBetween(origin.element(), dependency.element(), dependencyName)
  }

  private fun assertDependencyBetween(origin: GradleDslElement, dependency: GradleDslElement, dependencyName: String) {
    val injections = origin.dependencies.filter { e -> e.originElement == origin && e.toBeInjected == dependency && e.name == dependencyName }
    assertSize(1, injections)
    assertNotNull(injections[0])

    assertThat(dependency.dependents, hasItem(injections[0]))
  }

  private fun assertUnresolvedDependency(origin: GradlePropertyModel, dependencyName: String) {
    val injections = origin.element().dependencies.filter { e -> e.originElement == origin.element() && e.toBeInjected == null && !e.isResolved && e.name == dependencyName }
    assertSize(1, injections)
  }

  @Test
  fun testParsingDependenciesSingleFile() {
    setupSingleFile()
    val buildModel = gradleBuildModel

    val ext = buildModel.ext()
    val firstModel = ext.findProperty("N0")
    val secondModel = ext.findProperty("N1")
    val thirdModel = ext.findProperty("O")

    val fourthModel = ext.findProperty("versions")
    val fifthModel = ext.findProperty("files")
    val sixthModel = ext.findProperty("prop1")
    val seventhModel = ext.findProperty("prop2")

    assertDependencyNumbers(firstModel, 0, 0, 0, 1)
    assertDependencyNumbers(secondModel, 0, 0,  0, 1)
    assertDependencyNumbers(thirdModel, 0, 0, 0, 1)
    // Note: Unresolved dependencies are only counted at the item level.
    assertDependencyNumbers(fourthModel, 3, 0, 0, 1)

    assertDependencyNumbers(fourthModel.toList()!![0], 1, 1, 0, 0)
    assertDependencyBetween(fourthModel.toList()!![0], firstModel, "N0")
    assertDependencyNumbers(fourthModel.toList()!![1], 1, 1, 0, 1)
    assertDependencyBetween(fourthModel.toList()!![1], secondModel, "N1")
    assertDependencyNumbers(fourthModel.toList()!![2], 1, 1, 0, 0)
    assertDependencyBetween(fourthModel.toList()!![2], thirdModel, "O")

    assertDependencyNumbers(fifthModel, 0, 0, 0, 1)
    assertDependencyNumbers(fifthModel.toMap()!!["key"]!!, 0, 0, 0, 0)
    assertDependencyNumbers(fifthModel.toMap()!!["key2"]!!, 0, 0, 0, 0)
    assertDependencyNumbers(fifthModel.toMap()!!["passwordFile"]!!, 0, 0, 0, 1)

    assertDependencyNumbers(sixthModel, 1, 1, 0, 0)
    assertDependencyBetween(sixthModel, fourthModel, "versions")
    assertDependencyNumbers(seventhModel, 1, 1, 0, 0)
    assertDependencyBetween(seventhModel, fifthModel, "files")

    // Now we check the default config.
    val config = buildModel.android()!!.defaultConfig()
    val maxSdkModel = config.maxSdkVersion()
    val minSdkModel = config.minSdkVersion()

    assertDependencyNumbers(maxSdkModel, 1, 1, 0, 1)
    assertDependencyBetween(maxSdkModel, fourthModel.toList()!![1], "versions[1]")
    assertDependencyNumbers(minSdkModel, 1, 1, 0, 0)
    assertDependencyBetween(minSdkModel, maxSdkModel, "android.defaultConfig.maxSdkVersion")

    // Now the signing config
    val signingConfig = buildModel.android()!!.signingConfigs()[0]
    val storeFileModel = signingConfig.storeFile()
    val storePasswordModel = signingConfig.storePassword()

    assertDependencyNumbers(storeFileModel.fileModelToElement(), 1, 1, 0, 0)
    assertDependencyBetween(storeFileModel.fileModelToElement(), fifthModel.toMap()!!["passwordFile"]!!.element(), "prop2['passwordFile']")
    assertDependencyNumbers(storePasswordModel, 0, 1, 1, 0)
    assertUnresolvedDependency(storePasswordModel, "prop2['key3']")
  }

  @Test
  fun testParsingDependenciesMultiFile() {
    setupParentAndAppliedFiles()

    val projectModel = ProjectBuildModel.get(myProject)!!
    val childModel = projectModel.getModuleBuildModel(mySubModule)!!
    val parentModel = projectModel.projectBuildModel
    val appliedModel = childModel.involvedFiles.filter { f -> f.virtualFile.name == "defaults.gradle" }[0] as GradleBuildModel

    // Applied model
    val appliedExt = appliedModel.ext()
    val varsModel = appliedExt.findProperty("vars")
    val appliedMinSdkModel = varsModel.toMap()!!["minSdk"]!!
    val appliedMaxDskModel = varsModel.toMap()!!["maxSdk"]!!
    val appliedSigningModel = varsModel.toMap()!!["signing"]!!
    val appliedSigningVarModel = appliedSigningModel.dependencies[0]!!
    val storeFModel = appliedSigningVarModel.toMap()!!["storeF"]!!
    val storePModel = appliedSigningVarModel.toMap()!!["storeP"]!!
    val keyFModel   = appliedSigningVarModel.toMap()!!["keyF"]!!
    val keyPModel   = appliedSigningVarModel.toMap()!!["keyP"]!!
    assertDependencyNumbers(varsModel, 2, 0, 0, 0)
    assertDependencyNumbers(appliedMinSdkModel, 0, 0, 0, 2)
    assertDependencyNumbers(appliedMaxDskModel, 1, 1, 0, 1)
    assertDependencyBetween(appliedMaxDskModel, appliedMinSdkModel, "ext.vars.minSdk")
    assertDependencyNumbers(appliedSigningModel, 1, 1, 0, 0)
    assertDependencyBetween(appliedSigningModel, appliedSigningVarModel, "signing")
    assertDependencyNumbers(appliedSigningVarModel, 0, 0, 0, 1)
    assertDependencyNumbers(storeFModel, 0, 0, 0, 1)
    assertDependencyNumbers(storePModel, 0, 0, 0, 1)
    assertDependencyNumbers(keyFModel, 0, 0, 0, 0)
    assertDependencyNumbers(keyPModel, 0, 0, 0, 1)

    // Parent build file
    val parentExtModel = parentModel.ext()
    val numbersModel = parentExtModel.findProperty("numbers")
    val lettersModel = parentExtModel.findProperty("letters")
    val booleanModel = parentExtModel.findProperty("bool")
    assertDependencyNumbers(numbersModel, 0, 0, 0, 0)
    assertDependencyNumbers(lettersModel, 0, 0, 0, 0)
    assertDependencyNumbers(booleanModel, 0, 0, 0, 1)

    // Child build file
    val extModel = childModel.ext()
    val varIntModel = extModel.findProperty("varInt")
    val varBool = extModel.findProperty("varBool")
    val varRefString = extModel.findProperty("varRefString")
    val varProGuardFiles = extModel.findProperty("varProGuardFiles")
    assertDependencyNumbers(varIntModel, 1, 1, 0, 1)
    assertDependencyBetween(varIntModel, numbersModel.toList()!![1], "numbers[1]")
    assertDependencyNumbers(varBool, 1, 1, 0, 0)
    assertDependencyBetween(varBool, booleanModel, "bool")
    assertDependencyNumbers(varRefString, 1, 1, 0, 1)
    assertDependencyBetween(varRefString, numbersModel.toList()!![3], "numbers[3]")
    assertDependencyNumbers(varProGuardFiles, 0, 0, 0, 0)

    val android = childModel.android()!!
    val compileSdkModel = android.compileSdkVersion()
    val buildToolsModel = android.buildToolsVersion()

    assertDependencyNumbers(compileSdkModel, 1, 1, 0, 0)
    assertDependencyBetween(compileSdkModel, varIntModel, "varInt")
    assertDependencyNumbers(buildToolsModel, 1, 1, 0, 0)
    assertDependencyBetween(buildToolsModel, varRefString, "varRefString")

    val signingConfig = android.signingConfigs()[0]!!
    assertDependencyNumbers(signingConfig.storeFile().fileModelToElement(), 1, 1, 0, 0)
    assertDependencyBetween(signingConfig.storeFile().fileModelToElement(), storeFModel.element(), "vars['signing'].storeF")
    assertDependencyNumbers(signingConfig.storePassword(), 1, 1, 0, 0)
    assertDependencyBetween(signingConfig.storePassword(), storePModel, "vars[\"signing\"].storeP")
    assertDependencyNumbers(signingConfig.keyPassword(), 2, 2, 0, 0)
    assertDependencyBetween(signingConfig.keyPassword(), keyPModel, "vars[\"signing\"].keyP")
    assertDependencyBetween(signingConfig.keyPassword(), lettersModel.toList()!![3], "letters[3]")

    val defaultConfig = android.defaultConfig()
    assertDependencyNumbers(defaultConfig.applicationId(), 0, 1, 1, 0)
    assertDependencyNumbers(defaultConfig.testApplicationId(), 0, 1, 1, 0)
    assertDependencyNumbers(defaultConfig.maxSdkVersion(), 1, 1, 0, 0)
    assertDependencyBetween(defaultConfig.maxSdkVersion(), appliedMaxDskModel, "vars.maxSdk")
    assertDependencyNumbers(defaultConfig.minSdkVersion(), 1, 1, 0, 0)
    assertDependencyBetween(defaultConfig.minSdkVersion(), appliedMinSdkModel, "vars.minSdk")
  }

  @Test
  fun testAddPropertySingleFile() {
    setupSingleFile()

    val buildModel = gradleBuildModel
    val versionsModel = buildModel.ext().findProperty("versions")
    val filesModel = buildModel.ext().findProperty("files")
    // Add a property that depends on an existing one
    val newModel = buildModel.ext().findProperty("newProp")
    newModel.setValue(ReferenceTo("versions[2]"))
    assertDependencyNumbers(newModel, 1, 1, 0, 0)
    assertDependencyBetween(newModel, versionsModel.toList()!![2], "versions[2]")
    val otherNewModel = buildModel.ext().findProperty("otherNewProp")
    otherNewModel.setValue(ReferenceTo("N0"))
    assertDependencyNumbers(otherNewModel, 1, 1, 0, 0)
    assertDependencyBetween(otherNewModel,  buildModel.ext().findProperty("N0"), "N0")
    val keyPass = buildModel.android()!!.signingConfigs()[0]!!.keyPassword()
    keyPass.setValue(PLAIN_TEXT, iStr("${'$'}{prop2['key']}${'$'}{N1}"))
    assertDependencyNumbers(keyPass, 2, 2, 0, 0)
    assertDependencyBetween(keyPass, buildModel.ext().findProperty("N1"), "N1")
    assertDependencyBetween(keyPass, filesModel.toMap()!!["key"]!!, "prop2['key']")

    // Add a property that something depends on and check dependencies are correct.
    val key3 = filesModel.getMapValue("key3")
    key3.setValue("boo")
    assertDependencyNumbers(key3, 0, 0, 0, 1)
    val storePass = buildModel.android()!!.signingConfigs()[0]!!.storePassword()
    assertDependencyNumbers(storePass, 1, 1, 0, 0)
    assertDependencyBetween(storePass, key3, "prop2['key3']")
  }

  @Test
  fun testAddPropertyMultiFile() {
    setupParentAndAppliedFiles()

    val projectModel = ProjectBuildModel.get(myProject)!!
    val childModel = projectModel.getModuleBuildModel(mySubModule)!!
    val parentModel = projectModel.projectBuildModel
    val appliedModel = childModel.involvedFiles.filter { f -> f.virtualFile.name == "defaults.gradle" }[0] as GradleBuildModel

    val maxSdkModel = appliedModel.ext().findProperty("vars").toMap()!!["maxSdk"]!!
    val number4Model = parentModel.ext().findProperty("numbers").toList()!![4]!!
    val varIntModel = childModel.ext().findProperty("varInt")

    // Create a variable in the child model that used applied and parents.
    val newModel = childModel.ext().findProperty("newProp")
    newModel.setValue(iStr("${'$'}{vars.maxSdk} - 12 - ${'$'}{numbers[4]} = ${'$'}{varInt}"))
    assertDependencyNumbers(newModel, 3,3, 0, 0)
    assertDependencyBetween(newModel, maxSdkModel, "vars.maxSdk")
    assertDependencyBetween(newModel, number4Model, "numbers[4]")
    assertDependencyBetween(newModel, varIntModel, "varInt")
  }

  @Test
  fun testChangeValue() {
    setupSingleFile()

    val buildModel = gradleBuildModel

    val changedModel = buildModel.ext().findProperty("versions").toList()!![1]!!
    val oModel = buildModel.ext().findProperty("O")
    val oldN1Model = buildModel.ext().findProperty("N1")
    val maxSdkModel = buildModel.android()!!.defaultConfig().maxSdkVersion()

    changedModel.setValue(iStr("${'$'}{O}"))
    assertDependencyNumbers(changedModel, 1, 1, 0, 1)
    assertDependencyBetween(changedModel, oModel, "O")
    assertDependencyNumbers(oldN1Model, 0, 0, 0, 0)

    assertDependencyNumbers(maxSdkModel, 1, 1, 0, 1)
    assertDependencyBetween(maxSdkModel, changedModel, "versions[1]")
  }

  @Test
  fun testRenameProperty() {
    setupParentAndAppliedFiles()

    val projectModel = ProjectBuildModel.get(myProject)!!
    val childModel = projectModel.getModuleBuildModel(mySubModule)!!
    val parentModel = projectModel.projectBuildModel
    val appliedModel = childModel.involvedFiles.filter { f -> f.virtualFile.name == "defaults.gradle" }[0] as GradleBuildModel

    // Swap min and max sdk names from the applied file.
    val appliedMinSdk = appliedModel.ext().findProperty("vars").toMap()!!["minSdk"]!!
    val appliedMaxSdk = appliedModel.ext().findProperty("vars").toMap()!!["maxSdk"]!!
    val childMaxSdk = childModel.android()!!.defaultConfig().maxSdkVersion()
    val childMinSdk = childModel.android()!!.defaultConfig().minSdkVersion()

    appliedMinSdk.rename("maxSdk")
    assertDependencyNumbers(appliedMaxSdk, 0, 1, 1, 1)
    appliedMaxSdk.rename("minSdk")
    // Note: A cycle is now triggered here, this caused this model and any referencing is to not include the dependency.
    assertDependencyNumbers(appliedMaxSdk, 1, 1, 0, 2)
    assertDependencyNumbers(appliedMinSdk, 0, 0, 0, 1)

    assertDependencyNumbers(childMaxSdk, 1, 1, 0, 0)
    assertDependencyNumbers(childMinSdk, 1, 1, 0, 0)

    // Change the value to break the cycle.
    appliedMaxSdk.setValue(ReferenceTo("vars.maxSdk"))
    assertDependencyNumbers(appliedMaxSdk, 1, 1, 0, 1)
    assertDependencyBetween(childMaxSdk, appliedMinSdk, "vars.maxSdk")
    assertDependencyNumbers(appliedMinSdk, 0, 0, 0, 2)


    assertDependencyNumbers(childMaxSdk, 1, 1, 0, 0)
    assertDependencyBetween(childMaxSdk, appliedMinSdk, "vars.maxSdk")
    assertDependencyNumbers(childMinSdk, 1, 1, 0, 0)
    assertDependencyBetween(childMinSdk, appliedMaxSdk, "vars.minSdk")

    // Rename bool in the parent model to appId. This should break a dependency from varBool
    // and create one on applicationId.
    val boolModel = parentModel.ext().findProperty("bool")
    val varBoolModel = childModel.ext().findProperty("varBool")
    val appIdModel = childModel.android()!!.defaultConfig().applicationId()

    boolModel.rename("appId")
    assertDependencyNumbers(boolModel, 0, 0, 0, 1)
    assertDependencyNumbers(varBoolModel, 0, 1, 1, 0)
    assertDependencyNumbers(appIdModel, 1, 1, 0, 0)
    assertDependencyBetween(appIdModel, boolModel, "appId")
  }

  @Test
  fun testDeleteProperty() {
    setupSingleFile()

    val buildModel = gradleBuildModel

    val deletedN1Model = buildModel.ext().findProperty("versions").toList()!![1]!!
    val n1Model = buildModel.ext().findProperty("N1")
    val maxSdkModel = buildModel.android()!!.defaultConfig().maxSdkVersion()
    val oModel = buildModel.ext().findProperty("versions").toList()!![2]!!
    val deletedN0Model = buildModel.ext().findProperty("N0")
    val n0Model = buildModel.ext().findProperty("versions").toList()!![0]!!

    deletedN1Model.delete()
    assertDependencyNumbers(n1Model, 0, 0, 0, 0)
    assertDependencyNumbers(maxSdkModel, 1, 1, 0, 1)
    assertDependencyBetween(maxSdkModel, oModel, "versions[1]")

    deletedN0Model.delete()
    assertDependencyNumbers(n0Model, 0, 1, 1, 0)
  }
}