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
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.tests.gui.theme.ThemeEditorTestUtils;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import org.apache.commons.io.FileUtils;
import org.fest.assertions.Index;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.fest.assertions.Assertions.assertThat;

public class ThemeEditorUtilsTest extends AndroidTestCase {

  private String sdkPlatformPath;
  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  public ThemeEditorUtilsTest() {
    super(false);
  }

  private void compareWithGoldenFile(@NotNull String text, @NotNull String goldenFile) throws IOException {
    final File file = new File(goldenFile);
    String goldenText = String.format(FileUtils.readFileToString(file), sdkPlatformPath).trim();

    // Add line breaks after "<BR/>" tags for results that are easier to read.
    // Golden files are already have these line breaks, so there's no need to process them the same way.
    text = StringUtil.replace(text, "<BR/>", "<BR/>\n");

    assertEquals(String.format("Comparing to golden file %s failed", file.getCanonicalPath()), goldenText, text);
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
    ThemeEditorStyle theme = themeResolver.getTheme("AppTheme");
    assertNotNull(theme);

    Collection<EditedStyleItem> values = ThemeEditorTestUtils.getStyleLocalValues(theme);
    assertEquals(7, values.size());

    for (EditedStyleItem item : values) {
      String doc = ThemeEditorUtils.generateToolTipText(item.getSelectedValue(), myModule, configuration);
      compareWithGoldenFile(doc, myFixture.getTestDataPath() + "/themeEditor/tooltipDocAns/" + item.getName() + ".ans");
    }
  }

  public void testGetDisplayHtml() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = themeResolver.getTheme("AppTheme");
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

  /**
   * Tests copyTheme method for following cases:
   * 1. copyTheme(21, "values-en-night")
   * 2. copyTheme(21, "values-v19")
   */
  public void testCopyThemeVersionOverride() {
    myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values-en-night/styles.xml");
    myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values-v19/styles.xml");

    LocalResourceRepository repository = AppResourceRepository.getAppResources(myModule, true);
    assertNotNull(repository);
    final List<ResourceItem> styleItems = repository.getResourceItem(ResourceType.STYLE, "AppTheme");
    assertNotNull(styleItems);
    assertEquals(2, styleItems.size());

    new WriteCommandAction.Simple(myModule.getProject(), "Copy a theme") {
      @Override
      protected void run() throws Throwable {
        for (ResourceItem styleItem : styleItems) {
          XmlTag styleTag = LocalResourceRepository.getItemTag(getProject(), styleItem);
          assertNotNull(styleTag);
          ThemeEditorUtils.copyTheme(21, styleTag);
        }
      }
    }.execute();
    myFixture.checkResultByFile("res/values-en-night-v21/styles.xml", "themeEditor/styles_1.xml", true);
    myFixture.checkResultByFile("res/values-v21/styles.xml", "themeEditor/styles_1.xml", true);
  }

  public void testResourceResolverVisitor() {
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v14.xml", "res/values-v14/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v19.xml", "res/values-v19/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v21.xml", "res/values-v21/styles.xml");

    final AtomicInteger visitedRepos = new AtomicInteger(0);
    // With only one source set, this should be called just once.
    ThemeEditorUtils.acceptResourceResolverVisitor(myFacet, new ThemeEditorUtils.ResourceFolderVisitor() {
      @Override
      public void visitResourceFolder(@NotNull LocalResourceRepository resources,
                                      String moduleName,
                                      @NotNull String variantName,
                                      boolean isSelected) {
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
    List<EditedStyleItem> attributes = ThemeEditorUtils.resolveAllAttributes(theme, new ThemeResolver(configuration));

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
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-port.xml", "res/values-port/styles.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);
    assertNotNull(configuration.getTarget());
    configuration.setTarget(new CompatibilityRenderTarget(configuration.getTarget(), 20, null));
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    ThemeEditorStyle style = themeResolver.getTheme("AppTheme");
    assertNotNull(style);
    Collection<EditedStyleItem> attributes = ThemeEditorUtils.resolveAllAttributes(style, themeResolver);

    EditedStyleItem myBaseAttribute = findAttribute("myBase", attributes);
    EditedStyleItem myAttribute = findAttribute("myAttribute", attributes);

    assertEquals("V20", myAttribute.getValue());
    assertEquals("V20", myBaseAttribute.getValue());
    // This wil contain v17 and default as other configs
    assertSize(2, myBaseAttribute.getNonSelectedItemResourceValues());

    HashSet<String> values = Sets.newHashSet();
    for (ConfiguredElement<ItemResourceValue> item : myBaseAttribute.getNonSelectedItemResourceValues()) {
      values.add(item.getElement().getValue());
    }
    assertThat(values).containsOnly("V20", "V17");

    configuration.setTarget(new CompatibilityRenderTarget(configuration.getTarget(), 17, null));
    themeResolver = new ThemeResolver(configuration);

    style = themeResolver.getTheme("AppTheme");
    assertNotNull(style);
    attributes = ThemeEditorUtils.resolveAllAttributes(style, themeResolver);

    myBaseAttribute = findAttribute("myBase", attributes);
    myAttribute = findAttribute("myAttribute", attributes);

    assertEquals("V17", myAttribute.getValue());
    assertEquals("V17", myBaseAttribute.getValue());

    configuration.setTarget(new CompatibilityRenderTarget(configuration.getTarget(), 20, null));
    themeResolver = new ThemeResolver(configuration);

    style = themeResolver.getTheme("PortraitOnlyTheme");
    assertNotNull(style);
    attributes = ThemeEditorUtils.resolveAllAttributes(style, themeResolver);
    myAttribute = findAttribute("myAttribute", attributes);
    assertEquals("V20", myAttribute.getValue());
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

    // AppThemeParent is defined in v20 only but its parent is defined in both v20 AND v19
    ThemeEditorStyle style = themeResolver.getTheme("AppThemeParent");
    assertNotNull(style);
    Collection<EditedStyleItem> attributes = ThemeEditorUtils.resolveAllAttributes(style, new ThemeResolver(configuration));

    EditedStyleItem myAttribute = findAttribute("myAttribute", attributes);
    assertNotNull(myAttribute);
  }

  public void testAttributeInheritanceSet() {
    FolderConfiguration defaultFolder = new FolderConfiguration();
    FolderConfiguration v21Folder = FolderConfiguration.getConfigForQualifierString("v21");
    assertNotNull(v21Folder);
    ThemeEditorUtils.AttributeInheritanceSet inheritanceSet = new ThemeEditorUtils.AttributeInheritanceSet();

    assertFalse(inheritanceSet.iterator().hasNext());
    ItemResourceValue value = new ItemResourceValue("android:windowBackground", true, "0", true);
    inheritanceSet.add(ConfiguredElement.create(defaultFolder, value));
    assertEquals(1, Iterables.size(inheritanceSet));

    // This shouldn't add the attribute again
    inheritanceSet.add(ConfiguredElement.create(defaultFolder, value));
    assertEquals(1, Iterables.size(inheritanceSet));

    // Even when the source theme is different, it shouldn't be added
    inheritanceSet.add(ConfiguredElement.create(defaultFolder, value));
    assertEquals(1, Iterables.size(inheritanceSet));

    inheritanceSet.add(ConfiguredElement.create(v21Folder, value));
    assertEquals(2, Iterables.size(inheritanceSet));

    value = new ItemResourceValue("android:colorForeground", true, "0", true);
    inheritanceSet.add(ConfiguredElement.create(defaultFolder, value));
    assertEquals(3, Iterables.size(inheritanceSet));
  }

  public void testSimplifyName() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_2.xml", "res/values/styles.xml");
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);
    ThemeResolver res = new ThemeResolver(configuration);
    assertEquals("X Light", ThemeEditorUtils.simplifyThemeName(res.getTheme("Theme.X.Light.Y")));
    assertEquals("X Dark", ThemeEditorUtils.simplifyThemeName(res.getTheme("Theme.X.Dark.Y")));
    assertEquals("Material Light", ThemeEditorUtils.simplifyThemeName(res.getTheme("Theme.Material.Light")));
    assertEquals("Theme Dark", ThemeEditorUtils.simplifyThemeName(res.getTheme("android:Theme")));
    assertEquals("Theme Light", ThemeEditorUtils.simplifyThemeName(res.getTheme("Theme.Light")));
  }

  public void testGenerateWordEnumeration() {
    assertEquals("", ThemeEditorUtils.generateWordEnumeration(Collections.<String>emptyList()));
    assertEquals("one", ThemeEditorUtils.generateWordEnumeration(Collections.singletonList("one")));
    assertEquals("one and two", ThemeEditorUtils.generateWordEnumeration(Arrays.asList("one", "two")));
    assertEquals("one, two and Three", ThemeEditorUtils.generateWordEnumeration(Arrays.asList("one", "two", "Three")));
  }

  public void testThemeNamesListOrder() {
    myFixture.copyFileToProject("themeEditor/styles_alphabetical.xml", "res/values/styles.xml");
    List<String> themeNames = ThemeEditorUtils.getModuleThemeQualifiedNamesList(myModule);
    assertThat(themeNames).hasSize(4)
      .contains("aTheme", Index.atIndex(0))
      .contains("BTheme", Index.atIndex(1))
      .contains("cTheme", Index.atIndex(2))
      .contains("DTheme", Index.atIndex(3));
  }
}
