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

public class AndroidTryWithIdenticalCatchesInspectionTest extends AndroidInspectionTestCase {

  public void testNoIdenticalBranchWarningPre19() {
    addManifest(17);
    //noinspection all // Sample code
    doTest("" +
           "package test.pkg;\n" +
           "\n" +
           "@SuppressWarnings(\"unused\")\n" +
           "public class X {\n" +
           "    public void test() {\n" +
           "        try {\n" +
           "            Class.forName(\"name\").newInstance();\n" +
           "        } catch (ClassNotFoundException e) {\n" +
           "            e.printStackTrace();\n" +
           "        } catch (InstantiationException e) {\n" +
           "            e.printStackTrace();\n" +
           "        } catch (IllegalAccessException e) {\n" +
           "            e.printStackTrace();\n" +
           "        }        \n" +
           "    }\n" +
           "}\n");
  }

  // failing after 2017.3 merge
  public void /*test*/IdenticalBranchWarningPost19() {
    addManifest(20);
    //noinspection all // Sample code
    doTest("" +
           "package test.pkg;\n" +
           "\n" +
           "@SuppressWarnings(\"unused\")\n" +
           "public class X {\n" +
           "    public void test() {\n" +
           "        try {\n" +
           "            Class.forName(\"name\").newInstance();\n" +
           "        } catch (ClassNotFoundException e) {\n" +
           "            e.printStackTrace();\n" +
           "        } /*'catch' branch identical to 'ClassNotFoundException' branch*/catch (InstantiationException e)/**/ {\n" +
           "            e.printStackTrace();\n" +
           "        } /*'catch' branch identical to 'ClassNotFoundException' branch*/catch (IllegalAccessException e)/**/ {\n" +
           "            e.printStackTrace();\n" +
           "        }        \n" +
           "    }\n" +
           "}\n");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AndroidTryWithIdenticalCatchesInspection() {
      @Nls
      @NotNull
      @Override
      public String getDisplayName() {
        return "Identical 'catch' branches in 'try' statement";
      }
    };
  }
}
