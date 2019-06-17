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
package com.android.tools.idea.gradle.structure.model.helpers

import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.AndroidModuleDescriptors
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.pom.java.LanguageLevel
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat

class ExtractVariableWorkerTest : AndroidGradleTestCase() {

  fun testExtractVariable() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)
    val appModule = project.findModuleByName("app") as PsAndroidModule
    val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.bind(appModule)

    run {
      val worker = ExtractVariableWorker(compileSdkVersion)
      val (newName, newProperty) = worker.changeScope(appModule.variables, "")
      assertThat(newName, equalTo("compileSdkVersion"))
      assertThat(newProperty.getParsedValue(), equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString().asParsed().annotated()))

      worker.commit("compileSdkVersion")
      assertThat(compileSdkVersion.getParsedValue(),
                 equalTo<Annotated<ParsedValue<String>>>(ParsedValue.Set.Parsed(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString(),
                                                                                DslText.Reference("compileSdkVersion"))
                                                           .annotated()))
      assertThat(appModule.variables.getOrCreateVariable("compileSdkVersion").value,
                 equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.asParsed<Any>()))
    }

    run {
      val worker = ExtractVariableWorker(compileSdkVersion)
      val (newName, newProperty) = worker.changeScope(appModule.variables, "")
      assertThat(newName, equalTo("compileSdkVersion1"))   // The second suggested name is the preferredName + "1".
      assertThat(newProperty.getParsedValue(),
                 equalTo<Annotated<ParsedValue<String>>>(ParsedValue.Set.Parsed(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString(),
                                                                                DslText.Reference("compileSdkVersion"))
                                                           .annotated()))

      worker.commit("otherName")
      assertThat(compileSdkVersion.getParsedValue(),
                 equalTo<Annotated<ParsedValue<String>>>(ParsedValue.Set.Parsed(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString(),
                                                                                DslText.Reference("otherName"))
                                                           .annotated()))
      assertThat(appModule.variables.getOrCreateVariable("otherName").value,
                 equalTo<ParsedValue<Any>>(ParsedValue.Set.Parsed(28, DslText.Reference("compileSdkVersion"))))
    }
  }

  fun testExtractVariable_projectLevel() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)
    val appModule = project.findModuleByName("app") as PsAndroidModule
    val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.bind(appModule)

    run {
      val worker = ExtractVariableWorker(compileSdkVersion)
      val (newName, newProperty) = worker.changeScope(appModule.variables, "")
      assertThat(newName, equalTo("compileSdkVersion"))
      assertThat(newProperty.getParsedValue(), equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString().asParsed().annotated()))


      val (newName2, newProperty2) = worker.changeScope(project.variables, "renamedName")
      assertThat(newName2, equalTo("renamedName"))
      assertThat(newProperty2.getParsedValue(), equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString().asParsed().annotated()))

      worker.commit("renamedName")
      assertThat(compileSdkVersion.getParsedValue(),
                 equalTo<Annotated<ParsedValue<String>>>(ParsedValue.Set.Parsed(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString(),
                                                                                DslText.Reference("renamedName"))
                                                           .annotated()))
      assertThat(appModule.variables.getVariable("renamedName"), nullValue())

      assertThat(project.variables.getOrCreateVariable("renamedName").value,
                 equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.asParsed<Any>()))
    }
  }

  fun testExtractEmptyValue() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)
    val appModule = project.findModuleByName("app") as PsAndroidModule
    val targetCompatibility = AndroidModuleDescriptors.targetCompatibility.bind(appModule)

    run {
      val worker = ExtractVariableWorker(targetCompatibility)
      val (newName, newProperty) = worker.changeScope(appModule.variables, "")
      assertThat(newName, equalTo("targetCompatibility"))
      assertThat(newProperty.getParsedValue(), equalTo<Annotated<ParsedValue<LanguageLevel>>>(ParsedValue.NotSet.annotated()))

      assertThat(worker.validate("targetCompatibility"), equalTo("Cannot bind a variable to an empty value."))
    }
  }

  fun testExtractVariableWithBlankName() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)
    val appModule = project.findModuleByName("app") as PsAndroidModule
    val targetCompatibility = AndroidModuleDescriptors.targetCompatibility.bind(appModule)

    run {
      val worker = ExtractVariableWorker(targetCompatibility)
      val (newName, newProperty) = worker.changeScope(appModule.variables, "")
      assertThat(newName, equalTo("targetCompatibility"))
      assertThat(newProperty.getParsedValue(), equalTo<Annotated<ParsedValue<LanguageLevel>>>(ParsedValue.NotSet.annotated()))

      assertThat(worker.validate(" "), equalTo("Variable name is required."))
    }
  }

  fun testExtractAndroidModuleDependencyVersion() {
    loadProject(TestProjectPaths.UNIT_TESTING)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)
    val appModule = project.findModuleByName("app") as PsAndroidModule
    val junit = appModule.dependencies.findLibraryDependencies("junit:junit:4.12").firstOrNull()
    val mockito = appModule.dependencies.findLibraryDependencies("org.mockito:mockito-core:2.7.1").firstOrNull()

    assertThat(junit, notNullValue())
    assertThat(mockito, notNullValue())

    run {
      val junitVersion = junit!!.versionProperty.bind(Unit)
      val worker = ExtractVariableWorker(junitVersion)
      val (newName, newProperty) = worker.changeScope(appModule.variables, "")
      assertThat(newName, equalTo("junitVersion"))
      assertThat(newProperty.getParsedValue(),
                 equalTo<Annotated<ParsedValue<String>>>(ParsedValue.Set.Parsed("4.12", DslText.Literal).annotated()))
      worker.commit("junitVersion")

      assertThat(appModule.variables.getOrCreateVariable("junitVersion").value,
                 equalTo<ParsedValue<Any>>(ParsedValue.Set.Parsed("4.12", DslText.Literal)))
    }

    run {
      val mockitoVersion = mockito!!.versionProperty.bind(Unit)
      val worker = ExtractVariableWorker(mockitoVersion)
      val (newName, newProperty) = worker.changeScope(appModule.variables, "")
      assertThat(newName, equalTo("mockitoCoreVersion"))
      assertThat(newProperty.getParsedValue(),
                 equalTo<Annotated<ParsedValue<String>>>(ParsedValue.Set.Parsed("2.7.1", DslText.Literal).annotated()))
      worker.commit("mockitoCoreVersion")

      assertThat(appModule.variables.getOrCreateVariable("mockitoCoreVersion").value,
                 equalTo<ParsedValue<Any>>(ParsedValue.Set.Parsed("2.7.1", DslText.Literal)))
    }
  }
}
