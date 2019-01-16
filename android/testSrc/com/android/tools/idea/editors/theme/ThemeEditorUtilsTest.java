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

import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.ide.common.rendering.api.ResourceNamespace.ANDROID;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.sdklib.IAndroidTarget.RESOURCES;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.utils.SdkUtils;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PathUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link ThemeEditorUtils} and indirectly {@link com.android.tools.idea.javadoc.AndroidJavaDocRenderer}.
 */
public class ThemeEditorUtilsTest extends AndroidTestCase {
  private static final Pattern OPERATION_PATTERN = Pattern.compile("\\$\\$([A-Z_]+)\\{\\{(.*?)\\}\\}");
  private static final Pattern TEMPORARY_RENDER_FILE_PATTERN = Pattern.compile("'file:\\S+([/\\\\])render\\d*.png'");

  private String mySdkPlatformPath;
  private String mySdkPlatformRes;

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  private void compareWithGoldenFile(@NotNull String text, @NotNull String goldenFile) throws IOException {
    Path file = Paths.get(goldenFile);
    String goldenText = new String(Files.readAllBytes(file), UTF_8);
    goldenText = goldenText.replace("$$ANDROID_SDK_PATH", mySdkPlatformPath);
    goldenText = goldenText.replace("$$ANDROID_SDK_RES", mySdkPlatformRes);
    Matcher matcher = OPERATION_PATTERN.matcher(goldenText);
    StringBuffer processedGoldenText = new StringBuffer();

    while (matcher.find()) {
      String operation = matcher.group(1);
      String value = matcher.group(2);
      if (operation.equals("MAKE_URL")) {
        value = SdkUtils.fileToUrl(new File(value)).toString();
      }
      else if (operation.equals("MAKE_SYSTEM_DEPENDENT_PATH")) {
        value = PathUtil.toSystemDependentName(value);
        // Escape all the backslashes so they don't get treated as back references by the regex engine later.
        if (File.separatorChar == '\\') {
          value = value.replace("\\", "\\\\");
        }
      }
      else {
        // Ignore if we don't know how to handle that - may be accidental pattern match
        continue;
      }
      matcher.appendReplacement(processedGoldenText, value);
    }
    matcher.appendTail(processedGoldenText);

    // Add line breaks after "<BR/>" tags for results that are easier to read.
    // Golden files already have these line breaks, so there's no need to process them the same way.
    text = StringUtil.replace(text, "<BR/>", "<BR/>\n");
    matcher = TEMPORARY_RENDER_FILE_PATTERN.matcher(text);
    text = matcher.replaceAll("'file:/some/directory/render.png'");

    assertEquals(String.format("Comparing to golden file %s failed", file.normalize()), processedGoldenText.toString(), text);
  }

  public void testGenerateToolTipText() throws IOException {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(myFile);

    IAndroidTarget androidTarget = configuration.getTarget();
    assertNotNull(androidTarget);
    mySdkPlatformPath = Paths.get(androidTarget.getLocation()).normalize().toString();
    mySdkPlatformRes = Paths.get(androidTarget.getPath(RESOURCES)).normalize().toString();
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = themeResolver.getTheme(ResourceReference.style(RES_AUTO, "AppTheme"));
    assertNotNull(theme);

    Collection<EditedStyleItem> values = ThemeEditorTestUtils.getStyleLocalValues(theme);
    assertEquals(7, values.size());

    configuration.setTheme("AppTheme");

    for (EditedStyleItem item : values) {
      String doc = ThemeEditorUtils.generateToolTipText(item.getSelectedValue(), myModule, configuration);
      String filename = item.getAttrName();
      //TODO(sprigogin): Remove the following statement and the myDrawable_unpacked_resources.ans file
      //                 when unpacked built-in framework resources are removed.
      if (filename.equals("myDrawable") && !mySdkPlatformRes.endsWith(DOT_JAR)) {
        filename += "_unpacked_resources";
      }
      compareWithGoldenFile(doc, myFixture.getTestDataPath() + "/themeEditor/tooltipDocAns/" + filename + ".ans");
    }
  }

  public void testGetDisplayHtml() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(myFile);

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = themeResolver.getTheme(ResourceReference.style(RES_AUTO, "AppTheme"));
    assertNotNull(theme);

    Collection<EditedStyleItem> values = ThemeEditorTestUtils.getStyleLocalValues(theme);

    assertEquals(7, values.size());
    for (EditedStyleItem item : values) {
      String displayHtml = ThemeEditorUtils.getDisplayHtml(item);
      if ("myDeprecated".equals(item.getAttrName())) {
        assertEquals("<html><body><strike>myDeprecated</strike></body></html>", displayHtml);
      } else {
        assertEquals(item.getAttrName(), displayHtml);
      }
    }
  }

  public void testMinApiLevel() {
    myFixture.copyFileToProject("themeEditor/manifestWithApi.xml", FN_ANDROID_MANIFEST_XML);
    assertEquals(11, ThemeEditorUtils.getMinApiLevel(myModule));
  }

  public void testCopyTheme() {
    myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v19.xml", "res/values-v19/styles.xml");

    LocalResourceRepository repository = ResourceRepositoryManager.getAppResources(myModule);
    assertNotNull(repository);
    List<ResourceItem> resources = repository.getResources(RES_AUTO, ResourceType.STYLE, "AppTheme");
    assertNotNull(resources);
    assertFalse(resources.isEmpty());
    XmlTag sourceXml = LocalResourceRepository.getItemTag(getProject(), resources.get(0));
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

    LocalResourceRepository repository = ResourceRepositoryManager.getAppResources(myModule);
    assertNotNull(repository);
    List<ResourceItem> styleItems = repository.getResources(RES_AUTO, ResourceType.STYLE, "AppTheme");
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

    AtomicInteger visitedRepos = new AtomicInteger(0);
    // With only one source set, this should be called just once.
    ThemeEditorUtils.acceptResourceResolverVisitor(myFacet, (resources, moduleName, variantName, isSelected) -> {
      assertEquals("main", variantName);
      visitedRepos.incrementAndGet();
    });
    assertEquals(1, visitedRepos.get());
    // TODO: Test variants
  }

  @NotNull
  private static EditedStyleItem findAttribute(@NotNull String name, @NotNull Collection<EditedStyleItem> attributes) {
    EditedStyleItem item = attributes.stream().filter(input -> {
      assert input != null;
      return name.equals(input.getQualifiedName());
    }).findFirst().get();
    assertNotNull(item);

    return item;
  }

  public void testSimplifyName() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_2.xml", "res/values/styles.xml");
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(myFile);
    ThemeResolver res = new ThemeResolver(configuration);
    assertEquals("X Light", ThemeEditorUtils.simplifyThemeName(res.getTheme(ResourceReference.style(RES_AUTO, "Theme.X.Light.Y"))));
    assertEquals("X Dark", ThemeEditorUtils.simplifyThemeName(res.getTheme(ResourceReference.style(RES_AUTO, "Theme.X.Dark.Y"))));
    assertEquals("Material Light",
                 ThemeEditorUtils.simplifyThemeName(res.getTheme(ResourceReference.style(RES_AUTO, "Theme.Material.Light"))));
    assertEquals("Theme Dark", ThemeEditorUtils.simplifyThemeName(res.getTheme(ResourceReference.style(ANDROID, "Theme"))));
    assertEquals("Theme Light", ThemeEditorUtils.simplifyThemeName(res.getTheme(ResourceReference.style(RES_AUTO, "Theme.Light"))));
  }

  public void testGenerateWordEnumeration() {
    assertEquals("", ThemeEditorUtils.generateWordEnumeration(Collections.emptyList()));
    assertEquals("one", ThemeEditorUtils.generateWordEnumeration(Collections.singletonList("one")));
    assertEquals("one and two", ThemeEditorUtils.generateWordEnumeration(Arrays.asList("one", "two")));
    assertEquals("one, two and Three", ThemeEditorUtils.generateWordEnumeration(Arrays.asList("one", "two", "Three")));
  }

  public void testThemeNamesListOrder() {
    myFixture.copyFileToProject("themeEditor/styles_alphabetical.xml", "res/values/styles.xml");
    List<String> themeNames = ThemeEditorUtils.getModuleThemeQualifiedNamesList(myModule);
    assertThat(themeNames).containsExactly("aTheme", "BTheme", "cTheme", "DTheme").inOrder();
  }
}
