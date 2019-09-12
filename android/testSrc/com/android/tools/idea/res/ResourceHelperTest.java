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
package com.android.tools.idea.res;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.ide.common.rendering.api.ResourceNamespace.ANDROID;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.adtui.imagediff.ImageDiffUtil.DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT;
import static com.android.tools.idea.util.FileExtensions.toVirtualFile;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.model.TestAndroidModel;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.TwoColorsIcon;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.Permission;

public class ResourceHelperTest extends AndroidTestCase {
  public void testIsFileBasedResourceType() {
    assertTrue(ResourceHelper.isFileBased(ResourceType.ANIMATOR));
    assertTrue(ResourceHelper.isFileBased(ResourceType.LAYOUT));

    assertFalse(ResourceHelper.isFileBased(ResourceType.STRING));
    assertFalse(ResourceHelper.isFileBased(ResourceType.DIMEN));
    assertFalse(ResourceHelper.isFileBased(ResourceType.ID));

    // Both:
    assertTrue(ResourceHelper.isFileBased(ResourceType.DRAWABLE));
    assertTrue(ResourceHelper.isFileBased(ResourceType.COLOR));
  }

  public void testIsValueBasedResourceType() {
    assertTrue(ResourceHelper.isValueBased(ResourceType.STRING));
    assertTrue(ResourceHelper.isValueBased(ResourceType.DIMEN));
    assertTrue(ResourceHelper.isValueBased(ResourceType.ID));

    assertFalse(ResourceHelper.isValueBased(ResourceType.LAYOUT));

    // These can be both:
    assertTrue(ResourceHelper.isValueBased(ResourceType.DRAWABLE));
    assertTrue(ResourceHelper.isValueBased(ResourceType.COLOR));
  }

  public void testStyleToTheme() {
    assertEquals("Foo", ResourceHelper.styleToTheme("Foo"));
    assertEquals("Theme", ResourceHelper.styleToTheme("@android:style/Theme"));
    assertEquals("LocalTheme", ResourceHelper.styleToTheme("@style/LocalTheme"));
    assertEquals("LocalTheme", ResourceHelper.styleToTheme("@foo.bar:style/LocalTheme"));
  }

  public void testGetResourceNameAndUrl() {
    PsiFile file1 = myFixture.addFileToProject("res/layout-land/foo1.xml", "<LinearLayout/>");
    PsiFile file2 = myFixture.addFileToProject("res/menu-en-rUS/foo2.xml", "<menu/>");
    // Not a proper PNG file, but we just need a .9.something path to verify basename handling is right
    // and it has to be an XML file to get a PSI file out of the fixture
    PsiFile file3 = myFixture.addFileToProject("res/drawable-hdpi/foo3.9.xml", "invalidImage");

    assertEquals("foo1", ResourceHelper.getResourceName(file1));
    assertEquals("foo2", ResourceHelper.getResourceName(file2));
    assertEquals("foo3", ResourceHelper.getResourceName(file3));
    assertEquals("foo1", ResourceHelper.getResourceName(file1.getVirtualFile()));
    assertEquals("foo2", ResourceHelper.getResourceName(file2.getVirtualFile()));
    assertEquals("foo3", ResourceHelper.getResourceName(file3.getVirtualFile()));
  }

  public void testGetFolderConfiguration() {
    PsiFile file1 = myFixture.addFileToProject("res/layout-land/foo1.xml", "<LinearLayout/>");
    PsiFile file2 = myFixture.addFileToProject("res/menu-en-rUS/foo2.xml", "<menu/>");

    assertEquals("layout-land", ResourceHelper.getFolderConfiguration(file1).getFolderName(ResourceFolderType.LAYOUT));
    assertEquals("menu-en-rUS", ResourceHelper.getFolderConfiguration(file2).getFolderName(ResourceFolderType.MENU));
    assertEquals("layout-land", ResourceHelper.getFolderConfiguration(file1.getVirtualFile()).getFolderName(ResourceFolderType.LAYOUT));
    assertEquals("menu-en-rUS", ResourceHelper.getFolderConfiguration(file2.getVirtualFile()).getFolderName(ResourceFolderType.MENU));
  }

  public void testParseColor() {
    Color c = ResourceHelper.parseColor("#0f4");
    assert c != null;
    assertEquals(0xff00ff44, c.getRGB());

    c = ResourceHelper.parseColor("#1237");
    assert c != null;
    assertEquals(0x11223377, c.getRGB());

    c = ResourceHelper.parseColor("#123456");
    assert c != null;
    assertEquals(0xff123456, c.getRGB());

    c = ResourceHelper.parseColor("#08123456");
    assert c != null;
    assertEquals(0x08123456, c.getRGB());

    // Test that spaces are correctly trimmed
    c = ResourceHelper.parseColor("#0f4 ");
    assert c != null;
    assertEquals(0xff00ff44, c.getRGB());

    c = ResourceHelper.parseColor(" #1237");
    assert c != null;
    assertEquals(0x11223377, c.getRGB());

    c = ResourceHelper.parseColor("#123456\n\n ");
    assert c != null;
    assertEquals(0xff123456, c.getRGB());

    assertNull(ResourceHelper.parseColor("#123 456"));
  }

  public void testColorToString() {
    Color c = new Color(0x0fff0000, true);
    assertEquals("#0FFF0000", ResourceHelper.colorToString(c));

    c = new Color(0x00ff00);
    assertEquals("#00FF00", ResourceHelper.colorToString(c));

    c = new Color(0x00000000, true);
    assertEquals("#00000000", ResourceHelper.colorToString(c));

    Color color = new Color(0x11, 0x22, 0x33, 0xf0);
    assertEquals("#F0112233", ResourceHelper.colorToString(color));

    color = new Color(0xff, 0xff, 0xff, 0x00);
    assertEquals("#00FFFFFF", ResourceHelper.colorToString(color));
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
    Icon icon = ResourceHelper.resolveAsIcon(rr, value, getProject(), myFacet);
    assertEquals(new ColorIcon(16, new Color(0xEEDDCC)), icon);
  }

  public void testResolveAsIconFromColorStateList() {
    myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml");
    VirtualFile file = myFixture.copyFileToProject("resourceHelper/my_state_list.xml", "res/color/my_state_list.xml");

    ResourceUrl url = ResourceUrl.parse("@color/my_state_list");
    ResourceReference reference = url.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER);
    ResourceResolver rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver();
    ResourceValue value = rr.getResolvedResource(reference);
    Icon icon = ResourceHelper.resolveAsIcon(rr, value, getProject(), myFacet);
    assertEquals(new TwoColorsIcon(16, new Color(0xEEDDCC), new Color(0x33123456, true)), icon);
  }

  public void testResolveAsIconFromDrawable() throws IOException {
    VirtualFile file = myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml");
    ResourceUrl url = ResourceUrl.parse("@android:drawable/ic_delete");
    ResourceReference reference = url.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER);
    ResourceResolver rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver();
    ResourceValue value = rr.getResolvedResource(reference);
    Icon icon = ResourceHelper.resolveAsIcon(rr, value, getProject(), myFacet);
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    icon.paintIcon(null, image.getGraphics(), 0, 0);
    BufferedImage goldenImage = ImageIO.read(new File(getTestDataPath() + "/resourceHelper/ic_delete.png"));
    ImageDiffUtil.assertImageSimilar("ic_delete", goldenImage, image, DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT);
  }

  public void testGetCompletionFromTypes() {
    myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml");
    myFixture.copyFileToProject("resourceHelper/my_state_list.xml", "res/color/my_state_list.xml");

    List<String> colorOnly = ResourceHelper.getCompletionFromTypes(myFacet, EnumSet.of(ResourceType.COLOR));
    List<String> drawableOnly = ResourceHelper.getCompletionFromTypes(myFacet, EnumSet.of(ResourceType.DRAWABLE));
    List<String> colorAndDrawable =
      ResourceHelper.getCompletionFromTypes(myFacet, EnumSet.of(ResourceType.COLOR, ResourceType.DRAWABLE));
    List<String> dimenOnly = ResourceHelper.getCompletionFromTypes(myFacet, EnumSet.of(ResourceType.DIMEN));

    assertThat(colorOnly).containsAllOf("@android:color/primary_text_dark", "@color/myColor1", "@color/myColor2", "@color/my_state_list");
    assertThat(drawableOnly).containsAllOf("@color/myColor1", "@color/myColor2", "@android:drawable/menuitem_background");
    assertThat(colorAndDrawable)
      .containsAllOf("@android:color/primary_text_dark", "@color/myColor1", "@color/myColor2", "@color/my_state_list",
                       "@android:drawable/menuitem_background");
    assertThat(dimenOnly).containsAllOf("@dimen/myAlpha", "@dimen/myDimen");
  }

  public void testResolveEmptyStatelist() {
    VirtualFile file = myFixture.copyFileToProject("resourceHelper/empty_state_list.xml", "res/color/empty_state_list.xml");
    ResourceResolver rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver();
    assertNotNull(rr);
    ResourceValue rv = rr.getResolvedResource(
        new ResourceReference(RES_AUTO, ResourceType.COLOR, "empty_state_list"));
    assertNotNull(rv);
    assertNull(ResourceHelper.resolveColor(rr, rv, myModule.getProject()));
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
      ResourceHelper.resolve(configuration.getResourceResolver(), ResourceUrl.parse(include.getAttribute("layout").getValue()), include);
    assertEquals(innerFileLand.getVirtualFile().getPath(), resolved.getValue());
    configuration.setDeviceState(configuration.getDevice().getState("Portrait"));
    resolved = ResourceHelper
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

    ResourceNamespace.Resolver resolver = ResourceHelper.getNamespaceResolver(layout);
    assertThat(resolver.uriToPrefix(TOOLS_URI)).isNull();
    assertThat(resolver.uriToPrefix(ANDROID_URI)).isEqualTo("framework");
    assertThat(resolver.prefixToUri("newtools")).isNull();
    assertThat(resolver.prefixToUri("framework")).isEqualTo(ANDROID_URI);

    resolver = ResourceHelper.getNamespaceResolver(textview);
    assertThat(resolver.uriToPrefix(TOOLS_URI)).isEqualTo("newtools");
    assertThat(resolver.uriToPrefix(ANDROID_URI)).isEqualTo("framework");
    assertThat(resolver.prefixToUri("newtools")).isEqualTo(TOOLS_URI);
    assertThat(resolver.prefixToUri("framework")).isEqualTo(ANDROID_URI);
  }

  private void setProjectNamespace(ResourceNamespace appNs) {
    CommandProcessor.getInstance().runUndoTransparentAction(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      myFacet.getConfiguration().setModel(TestAndroidModel.namespaced(myFacet));
      Manifest.getMainManifest(myFacet).getPackage().setValue(appNs.getPackageName());
    }));
  }

  public void testGetResourceResolverFromXmlTag_namespacesDisabled() {
    XmlFile file = (XmlFile)myFixture.addFileToProject("layout/simple.xml", LAYOUT_FILE);
    XmlTag layout = file.getRootTag();
    XmlTag textview = layout.findFirstSubTag("TextView");

    ResourceNamespace.Resolver resolver = ResourceHelper.getNamespaceResolver(layout);

    // "tools" is implicitly defined in non-namespaced projects.
    assertThat(resolver.uriToPrefix(TOOLS_URI)).isEqualTo("tools");

    // Proper namespacing doesn't work in non-namespaced projects, so within XML attributes, only "android" works.
    // TODO(b/74426748)
    assertThat(resolver.uriToPrefix(ANDROID_URI)).isNull();

    assertThat(resolver.prefixToUri("newtools")).isNull();
    assertThat(resolver.prefixToUri("framework")).isNull();

    resolver = ResourceHelper.getNamespaceResolver(textview);
    assertThat(resolver.uriToPrefix(TOOLS_URI)).isEqualTo("tools");
    assertThat(resolver.uriToPrefix(ANDROID_URI)).isNull();
    assertThat(resolver.prefixToUri("newtools")).isNull();
    assertThat(resolver.prefixToUri("framework")).isNull();
  }

  public void testBuildResourceId() {
    assertEquals(0x7f_02_ffff, ResourceHelper.buildResourceId((byte) 0x7f, (byte) 0x02, (short) 0xffff));
    assertEquals(0x02_02_0001, ResourceHelper.buildResourceId((byte) 0x02, (byte) 0x02, (short) 0x0001));
  }

  public void testToFileResourcePathString() {
    assertThat(ResourceHelper.toFileResourcePathString("apk:///foo.apk!/bar.baz")).isEqualTo(new PathString("apk", "/foo.apk!/bar.baz"));
  }

  public void testPsiElementGetNamespace() {

    // Project XML:
    XmlFile layoutFile = (XmlFile)myFixture.addFileToProject("layout/simple.xml", LAYOUT_FILE);
    assertThat(ResourceHelper.getResourceNamespace(layoutFile)).isEqualTo(RES_AUTO);
    assertThat(ResourceHelper.getResourceNamespace(layoutFile.getRootTag())).isEqualTo(RES_AUTO);

    // Project class:
    PsiClass projectClass = myFixture.addClass("package com.example; public class Hello {}");
    assertThat(ResourceHelper.getResourceNamespace(projectClass)).isEqualTo(RES_AUTO);

    // Project R class:
    PsiClass rClass = myFixture.getJavaFacade().findClass("p1.p2.R", projectClass.getResolveScope());
    assertThat(ResourceHelper.getResourceNamespace(rClass)).isEqualTo(RES_AUTO);

    // Project manifest:
    Manifest manifest = Manifest.getMainManifest(myFacet);
    assertThat(ResourceHelper.getResourceNamespace(manifest.getXmlElement())).isEqualTo(RES_AUTO);
    assertThat(ResourceHelper.getResourceNamespace(manifest.getXmlElement().getContainingFile())).isEqualTo(RES_AUTO);

    // Project Manifest class:
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      Permission newPermission = manifest.addPermission();
      newPermission.getName().setValue("p1.p2.NEW_PERMISSION");
    });
    PsiClass manifestClass = myFixture.getJavaFacade().findClass("p1.p2.Manifest", projectClass.getResolveScope());
    assertThat(ResourceHelper.getResourceNamespace(manifestClass)).isEqualTo(RES_AUTO);

    // Framework class:
    PsiClass frameworkClass = myFixture.getJavaFacade().findClass("android.app.Activity");
    assertThat(ResourceHelper.getResourceNamespace(frameworkClass)).isEqualTo(ANDROID);

    // Framework XML: API28 has two default app icons: res/drawable-watch/sym_def_app_icon.xml and res/drawable/sym_def_app_icon.xml
    List<ResourceItem> appIconResourceItems = ResourceRepositoryManager.getInstance(myFacet)
      .getFrameworkResources(ImmutableSet.of())
      .getResources(ANDROID, ResourceType.DRAWABLE, "sym_def_app_icon");

    for (ResourceItem appIconResourceItem : appIconResourceItems) {
      XmlFile appIcon = (XmlFile)PsiManager.getInstance(getProject()).findFile(toVirtualFile(appIconResourceItem.getSource()));
      assertThat(ResourceHelper.getResourceNamespace(appIcon)).isEqualTo(ANDROID);
      assertThat(ResourceHelper.getResourceNamespace(appIcon.getRootTag())).isEqualTo(ANDROID);
    }
  }
}
