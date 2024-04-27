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
package org.jetbrains.android.refactoring;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.VfsTestUtil;
import java.io.File;
import java.util.Arrays;
import org.jetbrains.android.AndroidTestCase;

/**
 * This tests unused resource removal for a JPS project. The gradle scenario is
 * tested in {@link UnusedResourcesGradleTest}.
 */
public class UnusedResourcesTest extends AndroidTestCase {
  private static final String BASE_PATH = "refactoring/unusedResources/";

  public void testUnusedResources() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    VirtualFile layout2 = myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout2.xml");
    myFixture.copyFileToProject(BASE_PATH + "TestCode.java", "src/p1/p2/TestCode.java");

    UnusedResourcesProcessor processor = new UnusedResourcesProcessor(getProject(), null);
    processor.setIncludeIds(true);
    processor.run();

    myFixture.checkResultByFile("res/values/strings.xml", BASE_PATH + "strings_after.xml", true);
    myFixture.checkResultByFile("res/layout/layout.xml", BASE_PATH + "layout_after.xml", true);
    assertFalse(layout2.exists());
  }

  public void testUsagesPresentation() {
    myFixture.copyFileToProject("images/actions/res/drawable-hdpi/ic_action_custom.png",
                                "res/drawable-hdpi/ic_action_custom.png");

    VfsTestUtil.createFile(ProjectUtil.guessProjectDir(myFixture.getProject()), "res/raw/foo.bin", new byte[]{0,1,2});

    UnusedResourcesProcessor processor = new UnusedResourcesProcessor(getProject(), null);

    assertThat(myFixture.getUsageViewTreeTextRepresentation(Arrays.asList(processor.findUsages())))
      .isEqualTo("<root> (2)\n" +
                 " Usages (2)\n" +
                 "  Android resource file (2)\n" +
                 "   app (2)\n" +
                 "    res" + File.separatorChar +  "drawable-hdpi (1)\n" +
                 "     ic_action_custom.png (1)\n" +
                 "      Android resource file drawable-hdpi/ic_action_custom.png\n" +
                 "    res" + File.separatorChar +  "raw (1)\n" +
                 "     foo.bin (1)\n" +
                 "      Android resource file raw/foo.bin\n");
  }
}
