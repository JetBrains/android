/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.model

import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.npw.model.NewProjectModel.Companion.PROPERTIES_ANDROID_PACKAGE_KEY
import com.android.tools.idea.npw.model.NewProjectModel.Companion.PROPERTIES_KOTLIN_SUPPORT_KEY
import com.android.tools.idea.npw.model.NewProjectModel.Companion.PROPERTIES_NPW_ASKED_LANGUAGE_KEY
import com.android.tools.idea.npw.model.NewProjectModel.Companion.PROPERTIES_NPW_LANGUAGE_KEY
import com.android.tools.idea.npw.model.NewProjectModel.Companion.nameToJavaPackage
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.Language.Java
import com.android.tools.idea.wizard.template.Language.Kotlin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewProjectModelTest {
  @Test
  fun nameToPackageReturnsSanitizedPackageName() {
    assertEquals("", nameToJavaPackage("#"))
    assertEquals("aswitch", nameToJavaPackage("switch"))
    assertEquals("aswitch", nameToJavaPackage("Switch"))
    assertEquals("myapplication", nameToJavaPackage("#My \$AppLICATION"))
    assertEquals("myapplication", nameToJavaPackage("My..\u2603..APPLICATION"))
  }

  @Test
  fun nameToPackageIgnoresParentModule() {
    assertEquals("lib", nameToJavaPackage(":lib"))
    assertEquals("lib", nameToJavaPackage("libs:lib"))
    assertEquals("lib", nameToJavaPackage(":libs:lib"))
    assertEquals("lib", nameToJavaPackage("::libs:lib"))
  }

  @Test
  fun initialLanguage() {
    // We have 3 variables, with values:
    // Already saved Language => {Kotlin, Java, null}
    // Old Kotlin checkBox selected? => {true, false}
    // Is a new User? => {true, false}

    // There are 3x2x2=12 combinations:
    testInitialLanguage(Kotlin, false, false, Kotlin)
    testInitialLanguage(Kotlin, false, true, Kotlin)
    testInitialLanguage(Kotlin, true, false, Kotlin)
    testInitialLanguage(Kotlin, true, true, Kotlin)

    testInitialLanguage(Java, false, false, Java)
    testInitialLanguage(Java, false, true, Java)
    testInitialLanguage(Java, true, false, Java)
    testInitialLanguage(Java, true, true, Java)

    testInitialLanguage(null, false, false, Java)
    testInitialLanguage(null, false, true, Kotlin)
    testInitialLanguage(null, true, false, Kotlin)
    testInitialLanguage(null, true, true, Kotlin)
  }

  @Test
  fun initialLanguageAndAskedUser() {
    val props = PropertiesComponentMock()

    props.setValue(PROPERTIES_NPW_LANGUAGE_KEY, Kotlin.toString())
    var language = NewProjectModel.calculateInitialLanguage(props)
    assertTrue(language.isPresent)
    assertEquals(Kotlin, language.get())

    props.setValue(PROPERTIES_NPW_LANGUAGE_KEY, Java.toString())
    language = NewProjectModel.calculateInitialLanguage(props)
    assertFalse(language.isPresent)
  }

  private fun testInitialLanguage(savedLang: Language?, oldKotlinFlag: Boolean, newUser: Boolean, expectRes: Language) {
    val props = PropertiesComponentMock()
    // After 3.5, we may get "empty" value, if the saved value is not "Kotlin" and we didn't asked the user.
    // This test is only checking saved values, so we set "asked user" flag to true.
    props.setValue(PROPERTIES_NPW_ASKED_LANGUAGE_KEY, true)

    if (savedLang != null) {
      props.setValue(PROPERTIES_NPW_LANGUAGE_KEY, savedLang.toString())
    }
    props.setValue(PROPERTIES_KOTLIN_SUPPORT_KEY, oldKotlinFlag)
    if (!newUser) {
      props.setValue(PROPERTIES_ANDROID_PACKAGE_KEY, "com.example.java")
    }

    // We never expect "Option.empty", if we get one, .get() will throw an exception and the test will fail.
    val actualRes = NewProjectModel.calculateInitialLanguage(props)
    assertTrue(actualRes.isPresent)
    assertEquals(expectRes, actualRes.get())
    assertEquals(props.getValue(PROPERTIES_NPW_LANGUAGE_KEY), actualRes.get().toString())
    assertFalse(savedLang == null && props.isValueSet(PROPERTIES_KOTLIN_SUPPORT_KEY))
  }
}
