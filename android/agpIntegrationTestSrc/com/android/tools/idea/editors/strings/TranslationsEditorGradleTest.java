/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import static com.android.tools.idea.concurrency.AsyncTestUtils.waitForCondition;
import static com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT;

import com.android.ide.common.resources.Locale;
import com.android.testutils.TestUtils;
import com.android.tools.idea.io.TestFileUtils;
import com.android.tools.idea.res.StringResourceWriter;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Rule;
import org.junit.Test;

public final class TranslationsEditorGradleTest {
  @Rule
  public final AndroidGradleProjectRule myRule = new AndroidGradleProjectRule();

  @Test
  public void removeLocale() throws Exception {
    // Arrange.
    myRule.getFixture().setTestDataPath(TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString());
    myRule.load("stringsEditor/MyApplication", AGP_CURRENT, null);

    String projectBasePath = myRule.getProject().getBasePath();
    Path mainRes = Paths.get(projectBasePath, "app/src/main/res");

    @Language("XML")
    String mainContents = "<resources>\n" +
                          "    <string name=\"key_1\">key_1_main_ab</string>\n" +
                          "</resources>\n";

    TestFileUtils.writeFileAndRefreshVfs(mainRes.resolve(Paths.get("values-ab", "strings.xml")), mainContents);

    Path debugRes = Paths.get(projectBasePath, "app/src/debug/res");

    @Language("XML")
    String debugContents = "<resources>\n" +
                           "    <string name=\"key_1\">key_1_debug_ab</string>\n" +
                           "</resources>\n";

    TestFileUtils.writeFileAndRefreshVfs(debugRes.resolve(Paths.get("values-ab", "strings.xml")), debugContents);

    Module module = TestModuleUtil.findAppModule(myRule.getProject());
    StringResourceEditor stringResourceEditor = new StringResourceEditor(StringsVirtualFile.getStringsVirtualFile(module));
    Disposer.register(module, stringResourceEditor);
    StringResourceViewPanel panel = stringResourceEditor.getPanel();

    // Act.
    Application application = ApplicationManager.getApplication();
    Runnable loadResources = () -> Utils.loadResources(panel, Arrays.asList(mainRes, debugRes));

    application.invokeAndWait(loadResources);
    StringResourceWriter.INSTANCE.removeLocale(Locale.create("ab"), AndroidFacet.getInstance(module), this);
    application.invokeAndWait(loadResources);

    // Assert.
    waitForCondition(2, TimeUnit.SECONDS, () -> panel.getTable().getRowCount() == 0);
  }
}
