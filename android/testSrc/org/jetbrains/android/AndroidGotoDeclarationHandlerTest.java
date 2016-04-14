/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.dom.AndroidValueResourcesTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Note: There are some additional tests for goto declaration in {@link AndroidValueResourcesTest} such
 * as {@link AndroidValueResourcesTest#testDeclareStyleableNameNavigation1}, as well as in
 * {@link AndroidResourcesLineMarkerTest}
 * <p>
 * TODO: Test the manifest-oriented logic in {@link AndroidGotoDeclarationHandler}
 * TODO: Test jumping from a layout to an XML declare styleable attribute!
 */
public class AndroidGotoDeclarationHandlerTest extends AndroidTestCase {
  private static final String BASE_PATH = "/gotoDeclaration/";

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, "lib", true);
  }

  public void testGotoString() throws Exception {
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "GotoString.java", "src/p1/p2/GotoString.java");
    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file))
    );
  }

  public void testGotoImportedString() throws Exception {
    Module libModule = myAdditionalModules.get(0);
    // Remove the current lib manifest (has wrong package name) and copy a manifest with proper package into the lib module.
    deleteManifest(libModule);
    myFixture.copyFileToProject("util/lib/AndroidManifest.xml", "additionalModules/lib/AndroidManifest.xml");
    // Copy an empty R class with the proper package into the lib module.
    myFixture.copyFileToProject("util/lib/R.java", "additionalModules/lib/gen/p1/p2/lib/R.java");
    // Add some lib string resources.
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "additionalModules/lib/res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "additionalModules/lib/res/layout/layout.xml");
    // Remove the manifest from the main module.
    deleteManifest(myModule);

    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "ImportedGotoString.java", "src/p1/p2/ImportedGotoString.java");
    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file))
    );
  }

  public void testGotoDynamicId() throws Exception {
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "ids.xml", "res/values/ids.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "GotoId.java", "src/p1/p2/GotoId.java");
    assertEquals("layout/layout.xml:4:\n" +
                 "  <EditText android:id=\"@+id/anchor\"/>\n" +
                 "                       ~|~~~~~~~~~~~~ \n" +
                 "values/ids.xml:2:\n" +
                 "  <item name=\"anchor\" type=\"id\"/>\n" +
                 "             ~|~~~~~~~           \n",
                 describeElements(getDeclarationsFrom(file))
    );
  }

  public void testLanguageFolders() throws Exception {
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values-no/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values-en/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values-en-rUS/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "GotoString.java", "src/p1/p2/GotoString.java");
    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n" +
                 "values-en/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n" +
                 "values-en-rUS/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n" +
                 "values-no/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file))
    );
  }

  public void testGotoStringFromXml() throws Exception {
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout2.xml");
    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file))
    );
  }

  public void testGotStyleableAttr() throws Exception {
    myFixture.copyFileToProject(BASE_PATH + "attrs.xml", "res/values/attrs.xml");
    myFixture.copyFileToProject(BASE_PATH + "R_MyView.java", "src/p1/p2/R.java");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "MyView2.java", "src/p1/p2/MyView.java");
    assertEquals("values/attrs.xml:4:\n" +
                 "  <attr name=\"answer\">\n" +
                 "             ~|~~~~~~~\n",
                 describeElements(getDeclarationsFrom(file))
    );
  }

  public void testGotoActivityFromToolsContext() throws Exception {
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "MyActivity.java", "src/p1/p2/MyActivity.java");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "tools_layout1.xml", "res/layout/layout2.xml");
    assertEquals("MyActivity.java:6:\n" +
                 "  public class MyActivity extends Activity {\n" +
                 "  ~~~~~~~~~~~~~|~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n",
                 describeElements(getDeclarationsFrom(file))
    );
  }

  public void testGotoResourceFromToolsAttribute() throws Exception {
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "tools_layout2.xml", "res/layout/layout2.xml");
    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file))
    );
  }

  @Nullable
  private PsiElement[] getDeclarationsFrom(VirtualFile file) {
    myFixture.configureFromExistingVirtualFile(file);

    // AndroidGotoDeclarationHandler only handles .java files. We also want to check .xml files, so
    // we use GotoDeclarationAction instead of creating AndroidGotoDeclarationHandler and invoking getGotoDeclarationTargets
    // on it directly.
    return GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
  }
}