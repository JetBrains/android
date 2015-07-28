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
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredItemResourceValue;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.tests.gui.theme.ThemeEditorTestUtils;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import org.apache.commons.io.FileUtils;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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

  private void compareWithAns(String doc, String ansPath) throws IOException {
    assertNotNull(doc);

    String ansDoc = String.format(FileUtils.readFileToString(new File(ansPath)), sdkPlatformPath);

    doc = StringUtil.replace(doc, "\n", "");
    ansDoc = StringUtil.replace(ansDoc, "\n", "");

    assertEquals(ansDoc, doc);
  }

  public void testGenerateToolTipText() throws IOException {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    sdkPlatformPath = getTestSdkPath();
    if (!sdkPlatformPath.endsWith("/")) sdkPlatformPath += "/";
    IAndroidTarget androidTarget = configuration.getTarget();
    assertNotNull(androidTarget);
    sdkPlatformPath += "platforms/android-" + androidTarget.getVersion().getApiString();
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = themeResolver.getTheme("@style/AppTheme");
    assertNotNull(theme);

    Collection<EditedStyleItem> values = ThemeEditorTestUtils.getStyleLocalValues(theme);
    assertEquals(7, values.size());

    for (EditedStyleItem item : values) {
      String doc = ThemeEditorUtils.generateToolTipText(item.getSelectedValue(), myModule, configuration);
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

    Collection<EditedStyleItem> values = ThemeEditorTestUtils.getStyleLocalValues(theme);

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
      public void visitResourceFolder(@NotNull LocalResourceRepository resources, @NotNull String variantName, boolean isSelected) {
        assertEquals("main", variantName);
        visitedRepos.incrementAndGet();
      }
    });
    assertEquals(1, visitedRepos.get());
    // TODO: Test variants
  }

  public void testResolveAllAttributes() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_resolve_all.xml", "res/values/styles.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    ThemeEditorStyle theme = ResolutionUtils.getStyle(configuration, "AppTheme", null);
    assertNotNull(theme);
    List<EditedStyleItem> attributes = ThemeEditorUtils.resolveAllAttributes(theme);

    HashMap<String, EditedStyleItem> items = Maps.newHashMapWithExpectedSize(attributes.size());
    for (EditedStyleItem item : attributes) {
      assertNull(items.put(item.getQualifiedName(), item));
    }

    assertTrue(items.containsKey("android:colorBackground"));
    assertTrue(items.containsKey("android:colorPrimary"));
    // Action bar should be there twice, one defined by the framework one defined by us
    assertTrue(items.containsKey("android:windowActionBar"));
    assertTrue(items.containsKey("windowActionBar"));
    assertTrue(items.containsKey("myAttribute"));
    assertFalse(items.containsKey("android:myBoolean"));
  }

  @NotNull
  private static EditedStyleItem findAttribute(@NotNull final String name, @NotNull Collection<EditedStyleItem> attributes) {
    EditedStyleItem item = Iterables.find(attributes, new Predicate<EditedStyleItem>() {
      @Override
      public boolean apply(@javax.annotation.Nullable EditedStyleItem input) {
        assert input != null;
        return name.equals(input.getQualifiedName());
      }
    });
    assertNotNull(item);

    return item;
  }

  public void testResolveAllMultipleParents() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/attributeResolution/styles_base.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v17.xml", "res/values-v17/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v20.xml", "res/values-v20/styles.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);
    assertNotNull(configuration.getTarget());
    configuration.setTarget(new CompatibilityRenderTarget(configuration.getTarget(), 20, null));
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    ThemeEditorStyle style = themeResolver.getTheme("@style/AppTheme");
    assertNotNull(style);
    Collection<EditedStyleItem> attributes = ThemeEditorUtils.resolveAllAttributes(style);

    EditedStyleItem myBaseAttribute = findAttribute("myBase", attributes);
    EditedStyleItem myAttribute = findAttribute("myAttribute", attributes);

    assertEquals("V20", myAttribute.getValue());
    assertEquals("V20", myBaseAttribute.getValue());
    // This wil contain v19 and default as other configs
    assertSize(2, myBaseAttribute.getNonSelectedItemResourceValues());
    ConfiguredItemResourceValue value = Iterables.getFirst(myBaseAttribute.getNonSelectedItemResourceValues(), null);
    assertEquals("V17", value.getItemResourceValue().getValue());

    configuration.setTarget(new CompatibilityRenderTarget(configuration.getTarget(), 17, null));
    themeResolver = new ThemeResolver(configuration);

    style = themeResolver.getTheme("@style/AppTheme");
    assertNotNull(style);
    attributes = ThemeEditorUtils.resolveAllAttributes(style);

    myBaseAttribute = findAttribute("myBase", attributes);
    myAttribute = findAttribute("myAttribute", attributes);

    assertEquals("V17", myAttribute.getValue());
    assertEquals("V17", myBaseAttribute.getValue());
  }

  /**
   * Test themes in only one folder but parents in multiple folders
   */
  public void testResolveAllOnlyOneFolder() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/attributeResolution/styles_base.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v17.xml", "res/values-v17/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v19.xml", "res/values-v19/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v20.xml", "res/values-v20/styles.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);
    assertNotNull(configuration.getTarget());
    configuration.setTarget(new CompatibilityRenderTarget(configuration.getTarget(), 20, null));
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    // AppThemeParent is defined in v20 only but it's parent it's defoned in both v20 AND v19
    ThemeEditorStyle style = themeResolver.getTheme("@style/AppThemeParent");
    assertNotNull(style);
    Collection<EditedStyleItem> attributes = ThemeEditorUtils.resolveAllAttributes(style);

    EditedStyleItem myAttribute = findAttribute("myAttribute", attributes);
    System.out.println(myAttribute);
  }

  public void testAttributeInheritanceSet() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_resolve_all.xml", "res/values/styles.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    ThemeEditorStyle theme = ResolutionUtils.getStyle(configuration, "@android:style/Theme", null);
    ThemeEditorStyle appTheme = ResolutionUtils.getStyle(configuration, "AppTheme", null);
    assertNotNull(theme);
    assertNotNull(appTheme);

    FolderConfiguration defaultFolder = new FolderConfiguration();
    FolderConfiguration v21Folder = FolderConfiguration.getConfigForQualifierString("v21");
    assertNotNull(v21Folder);
    ThemeEditorUtils.AttributeInheritanceSet inheritanceSet = new ThemeEditorUtils.AttributeInheritanceSet();

    assertFalse(inheritanceSet.iterator().hasNext());
    ItemResourceValue value = new ItemResourceValue("android:windowBackground", true, "0", true);
    inheritanceSet.add(new ConfiguredItemResourceValue(defaultFolder, value, theme));
    assertEquals(1, Iterables.size(inheritanceSet));

    // This shouldn't add the attribute again
    inheritanceSet.add(new ConfiguredItemResourceValue(defaultFolder, value, theme));
    assertEquals(1, Iterables.size(inheritanceSet));

    // Even when the source theme is different, it shouldn't be added
    inheritanceSet.add(new ConfiguredItemResourceValue(defaultFolder, value, appTheme));
    assertEquals(1, Iterables.size(inheritanceSet));

    inheritanceSet.add(new ConfiguredItemResourceValue(v21Folder, value, appTheme));
    assertEquals(2, Iterables.size(inheritanceSet));

    value = new ItemResourceValue("android:colorForeground", true, "0", true);
    inheritanceSet.add(new ConfiguredItemResourceValue(defaultFolder, value, theme));
    assertEquals(3, Iterables.size(inheritanceSet));
  }
}
