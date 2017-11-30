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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidJava8CollectionRemoveIfInspectionTest extends AndroidInspectionTestCase {

  public void testNoWarningsPre24() {
    addManifest(19);
    //noinspection all // Sample code
    doTest("" +
           "package test.pkg;\n" +
           "\n" +
           "import java.util.Iterator;\n" +
           "import java.util.List;\n" +
           "\n" +
           "@SuppressWarnings(\"unused\")\n" +
           "public class X {\n" +
           "\n" +
           "    public void test(List<String> strings) {\n" +
           "        for (Iterator<String> it = strings.iterator(); it.hasNext(); ) {\n" +
           "            String aValue = it.next();\n" +
           "            if(shouldBeRemoved(aValue)) {\n" +
           "                it.remove();\n" +
           "            }\n" +
           "        }\n" +
           "    }\n" +
           "\n" +
           "    private boolean shouldBeRemoved(String aValue) {\n" +
           "        return aValue.length() < 3;\n" +
           "    }\n" +
           "}\n");
  }

  public void testWarningsPost24() {
    addManifest(25);
    //noinspection all // Sample code
    doTest("" +
           "package test.pkg;\n" +
           "\n" +
           "import java.util.Iterator;\n" +
           "import java.util.List;\n" +
           "\n" +
           "@SuppressWarnings(\"unused\")\n" +
           "public class X {\n" +
           "\n" +
           "    public void test(List<String> strings) {\n" +
           "        /*The loop could be replaced with Collection.removeIf*/for (Iterator<String> it = strings.iterator(); it.hasNext(); )/**/ {\n" +
           "            String aValue = it.next();\n" +
           "            if(shouldBeRemoved(aValue)) {\n" +
           "                it.remove();\n" +
           "            }\n" +
           "        }\n" +
           "    }\n" +
           "\n" +
           "    private boolean shouldBeRemoved(String aValue) {\n" +
           "        return aValue.length() < 3;\n" +
           "    }\n" +
           "}\n");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AndroidJava8CollectionRemoveIfInspection() {
      @Nls
      @NotNull
      @Override
      public String getGroupDisplayName() {
        return "Java 8";
      }

      @Nls
      @NotNull
      @Override
      public String getDisplayName() {
        return "Loop can be replaced with Collection.removeIf()";
      }
    };
  }
}
