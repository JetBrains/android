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
package com.android.tools.idea.npw.model;

import static com.android.tools.idea.npw.model.NewProjectModel.PROPERTIES_ANDROID_PACKAGE_KEY;
import static com.android.tools.idea.npw.model.NewProjectModel.PROPERTIES_KOTLIN_SUPPORT_KEY;
import static com.android.tools.idea.npw.model.NewProjectModel.PROPERTIES_NPW_LANGUAGE_KEY;
import static com.android.tools.idea.npw.model.NewProjectModel.toPackagePart;
import static com.android.tools.idea.npw.platform.Language.JAVA;
import static com.android.tools.idea.npw.platform.Language.KOTLIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.npw.platform.Language;
import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

public final class NewProjectModelTest {
  @Test
  public void nameToPackageReturnsSanitizedPackageName() {
    assertEquals("", toPackagePart("#"));
    assertEquals("aswitch", toPackagePart("switch"));
    assertEquals("aswitch", toPackagePart("Switch"));
    assertEquals("myapplication", toPackagePart("#My $AppLICATION"));
    assertEquals("myapplication", toPackagePart("My..\u2603..APPLICATION"));
  }

  @Test
  public void initialLanguage() {
    // We have 3 variables, with values:
    // Already saved Language => {Kotlin, Java, null}
    // Old Kotlin checkBox selected? => {true, false}
    // Is a new User? => {true, false}

    // There are 3x2x2=12 combinations:
    testInitialLanguage(KOTLIN, false, false, KOTLIN);
    testInitialLanguage(KOTLIN, false, true, KOTLIN);
    testInitialLanguage(KOTLIN, true, false, KOTLIN);
    testInitialLanguage(KOTLIN, true, true, KOTLIN);

    testInitialLanguage(JAVA, false, false, JAVA);
    testInitialLanguage(JAVA, false, true, JAVA);
    testInitialLanguage(JAVA, true, false, JAVA);
    testInitialLanguage(JAVA, true, true, JAVA);

    testInitialLanguage(null, false, false, JAVA);
    testInitialLanguage(null, false, true, KOTLIN);
    testInitialLanguage(null, true, false, KOTLIN);
    testInitialLanguage(null, true, true, KOTLIN);
  }

  private static void testInitialLanguage(@Nullable Language savedLang, boolean oldKotlinFlag, boolean newUser,
                                          @NotNull Language expectRes) {
    PropertiesComponent props = new PropertiesComponentMock();
    if (savedLang != null) {
      props.setValue(PROPERTIES_NPW_LANGUAGE_KEY, savedLang.getName());
    }
    props.setValue(PROPERTIES_KOTLIN_SUPPORT_KEY, oldKotlinFlag);
    if (!newUser) {
      props.setValue(PROPERTIES_ANDROID_PACKAGE_KEY, "com.example.java");
    }

    Language actualRes = NewProjectModel.calculateInitialLanguage(props);
    assertEquals(expectRes, actualRes);
    assertEquals(props.getValue(PROPERTIES_NPW_LANGUAGE_KEY), actualRes.getName());
    assertFalse(savedLang == null && props.isValueSet(PROPERTIES_KOTLIN_SUPPORT_KEY));
  }
}
