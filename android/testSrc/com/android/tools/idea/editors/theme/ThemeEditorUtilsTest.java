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
package com.android.tools.idea.editors.theme;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class ThemeEditorUtilsTest extends AndroidTestCase {

  private String sdkPlatformPath;
  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  public ThemeEditorUtilsTest() {
    super(false);
  }

  private void compareWithAns(String doc, String ansPath) throws FileNotFoundException {
    assertNotNull(doc);
    Scanner in = new Scanner(new File(ansPath));
    String ansDoc = "";
    while (in.hasNext()) {
      ansDoc += in.nextLine();
    }

    ansDoc = String.format(ansDoc, sdkPlatformPath);

    doc = StringUtil.replace(doc, "\n", "");
    assertEquals(ansDoc, doc);
  }

  public void testGenerateToolTipText() throws FileNotFoundException {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    sdkPlatformPath = getTestSdkPath();
    if (!sdkPlatformPath.endsWith("/")) sdkPlatformPath += "/";
    IAndroidTarget androidTarget = configuration.getTarget();
    assertNotNull(androidTarget);
    sdkPlatformPath += "platforms/android-" + androidTarget.getVersion().getApiLevel();

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = themeResolver.getTheme("@style/AppTheme");
    assertNotNull(theme);
    Collection<EditedStyleItem> values = theme.getValues();
    assertEquals(7, values.size());

    for (EditedStyleItem item : values) {
      String doc = ThemeEditorUtils.generateToolTipText(item.getItemResourceValue(), myModule, configuration);
      compareWithAns(doc, myFixture.getTestDataPath() + "/themeEditor/tooltipDocAns/" + item.getName() + ".ans");
    }
  }

  public void testGetDisplayHtml() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = themeResolver.getTheme("@style/AppTheme");
    assertNotNull(theme);

    Collection<EditedStyleItem> values = theme.getValues();
    assertEquals(7, values.size());
    for (EditedStyleItem item : values) {
      String displayHtml = ThemeEditorUtils.getDisplayHtml(item);
      if ("myDeprecated".equals(item.getName())) {
        assertEquals("<html><body><strike>myDeprecated</strike></body></html>", displayHtml);
      } else {
        assertEquals(item.getName(), displayHtml);
      }
    }
  }

  public void testMinApiLevel() {
    myFixture.copyFileToProject("themeEditor/manifestWithApi.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    assertEquals(11, ThemeEditorUtils.getMinApiLevel(myModule));
  }

  /**
   * Tests that the method getOriginalApiLevel correctly returns the api level
   * in which a particular framework attribute or value was defined
   */
  private void assertOriginalApiLevel(@Nullable String name, int expectedApiLevel) {
    assertEquals(expectedApiLevel, ThemeEditorUtils.getOriginalApiLevel(name, getProject()));
  }

  public void testOriginalApi() {
    assertOriginalApiLevel("android:statusBarColor", 21); // framework attribute
    assertOriginalApiLevel("@android:color/holo_purple", 14); // framework value
    assertOriginalApiLevel("myString", -1); // random string
    assertOriginalApiLevel("statusBarColor", -1); // no prefix attribute
    assertOriginalApiLevel("@color/holo_purple", -1); // no prefix value
    assertOriginalApiLevel(null, -1);
  }

  public void testCopyTheme() {
    myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v19.xml", "res/values-v19/styles.xml");

    LocalResourceRepository repository = AppResourceRepository.getAppResources(myModule, true);
    assertNotNull(repository);
    List<ResourceItem> resources = repository.getResourceItem(ResourceType.STYLE, "AppTheme");
    assertNotNull(resources);
    assertFalse(resources.isEmpty());
    final XmlTag sourceXml = LocalResourceRepository.getItemTag(getProject(), resources.get(0));
    assertNotNull(sourceXml);
    new WriteCommandAction.Simple(myModule.getProject(), "Copy a theme") {
      @Override
      protected void run() throws Throwable {
        ThemeEditorUtils.copyTheme(16, sourceXml);
        ThemeEditorUtils.copyTheme(19, sourceXml);
      }
    }.execute();
    myFixture.checkResultByFile("res/values-v16/styles.xml", "themeEditor/testCopyTheme/styles-v16.xml", true);
    myFixture.checkResultByFile("res/values-v19/styles.xml", "themeEditor/testCopyTheme/styles-v19.xml", true);
  }

  public void testResourceResolverVisitor() {
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v14.xml", "res/values-v14/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v19.xml", "res/values-v19/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v21.xml", "res/values-v21/styles.xml");

    final AtomicInteger visitedRepos = new AtomicInteger(0);
    // With only one source set, this should be called just once.
    ThemeEditorUtils.acceptResourceResolverVisitor(myFacet, new ThemeEditorUtils.ResourceFolderVisitor() {
      @Override
      public void visitResourceFolder(@NotNull LocalResourceRepository resources, boolean isSelected) {
        visitedRepos.incrementAndGet();
      }
    });
    assertEquals(1, visitedRepos.get());
    // TODO: Test variants
  }
}
