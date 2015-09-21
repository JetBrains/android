/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.psi.PsiField;
import org.jetbrains.android.AndroidTestCase;

import java.util.List;
import java.util.Set;

public class AndroidResourceUtilTest extends AndroidTestCase {
  public void testCaseSensitivityInChangeColorResource() {
    myFixture.copyFileToProject("util/colors_before.xml", "res/values/colors.xml");
    List<String> dirNames = ImmutableList.of("values");
    assertTrue(AndroidResourceUtil.changeColorResource(myFacet, "myColor", "#000000", "colors.xml", dirNames));
    assertFalse(AndroidResourceUtil.changeColorResource(myFacet, "mycolor", "#FFFFFF", "colors.xml", dirNames));
    myFixture.checkResultByFile("res/values/colors.xml", "util/colors_after.xml", true);
  }

  public void testFindResourceFields() {
    String lightRCode = "package light;" +
                        "class R {" +
                        "static class string {" +
                        "static final int hello = 1;" +
                        "}" +
                        "}";
    myFacet.setLightRClass(myFixture.addClass(lightRCode));

    // Package R file is not found without the "src" prefix on the package.
    myFixture.addClass("package src.p1.p2;" +
                       "class R {" +
                       "static class string {" +
                       "static final int hello = 1;" +
                       "}" +
                       "}");

    PsiField[] fields = AndroidResourceUtil.findResourceFields(myFacet, "string", "hello", false);

    assertEquals(2, fields.length);
    Set<String> dirNames = Sets.newHashSet();
    for (PsiField field : fields) {
      assertEquals("hello", field.getName());
      dirNames.add(field.getContainingFile().getContainingDirectory().getName());
    }
    assertEquals(ImmutableSet.of("light", "p2"), dirNames);
  }
}
