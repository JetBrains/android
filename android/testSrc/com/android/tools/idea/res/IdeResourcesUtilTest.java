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
package com.android.tools.idea.res;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.FRAME_LAYOUT;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.TAG_LAYOUT;
import static com.android.SdkConstants.TAG_NAVIGATION;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VIEW_MERGE;
import static com.android.ide.common.rendering.api.ResourceNamespace.ANDROID;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.adtui.imagediff.ImageDiffTestUtil.DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT;
import static com.android.tools.idea.util.FileExtensions.toVirtualFile;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.testutils.ImageDiffUtil;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.MergedManifestModificationListener;
import com.android.tools.idea.model.TestAndroidModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.ColorsIcon;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.Permission;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IdeResourcesUtilTest extends AndroidTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MergedManifestModificationListener.ensureSubscribed(getProject());
  }

  public void testIsFileBasedResourceType() {
    assertTrue(IdeResourcesUtil.isFileBased(ResourceType.ANIMATOR));
    assertTrue(IdeResourcesUtil.isFileBased(ResourceType.LAYOUT));

    assertFalse(IdeResourcesUtil.isFileBased(ResourceType.STRING));
    assertFalse(IdeResourcesUtil.isFileBased(ResourceType.DIMEN));
    assertFalse(IdeResourcesUtil.isFileBased(ResourceType.ID));

    // Both:
    assertTrue(IdeResourcesUtil.isFileBased(ResourceType.DRAWABLE));
    assertTrue(IdeResourcesUtil.isFileBased(ResourceType.COLOR));
  }

  public void testIsValueBasedResourceType() {
    assertTrue(IdeResourcesUtil.isValueBased(ResourceType.STRING));
    assertTrue(IdeResourcesUtil.isValueBased(ResourceType.DIMEN));
    assertTrue(IdeResourcesUtil.isValueBased(ResourceType.ID));

    assertFalse(IdeResourcesUtil.isValueBased(ResourceType.LAYOUT));

    // These can be both:
    assertTrue(IdeResourcesUtil.isValueBased(ResourceType.DRAWABLE));
    assertTrue(IdeResourcesUtil.isValueBased(ResourceType.COLOR));
  }

  public void testStyleToTheme() {
    assertEquals("Foo", IdeResourcesUtil.styleToTheme("Foo"));
    assertEquals("Theme", IdeResourcesUtil.styleToTheme("@android:style/Theme"));
    assertEquals("LocalTheme", IdeResourcesUtil.styleToTheme("@style/LocalTheme"));
    assertEquals("LocalTheme", IdeResourcesUtil.styleToTheme("@foo.bar:style/LocalTheme"));
  }

  public void testGetFolderConfiguration() {
    PsiFile file1 = myFixture.addFileToProject("res/layout-land/foo1.xml", "<LinearLayout/>");
    PsiFile file2 = myFixture.addFileToProject("res/menu-en-rUS/foo2.xml", "<menu/>");

    assertEquals("layout-land", IdeResourcesUtil.getFolderConfiguration(file1).getFolderName(ResourceFolderType.LAYOUT));
    assertEquals("menu-en-rUS", IdeResourcesUtil.getFolderConfiguration(file2).getFolderName(ResourceFolderType.MENU));
    assertEquals("layout-land", IdeResourcesUtil.getFolderConfiguration(file1.getVirtualFile()).getFolderName(ResourceFolderType.LAYOUT));
    assertEquals("menu-en-rUS", IdeResourcesUtil.getFolderConfiguration(file2.getVirtualFile()).getFolderName(ResourceFolderType.MENU));
  }

  public void testParseColor() {
    Color c = IdeResourcesUtil.parseColor("#0f4");
    assert c != null;
    assertEquals(0xff00ff44, c.getRGB());

    c = IdeResourcesUtil.parseColor("#1237");
    assert c != null;
    assertEquals(0x11223377, c.getRGB());

    c = IdeResourcesUtil.parseColor("#123456");
    assert c != null;
    assertEquals(0xff123456, c.getRGB());

    c = IdeResourcesUtil.parseColor("#08123456");
    assert c != null;
    assertEquals(0x08123456, c.getRGB());

    // Test that spaces are correctly trimmed
    c = IdeResourcesUtil.parseColor("#0f4 ");
    assert c != null;
    assertEquals(0xff00ff44, c.getRGB());

    c = IdeResourcesUtil.parseColor(" #1237");
    assert c != null;
    assertEquals(0x11223377, c.getRGB());

    c = IdeResourcesUtil.parseColor("#123456\n\n ");
    assert c != null;
    assertEquals(0xff123456, c.getRGB());

    assertNull(IdeResourcesUtil.parseColor("#123 456"));
  }

  public void testColorToString() {
    Color c = new Color(0x0fff0000, true);
    assertEquals("#0FFF0000", IdeResourcesUtil.colorToString(c));

    c = new Color(0x00ff00);
    assertEquals("#00FF00", IdeResourcesUtil.colorToString(c));

    c = new Color(0x00000000, true);
    assertEquals("#00000000", IdeResourcesUtil.colorToString(c));

    Color color = new Color(0x11, 0x22, 0x33, 0xf0);
    assertEquals("#F0112233", IdeResourcesUtil.colorToString(color));

    color = new Color(0xff, 0xff, 0xff, 0x00);
    assertEquals("#00FFFFFF", IdeResourcesUtil.colorToString(color));
  }

  public void testDisabledStateListStates() {
    StateListState disabled = new StateListState("value", ImmutableMap.of("state_enabled", false), null);
    StateListState disabledPressed =
      new StateListState("value", ImmutableMap.of("state_enabled", false, "state_pressed", true), null);
    StateListState pressed = new StateListState("value", ImmutableMap.of("state_pressed", true), null);
    StateListState enabledPressed =
      new StateListState("value", ImmutableMap.of("state_enabled", true, "state_pressed", true), null);
    StateListState enabled = new StateListState("value", ImmutableMap.of("state_enabled", true), null);
    StateListState selected = new StateListState("value", ImmutableMap.of("state_selected", true), null);
    StateListState selectedPressed =
      new StateListState("value", ImmutableMap.of("state_selected", true, "state_pressed", true), null);
    StateListState enabledSelectedPressed =
      new StateListState("value", ImmutableMap.of("state_enabled", true, "state_selected", true, "state_pressed", true),
                         null);
    StateListState notFocused = new StateListState("value", ImmutableMap.of("state_focused", false), null);
    StateListState notChecked = new StateListState("value", ImmutableMap.of("state_checked", false), null);
    StateListState checkedNotPressed =
      new StateListState("value", ImmutableMap.of("state_checked", true, "state_pressed", false), null);

    StateList stateList = new StateList("stateList", "colors");
    stateList.addState(pressed);
    stateList.addState(disabled);
    stateList.addState(selected);
    assertThat(stateList.getDisabledStates()).containsExactly(disabled);

    stateList.addState(disabledPressed);
    assertThat(stateList.getDisabledStates()).containsExactly(disabled, disabledPressed);

    stateList = new StateList("stateList", "colors");
    stateList.addState(enabled);
    stateList.addState(pressed);
    stateList.addState(selected);
    stateList.addState(enabledPressed); // Not reachable
    stateList.addState(disabled);
    assertThat(stateList.getDisabledStates()).containsExactly(pressed, selected, enabledPressed, disabled);

    stateList = new StateList("stateList", "colors");
    stateList.addState(enabledPressed);
    stateList.addState(pressed);
    stateList.addState(selected);
    stateList.addState(disabled);
    assertThat(stateList.getDisabledStates()).containsExactly(pressed, disabled);

    stateList.addState(selectedPressed);
    assertThat(stateList.getDisabledStates()).containsExactly(pressed, disabled, selectedPressed);

    stateList = new StateList("stateList", "colors");
    stateList.addState(enabledSelectedPressed);
    stateList.addState(pressed);
    stateList.addState(selected);
    stateList.addState(disabled);
    stateList.addState(selectedPressed);
    assertThat(stateList.getDisabledStates()).containsExactly(disabled, selectedPressed);

    stateList = new StateList("stateList", "colors");
    stateList.addState(enabledPressed);
    stateList.addState(notChecked);
    stateList.addState(checkedNotPressed);
    stateList.addState(selected);
    stateList.addState(notFocused);
    assertThat(stateList.getDisabledStates()).containsExactly(selected, notFocused);
  }

  public void testResolveAsIconFromColorReference() {
    VirtualFile file = myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml");

    ResourceUrl url = ResourceUrl.parse("@color/myColor2");
    ResourceReference reference = url.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER);
    ResourceResolver rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver();
    ResourceValue value = rr.getResolvedResource(reference);
    Icon icon = IdeResourcesUtil.resolveAsIcon(rr, value, getProject(), myFacet);
    assertEquals(new ColorIcon(16, new Color(0xEEDDCC)), icon);
  }

  public void testResolveAsIconFromColorStateList() {
    myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml");
    VirtualFile file = myFixture.copyFileToProject("resourceHelper/my_state_list.xml", "res/color/my_state_list.xml");

    ResourceUrl url = ResourceUrl.parse("@color/my_state_list");
    ResourceReference reference = url.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER);
    ResourceResolver rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver();
    ResourceValue value = rr.getResolvedResource(reference);
    Icon icon = IdeResourcesUtil.resolveAsIcon(rr, value, getProject(), myFacet);
    assertEquals(new ColorsIcon(16, new Color(0xEEDDCC), new Color(0x33123456, true)), icon);
  }

  public void testResolveAsIconFromDrawable() throws IOException {
    VirtualFile file = myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml");
    ResourceUrl url = ResourceUrl.parse("@android:drawable/ic_delete");
    ResourceReference reference = url.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER);
    ResourceResolver rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver();
    ResourceValue value = rr.getResolvedResource(reference);
    Icon icon = IdeResourcesUtil.resolveAsIcon(rr, value, getProject(), myFacet);
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    icon.paintIcon(null, image.getGraphics(), 0, 0);
    BufferedImage goldenImage = ImageIO.read(new File(getTestDataPath() + "/resourceHelper/ic_delete.png"));
    ImageDiffUtil.assertImageSimilar("ic_delete", goldenImage, image, DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT);
  }

  public void testResolveAsIconFromStateListDrawable() throws IOException {
    myFixture.copyFileToProject("resourceHelper/ic_delete.png", "res/drawable/ic_delete.png");
    VirtualFile file = myFixture.copyFileToProject("resourceHelper/icon_state_list.xml", "res/drawable/icon_state_list.xml");
    ResourceUrl url = ResourceUrl.parse("@drawable/icon_state_list");
    ResourceReference reference = url.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER);
    ResourceResolver rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver();
    ResourceValue value = rr.getResolvedResource(reference);
    VirtualFile iconFile = IdeResourcesUtil.resolveDrawable(rr, value, getProject());
    assertThat(iconFile.getName()).isEqualTo("ic_delete.png");
    assertThat(iconFile.exists()).isTrue();
  }

  public void testResolveEmptyStatelist() {
    VirtualFile file = myFixture.copyFileToProject("resourceHelper/empty_state_list.xml", "res/color/empty_state_list.xml");
    ResourceResolver rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver();
    assertNotNull(rr);
    ResourceValue rv = rr.getResolvedResource(
      new ResourceReference(RES_AUTO, ResourceType.COLOR, "empty_state_list"));
    assertNotNull(rv);
    assertNull(IdeResourcesUtil.resolveColor(rr, rv, myModule.getProject()));
  }

  public void testResolve() {
    ResourceNamespace appNs = ResourceNamespace.fromPackageName("com.example.app");
    setProjectNamespace(appNs);

    PsiFile innerFileLand = myFixture.addFileToProject("res/layout-land/inner.xml", "<LinearLayout/>");
    PsiFile innerFilePort = myFixture.addFileToProject("res/layout-port/inner.xml", "<LinearLayout/>");
    String outerFileContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                              "<FrameLayout xmlns:app=\""+ appNs.getXmlNamespaceUri() +"\">\n" +
                              "\n" +
                              "    <include\n" +
                              "        layout=\"@app:layout/inner\"\n" +
                              "        android:layout_width=\"wrap_content\"\n" +
                              "        android:layout_height=\"wrap_content\" />\n" +
                              "\n" +
                              "</FrameLayout>";
    XmlFile outerFile = (XmlFile)myFixture.addFileToProject("layout/outer.xml", outerFileContent);
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myFacet).getConfiguration(innerFileLand.getVirtualFile());
    XmlTag include = outerFile.getRootTag().findFirstSubTag("include");
    ResourceValue resolved =
      IdeResourcesUtil
        .resolve(configuration.getResourceResolver(), ResourceUrl.parse(include.getAttribute("layout").getValue()), include);
    assertEquals(innerFileLand.getVirtualFile().getPath(), resolved.getValue());
    configuration.setDeviceState(configuration.getDevice().getState("Portrait"));
    resolved = IdeResourcesUtil
      .resolve(configuration.getResourceResolver(), ResourceUrl.parse(include.getAttribute("layout").getValue()), include);
    assertEquals(innerFilePort.getVirtualFile().getPath(), resolved.getValue());
  }

  @Language("XML")
  private static final String LAYOUT_FILE =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<LinearLayout xmlns:framework=\"http://schemas.android.com/apk/res/android\"\n" +
    "    framework:orientation=\"vertical\"\n" +
    "    framework:layout_width=\"fill_parent\"\n" +
    "    framework:layout_height=\"fill_parent\">\n" +
    "\n" +
    "    <TextView xmlns:newtools=\"http://schemas.android.com/tools\"\n" +
    "        framework:layout_width=\"fill_parent\"\n" +
    "        framework:layout_height=\"wrap_content\"\n" +
    "        newtools:text=\"Hello World, MyActivity\" />\n" +
    "</LinearLayout>\n";

  public void testGetResourceResolverFromXmlTag_namespacesEnabled() {
    setProjectNamespace(ResourceNamespace.fromPackageName("com.example.app"));

    XmlFile file = (XmlFile)myFixture.addFileToProject("layout/simple.xml", LAYOUT_FILE);
    XmlTag layout = file.getRootTag();
    XmlTag textview = layout.findFirstSubTag("TextView");

    ResourceNamespace.Resolver resolver = IdeResourcesUtil.getNamespaceResolver(layout);
    assertThat(resolver.uriToPrefix(TOOLS_URI)).isNull();
    assertThat(resolver.uriToPrefix(ANDROID_URI)).isEqualTo("framework");
    assertThat(resolver.prefixToUri("newtools")).isNull();
    assertThat(resolver.prefixToUri("framework")).isEqualTo(ANDROID_URI);

    resolver = IdeResourcesUtil.getNamespaceResolver(textview);
    assertThat(resolver.uriToPrefix(TOOLS_URI)).isEqualTo("newtools");
    assertThat(resolver.uriToPrefix(ANDROID_URI)).isEqualTo("framework");
    assertThat(resolver.prefixToUri("newtools")).isEqualTo(TOOLS_URI);
    assertThat(resolver.prefixToUri("framework")).isEqualTo(ANDROID_URI);
  }

  private void setProjectNamespace(ResourceNamespace appNs) {
    CommandProcessor.getInstance().runUndoTransparentAction(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      AndroidModel.set(myFacet, TestAndroidModel.namespaced(myFacet));
      Manifest.getMainManifest(myFacet).getPackage().setValue(appNs.getPackageName());
    }));
  }

  public void testGetResourceResolverFromXmlTag_namespacesDisabled() {
    XmlFile file = (XmlFile)myFixture.addFileToProject("layout/simple.xml", LAYOUT_FILE);
    XmlTag layout = file.getRootTag();
    XmlTag textview = layout.findFirstSubTag("TextView");

    ResourceNamespace.Resolver resolver = IdeResourcesUtil.getNamespaceResolver(layout);

    // "tools" is implicitly defined in non-namespaced projects.
    assertThat(resolver.uriToPrefix(TOOLS_URI)).isEqualTo("tools");

    // Proper namespacing doesn't work in non-namespaced projects, so within XML attributes, only "android" works.
    // TODO(b/74426748)
    assertThat(resolver.uriToPrefix(ANDROID_URI)).isNull();

    assertThat(resolver.prefixToUri("newtools")).isNull();
    assertThat(resolver.prefixToUri("framework")).isNull();

    resolver = IdeResourcesUtil.getNamespaceResolver(textview);
    assertThat(resolver.uriToPrefix(TOOLS_URI)).isEqualTo("tools");
    assertThat(resolver.uriToPrefix(ANDROID_URI)).isNull();
    assertThat(resolver.prefixToUri("newtools")).isNull();
    assertThat(resolver.prefixToUri("framework")).isNull();
  }

  public void testBuildResourceId() {
    assertEquals(0x7f_02_ffff, IdeResourcesUtil.buildResourceId((byte) 0x7f, (byte) 0x02, (short) 0xffff));
    assertEquals(0x02_02_0001, IdeResourcesUtil.buildResourceId((byte) 0x02, (byte) 0x02, (short) 0x0001));
  }

  public void testPsiElementGetNamespace() {
    // Project XML:
    XmlFile layoutFile = (XmlFile)myFixture.addFileToProject("layout/simple.xml", LAYOUT_FILE);
    assertThat(IdeResourcesUtil.getResourceNamespace(layoutFile)).isEqualTo(RES_AUTO);
    assertThat(IdeResourcesUtil.getResourceNamespace(layoutFile.getRootTag())).isEqualTo(RES_AUTO);

    // Project class:
    PsiClass projectClass = myFixture.addClass("package com.example; public class Hello {}");
    assertThat(IdeResourcesUtil.getResourceNamespace(projectClass)).isEqualTo(RES_AUTO);

    // Project R class:
    PsiClass rClass = myFixture.getJavaFacade().findClass("p1.p2.R", projectClass.getResolveScope());
    assertThat(IdeResourcesUtil.getResourceNamespace(rClass)).isEqualTo(RES_AUTO);

    // Project manifest:
    Manifest manifest = Manifest.getMainManifest(myFacet);
    assertThat(IdeResourcesUtil.getResourceNamespace(manifest.getXmlElement())).isEqualTo(RES_AUTO);
    assertThat(IdeResourcesUtil.getResourceNamespace(manifest.getXmlElement().getContainingFile())).isEqualTo(RES_AUTO);

    // Project Manifest class:
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      Permission newPermission = manifest.addPermission();
      newPermission.getName().setValue("p1.p2.NEW_PERMISSION");
    });
    PsiClass manifestClass = myFixture.getJavaFacade().findClass("p1.p2.Manifest", projectClass.getResolveScope());
    assertThat(IdeResourcesUtil.getResourceNamespace(manifestClass)).isEqualTo(RES_AUTO);

    // Framework class:
    PsiClass frameworkClass = myFixture.getJavaFacade().findClass("android.app.Activity");
    assertThat(IdeResourcesUtil.getResourceNamespace(frameworkClass)).isEqualTo(ANDROID);

    // Framework XML: API28 has two default app icons: res/drawable-watch/sym_def_app_icon.xml and res/drawable/sym_def_app_icon.xml
    List<ResourceItem> appIconResourceItems = ResourceRepositoryManager.getInstance(myFacet)
      .getFrameworkResources(ImmutableSet.of())
      .getResources(ANDROID, ResourceType.DRAWABLE, "sym_def_app_icon");

    for (ResourceItem appIconResourceItem : appIconResourceItems) {
      XmlFile appIcon = (XmlFile)PsiManager.getInstance(getProject()).findFile(toVirtualFile(appIconResourceItem.getSource()));
      assertThat(IdeResourcesUtil.getResourceNamespace(appIcon)).isEqualTo(ANDROID);
      assertThat(IdeResourcesUtil.getResourceNamespace(appIcon.getRootTag())).isEqualTo(ANDROID);
    }
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, "lib", PROJECT_TYPE_LIBRARY);
  }

  public void testCaseSensitivityInChangeColorResource() {
    VirtualFile xmlFile = myFixture.copyFileToProject("util/colors_before.xml", "res/values/colors.xml");
    VirtualFile resDir = xmlFile.getParent().getParent();
    List<String> dirNames = ImmutableList.of("values");
    assertTrue(IdeResourcesUtil.changeValueResource(getProject(), resDir, "myColor", ResourceType.COLOR, "#000000", "colors.xml",
                                                    dirNames, false));
    assertFalse(IdeResourcesUtil.changeValueResource(getProject(), resDir, "mycolor", ResourceType.COLOR, "#FFFFFF", "colors.xml",
                                                     dirNames, false));
    myFixture.checkResultByFile("res/values/colors.xml", "util/colors_after.xml", true);
  }

  public void testFindResourceFields() {
    myFixture.copyFileToProject("util/strings.xml", "res/values/strings.xml");

    PsiField[] fields = IdeResourcesUtil.findResourceFields(myFacet, "string", "hello", false);
    assertEquals(1, fields.length);
    PsiField field = fields[0];
    assertEquals("hello", field.getName());
    assertEquals("string", field.getContainingClass().getName());
    assertEquals("p1.p2.R", field.getContainingClass().getContainingClass().getQualifiedName());
  }

  public void testFindResourceFieldsWithMultipleResourceNames() {
    myFixture.copyFileToProject("util/strings.xml", "res/values/strings.xml");

    PsiField[] fields = IdeResourcesUtil.findResourceFields(
      myFacet, "string", ImmutableList.of("hello", "goodbye"), false);

    Set<String> fieldNames = Sets.newHashSet();
    for (PsiField field : fields) {
      fieldNames.add(field.getName());
      assertEquals("p1.p2.R", field.getContainingClass().getContainingClass().getQualifiedName());
    }
    assertEquals(ImmutableSet.of("hello", "goodbye"), fieldNames);
    assertEquals(2, fields.length);
  }

  /** Tests that "inherited" resource references are found (R fields in generated in dependent modules). */
  public void testFindResourceFieldsWithInheritance() throws Exception {
    Module libModule = myAdditionalModules.get(0);
    // Remove the current manifest (has wrong package name) and copy a manifest with proper package into the lib module.
    deleteManifest(libModule);

    myFixture.copyFileToProject("util/lib/AndroidManifest.xml", "additionalModules/lib/AndroidManifest.xml");

    // Add some lib string resources.
    myFixture.copyFileToProject("util/lib/strings.xml", "additionalModules/lib/res/values/strings.xml");

    AndroidFacet facet = AndroidFacet.getInstance(libModule);
    assertThat(facet).isNotNull();
    PsiField[] fields = IdeResourcesUtil.findResourceFields(facet, "string", "lib_hello", false /* onlyInOwnPackages */);

    Set<String> packages = Sets.newHashSet();
    for (PsiField field : fields) {
      assertEquals("lib_hello", field.getName());
      packages.add(StringUtil.getPackageName(field.getContainingClass().getContainingClass().getQualifiedName()));
    }
    assertEquals(ImmutableSet.of("p1.p2", "p1.p2.lib"), packages);
    assertEquals(2, fields.length);
  }

  /** Tests that a module without an Android Manifest can still import a lib's R class */
  public void testIsRJavaFileImportedNoManifest() throws Exception {
    Module libModule = myAdditionalModules.get(0);
    // Remove the current lib manifest (has wrong package name) and copy a manifest with proper package into the lib module.
    deleteManifest(libModule);
    myFixture.copyFileToProject("util/lib/AndroidManifest.xml", "additionalModules/lib/AndroidManifest.xml");

    // Add some lib string resources.
    myFixture.copyFileToProject("util/lib/strings.xml", "additionalModules/lib/res/values/strings.xml");
    // Remove the manifest from the main module.
    deleteManifest(myModule);

    // The main module doesn't get a generated R class and inherit fields (lack of manifest)
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertThat(facet).isNotNull();
    PsiField[] mainFields = IdeResourcesUtil.findResourceFields(facet, "string", "lib_hello", false /* onlyInOwnPackages */);
    assertEmpty(mainFields);

    // However, if the main module happens to get a handle on the lib's R class
    // (e.g., via "import p1.p2.lib.R;"), then that R class should be recognized
    // (e.g., for goto navigation).
    PsiFile javaFile =
      myFixture.addFileToProject("src/com/example/Foo.java", "package com.example; class Foo {}");
    PsiClass libRClass =
      myFixture.getJavaFacade().findClass("p1.p2.lib.R", javaFile.getResolveScope());
    assertNotNull(libRClass);
    assertTrue(IdeResourcesUtil.isRJavaClass(libRClass));
  }

  public void testEnsureNamespaceImportedAddAuto() {
    XmlFile xmlFile = ensureNamespaceImported("<LinearLayout/>", AUTO_URI, null);
    assertThat(xmlFile.getText()).isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />");
  }

  public void testEnsureNamespaceImportedAddAutoWithPrefixSuggestion() {
    XmlFile xmlFile = ensureNamespaceImported("<LinearLayout/>", AUTO_URI, "sherpa");
    assertThat(xmlFile.getText()).isEqualTo("<LinearLayout xmlns:sherpa=\"http://schemas.android.com/apk/res-auto\" />");
  }

  public void testEnsureNamespaceImportedDoNotAddAutoIfAlreadyThere() {
    @SuppressWarnings("XmlUnusedNamespaceDeclaration")
    XmlFile xmlFile = ensureNamespaceImported("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />", AUTO_URI, null);
    assertThat(xmlFile.getText()).isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />");
  }

  public void testEnsureNamespaceImportedDoNotAddAutoIfAlreadyThereWithPrefixSuggestion() {
    @SuppressWarnings("XmlUnusedNamespaceDeclaration")
    XmlFile xmlFile = ensureNamespaceImported("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />", AUTO_URI, "sherpa");
    assertThat(xmlFile.getText()).isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />");
  }

  public void testEnsureNamespaceImportedAddEmptyNamespaceForStyleAttribute() {
    XmlFile xmlFile = ensureNamespaceImported("<LinearLayout/>", "", null);
    assertThat(xmlFile.getText()).isEqualTo("<LinearLayout/>");
  }

  private XmlFile ensureNamespaceImported(@Language("XML") @NotNull String text, @NotNull String namespaceUri, @Nullable String suggestedPrefix) {
    XmlFile xmlFile = (XmlFile)myFixture.configureByText("res/layout/layout.xml", text);

    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      IdeResourcesUtil.ensureNamespaceImported(xmlFile, namespaceUri, suggestedPrefix);
    }), "", "");

    return xmlFile;
  }

  public void testCreateFrameLayoutFileResource() throws Exception {
    XmlFile file = IdeResourcesUtil.createFileResource("linear", getLayoutFolder(), FRAME_LAYOUT, ResourceType.LAYOUT.getName(), false);
    assertThat(file.getName()).isEqualTo("linear.xml");
    assertThat(file.getText()).isEqualTo("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                         "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                         "    android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">\n" +
                                         "\n" +
                                         "</FrameLayout>");
  }

  public void testCreateLinearLayoutFileResource() throws Exception {
    XmlFile file = IdeResourcesUtil.createFileResource("linear", getLayoutFolder(), LINEAR_LAYOUT, ResourceType.LAYOUT.getName(), false);
    assertThat(file.getName()).isEqualTo("linear.xml");
    assertThat(file.getText()).isEqualTo("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                         "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                         "    android:orientation=\"vertical\" android:layout_width=\"match_parent\"\n" +
                                         "    android:layout_height=\"match_parent\">\n" +
                                         "\n" +
                                         "</LinearLayout>");
  }

  public void testCreateLayoutFileResource() throws Exception {
    XmlFile file = IdeResourcesUtil.createFileResource("layout", getLayoutFolder(), TAG_LAYOUT, ResourceType.LAYOUT.getName(), false);
    assertThat(file.getName()).isEqualTo("layout.xml");
    assertThat(file.getText()).isEqualTo("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                         "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                                         "\n" +
                                         "</layout>");
  }

  public void testCreateMergeFileResource() throws Exception {
    XmlFile file = IdeResourcesUtil.createFileResource("merge", getLayoutFolder(), VIEW_MERGE, ResourceType.LAYOUT.getName(), false);
    assertThat(file.getName()).isEqualTo("merge.xml");
    assertThat(file.getText()).isEqualTo("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                         "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                                         "\n" +
                                         "</merge>");
  }

  public void testCreateNavigationFileResource() throws Exception {
    XmlFile file =
      IdeResourcesUtil.createFileResource("nav", getLayoutFolder(), TAG_NAVIGATION, ResourceType.NAVIGATION.getName(), false);
    assertThat(file.getName()).isEqualTo("nav.xml");
    assertThat(file.getText()).isEqualTo("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                         "<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                         "    xmlns:app=\"http://schemas.android.com/apk/res-auto\" android:id=\"@+id/nav\">\n" +
                                         "\n" +
                                         "</navigation>");
  }

  public void testBuildResourceNameFromStringValue_simpleName() {
    assertThat(IdeResourcesUtil.buildResourceNameFromStringValue("Just simple string")).isEqualTo("just_simple_string");
  }

  public void testBuildResourceNameFromStringValue_nameWithSurroundingSpaces() {
    assertThat(IdeResourcesUtil.buildResourceNameFromStringValue(" Just a simple string ")).isEqualTo("just_a_simple_string");
  }

  public void testBuildResourceNameFromStringValue_nameWithDigits() {
    assertThat(IdeResourcesUtil.buildResourceNameFromStringValue("A string with 31337 number")).isEqualTo("a_string_with_31337_number");
  }

  public void testBuildResourceNameFromStringValue_nameShouldNotStartWithNumber() {
    assertThat(IdeResourcesUtil.buildResourceNameFromStringValue("100 things")).isEqualTo("_100_things");
  }

  public void testBuildResourceNameFromStringValue_emptyString() {
    assertThat(IdeResourcesUtil.buildResourceNameFromStringValue("")).isNull();
  }

  public void testBuildResourceNameFromStringValue_stringHasPunctuation() {
    assertThat(IdeResourcesUtil.buildResourceNameFromStringValue("Hello!!#^ But why??")).isEqualTo("hello_but_why");
  }

  public void testBuildResourceNameFromStringValue_stringIsOnlyPunctuation() {
    assertThat(IdeResourcesUtil.buildResourceNameFromStringValue("!!#^??")).isNull();
  }

  public void testBuildResourceNameFromStringValue_stringStartsAndEndsWithPunctuation() {
    assertThat(IdeResourcesUtil.buildResourceNameFromStringValue("\"A quotation\"")).isEqualTo("a_quotation");
    assertThat(IdeResourcesUtil.buildResourceNameFromStringValue("<tag>")).isEqualTo("tag");
  }

  @NotNull
  private PsiDirectory getLayoutFolder() {
    PsiFile file = myFixture.configureByText("res/layout/main.xml", "<LinearLayout/>");
    PsiDirectory folder = file.getParent();
    assertThat(folder).isNotNull();
    return folder;
  }

  @NotNull
  private PsiDirectory getNavigationFolder() {
    PsiFile file = myFixture.configureByText("res/navigation/main.xml", "<navigation/>");
    PsiDirectory folder = file.getParent();
    assertThat(folder).isNotNull();
    return folder;
  }
}
