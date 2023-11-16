/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.inspections;

import com.intellij.codeInspection.InspectionProfileEntry;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("StatementWithEmptyBody")
public class AndroidDeprecationInspectionTest extends AndroidInspectionTestCase {

  public void testDeprecationWarningForApi17() {
    addManifest(17);
    doTest("" +
            "package p1.p2;\n" +
            "\n" +
            "public class X extends java.util.Date {\n" +
            "    @Override\n" +
            "    public int /*Overrides deprecated method in 'java.util.Date' as of API 15 (\\\"IceCreamSandwich\\\"; Android 4.0.3)*/getSeconds/**/() {\n" +
            "        return super./*'getSeconds()' is deprecated as of API 15 (\\\"IceCreamSandwich\\\"; Android 4.0.3)*/getSeconds/**/();\n" +
            "    }\n" +
            "}");
  }

  public void testNoDeprecationWarningForApi14() {
    addManifest(14);
    doTest("" +
           "package p1.p2;\n" +
           "\n" +
           "public class X extends java.util.Date {\n" +
           "    @Override\n" +
           "    public int getSeconds() {\n" +
           "        return super.getSeconds();\n" +
           "    }\n" +
           "}");
  }

  public void testConditionalWarnings() {
    // Check that if we have a low minSdkVersion where deprecated APIs are normally not flagged, we *do*
    // flag usages in code sections where there is a higher known API level due to SDK_INT checks
    addManifest(9);
    doTest("" +
           "package p1.p2;\n" +
           "\n" +
           "import android.os.Build;\n" +
           "\n" +
           "import java.util.Date;\n" +
           "\n" +
           "public class X extends java.util.Date {\n" +
           "    @Override\n" +
           "    public int getSeconds() {\n" +
           "        return super.getSeconds();\n" +
           "    }\n" +
           "\n" +
           "    public void testConditionals(Date date) {\n" +
           "        date.getSeconds(); // No warning\n" +
           "\n" +
           "        if (Build.VERSION.SDK_INT >= 14) {\n" +
           "            date.getSeconds(); // No warning\n" +
           "        }\n" +
           "        if (Build.VERSION.SDK_INT >= 15) {\n" +
           "            date./*'getSeconds()' is deprecated as of API 15 (\\\"IceCreamSandwich\\\"; Android 4.0.3)*/getSeconds/**/(); // Should warn\n" +
           "        }\n" +
           "        if (Build.VERSION.SDK_INT >= 16) {\n" +
           "            date./*'getSeconds()' is deprecated as of API 15 (\\\"IceCreamSandwich\\\"; Android 4.0.3)*/getSeconds/**/(); // Should warn\n" +
           "        }\n" +
           "    }\n" +
           "\n" +
           "    public void testEarlyExit(Date date) {\n" +
           "        date.getSeconds(); // No warning\n" +
           "\n" +
           "        if (Build.VERSION.SDK_INT < 16) {\n" +
           "            return;\n" +
           "        }\n" +
           "\n" +
           "        date./*'getSeconds()' is deprecated as of API 15 (\\\"IceCreamSandwich\\\"; Android 4.0.3)*/getSeconds/**/(); // Should warn\n" +
           "    }\n" +
           "}\n");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AndroidDeprecationInspection();
  }
}
