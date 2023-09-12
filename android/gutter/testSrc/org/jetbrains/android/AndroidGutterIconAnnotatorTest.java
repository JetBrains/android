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
package org.jetbrains.android;

import static com.android.testutils.ImageDiffUtil.assertImageSimilar;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.ide.common.util.PathString;
import com.android.sdklib.IAndroidTarget;
import com.android.testutils.ImageDiffUtil;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.GutterIconCache;
import com.android.tools.idea.rendering.GutterIconRenderer.NavigationTargetProvider;
import com.android.tools.idea.rendering.TestRenderingUtils;
import com.android.tools.idea.testing.AndroidTestUtils;
import com.android.tools.idea.ui.resourcemanager.rendering.MultipleColorIcon;
import com.android.tools.idea.util.FileExtensions;
import com.google.common.collect.ImmutableList;
import com.intellij.codeInsight.daemon.GutterIconDescriptor;
import com.intellij.codeInsight.daemon.LineMarkerSettings;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.LineMarkerSettingsImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.ColorIcon;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link AndroidJavaResourceExternalAnnotator}, and {@link AndroidXMLResourceExternalAnnotator}.
 */
@SuppressWarnings("UseJBColor")
public class AndroidGutterIconAnnotatorTest extends AndroidTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IconLoader.activate();
    myFixture.copyFileToProject("annotator/color_test.xml", "res/layout/color_test.xml");
    myFixture.copyFileToProject("annotator/colors.xml", "res/values/colors1.xml");
    myFixture.copyFileToProject("annotator/colors.xml", "res/values/colors2.xml");
    myFixture.copyFileToProject("annotator/colors-night.xml", "res/values-night/colors.xml");
    myFixture.copyFileToProject("annotator/layout.xml", "res/layout/layout1.xml");
    myFixture.copyFileToProject("annotator/layout.xml", "res/layout/layout2.xml");
    myFixture.copyFileToProject("annotator/ColorTest.java", "src/p1/p2/ColorTest.java");
    myFixture.copyFileToProject("annotator/EmptyActivity.java", "src/p1/p2/EmptyActivity.java");
    myFixture.copyFileToProject("annotator/ic_tick.xml", "res/drawable/ic_tick.xml");
    myFixture.copyFileToProject("annotator/layer_list.xml", "res/drawable/layer_list.xml");
    myFixture.copyFileToProject("annotator/selector.xml", "res/color/selector.xml");
    myFixture.copyFileToProject("annotator/shape.xml", "res/drawable/shape.xml");
    myFixture.copyFileToProject("annotator/animated_selector.xml", "res/drawable/animated_selector.xml");
    myFixture.copyFileToProject("annotator/values.xml", "res/values/values.xml");
    myFixture.copyFileToProject("render/imageutils/actual.png", "res/drawable-mdpi/drawable1.png");
    myFixture.copyFileToProject("annotator/ic_launcher.png", "res/drawable/ic_launcher.png");
    myFixture.copyFileToProject("annotator/customDrawable.xml", "res/drawable/customDrawable.xml");
    myFixture.copyFileToProject("annotator/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject("annotator/themes.xml", "res/values/themes.xml");
  }

  @Override
  protected boolean isIconRequired() {
    return true;
  }

  public void testMacroWithLiteral() {
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "?macroColorAttr", XmlAttributeValue.class);
    checkHighlightInfoColors(highlightInfo, ImmutableList.of(new Color(171, 193, 35)));
  }

  public void testItemWithLiteral() {
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "?colorPrimary", XmlAttributeValue.class);
    checkHighlightInfoColors(highlightInfo, ImmutableList.of(new Color(171, 193, 35)));
  }

  public void testMacroTags() {
    // Reference the value of the macro tag which is a color literal, to a color.
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/values/colors1.xml", "3F5BBB", XmlTag.class);
    checkHighlightInfoColor(highlightInfo, new Color(63, 91, 187));

    // Macro tag that references a color
    HighlightInfo colorHighlightInfo =
      findHighlightInfoWithGutterRenderer("res/values/colors1.xml", "@color/color2", XmlTag.class);
    checkHighlightInfoColors(colorHighlightInfo, ImmutableList.of(new Color(48, 63, 159)));

    // Color tag that references a macro, that references a color
    HighlightInfo macroHighlightInfo =
      findHighlightInfoWithGutterRenderer("res/values/colors1.xml", "@macro/macro2", XmlTag.class);
    checkHighlightInfoColors(macroHighlightInfo, ImmutableList.of(new Color(48, 63, 159)));
  }

  public void testDrawableInManifest() throws IOException {
    // Drawable icon in AndroidManifest.xml file.
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer(SdkConstants.FN_ANDROID_MANIFEST_XML, "@drawable/drawable1", XmlAttributeValue.class);
    checkHighlightInfoImage(highlightInfo, "annotator/drawable1_thumbnail.png");
  }

  public void testNoResourceReferences() {
    // Testing a java file with no references and no gutter icons
    List<HighlightInfo> highlightInfo = findAllHighlightInfo("src/p1/p2/EmptyActivity.java");
    List<HighlightInfo> gutterIconInfo =
      highlightInfo.stream().filter(info -> info.getGutterIconRenderer() != null).collect(Collectors.toList());
    assertThat(gutterIconInfo).isEmpty();
  }

  public void testColorReferenceInJava1() {
    // Color resource reference in java file
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("src/p1/p2/ColorTest.java", "R.color.color1", PsiReferenceExpression.class);
    checkHighlightInfoColors(highlightInfo, ImmutableList.of((new Color(63, 81, 181))));
  }

  public void testColorReferenceInJava2() {
    // Color resource reference in java file
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("src/p1/p2/ColorTest.java", "R.color.color2", PsiReferenceExpression.class);
    checkHighlightInfoColors(highlightInfo, ImmutableList.of((new Color(0x303F9F))));
  }

  public void testSelectorReferenceInJava() {
    // Selector color resource reference in java file
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("src/p1/p2/ColorTest.java", "R.color.selector", PsiReferenceExpression.class);
    checkHighlightInfoColors(highlightInfo, ImmutableList.of(new Color(255, 0, 0), new Color(0, 0, 255), new Color(0, 255, 0)));
  }

  public void testJavaFileWithErrors() {
    myFixture.openFileInEditor(getPsiFile("src/p1/p2/ColorTest.java").getVirtualFile());
    AndroidTestUtils.moveCaret(myFixture, "R.color.selector|;");
    myFixture.type("not valid code anymore!!!");
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("src/p1/p2/ColorTest.java", "R.color.color1", PsiReferenceExpression.class);
    checkHighlightInfoColors(highlightInfo, ImmutableList.of((new Color(63, 81, 181))));
  }

  public void testColorInValues1() {
    // Color definition in a values file
    HighlightInfo highlightInfo = findHighlightInfoWithGutterRenderer("res/values/colors1.xml", "3F51B5", XmlTag.class);
    checkHighlightInfoColor(highlightInfo, new Color(63, 81, 181));
    // Inline color have a color picker click action
    assertThat(((GutterIconRenderer)highlightInfo.getGutterIconRenderer()).getClickAction()).isNotNull();
  }

  public void testColorInValues2() {
    // Color definition in a values file
    HighlightInfo highlightInfo = findHighlightInfoWithGutterRenderer("res/values/colors2.xml", "303F9F", XmlTag.class);
    checkHighlightInfoColor(highlightInfo, new Color(0x303F9F));
    // Inline color have a color picker click action
    assertThat(((GutterIconRenderer)highlightInfo.getGutterIconRenderer()).getClickAction()).isNotNull();
  }

  /**
   * Regression test for https://issuetracker.google.com/151480852
   */
  public void testColorInValuesWithQualifiers() {
    // Color definition in a non-default values.xml file, referencing a color which is overriden in the same file
    HighlightInfo highlightInfo = findHighlightInfoWithGutterRenderer("res/values-night/colors.xml", "@color/color2", XmlTag.class);
    checkHighlightInfoColors(highlightInfo, ImmutableList.of((new Color(0xffffff))));
  }

  public void testColorInLayout1() {
    // Color definition in a layout file
    HighlightInfo highlightInfo = findHighlightInfoWithGutterRenderer("res/layout/layout1.xml", "FAFAFA", XmlTag.class);
    checkHighlightInfoColor(highlightInfo, new Color(0xFAFAFA));
    // Inline color have a color picker click action
    assertThat(((GutterIconRenderer)highlightInfo.getGutterIconRenderer()).getClickAction()).isNotNull();
  }

  public void testColorInLayout2() {
    // Color definition in a layout file
    HighlightInfo highlightInfo = findHighlightInfoWithGutterRenderer("res/layout/layout2.xml", "FBFBFB", XmlTag.class);
    checkHighlightInfoColor(highlightInfo, new Color(0xFBFBFB));
    // Inline color have a color picker click action
    assertThat(((GutterIconRenderer)highlightInfo.getGutterIconRenderer()).getClickAction()).isNotNull();
  }

  public void testColorStateListInValues() {
    // Color definition in a color state list file
    HighlightInfo highlightInfo = findHighlightInfoWithGutterRenderer("res/color/selector.xml", "ffff0000", XmlAttributeValue.class);
    checkHighlightInfoColor(highlightInfo, new Color(255, 0, 0));
    highlightInfo = findHighlightInfoWithGutterRenderer("res/color/selector.xml", "#ff00ff00", XmlAttributeValue.class);
    checkHighlightInfoColor(highlightInfo, new Color(0, 255, 0));
    // Inline color have a color picker click action
    assertThat(((GutterIconRenderer)highlightInfo.getGutterIconRenderer()).getClickAction()).isNotNull();
  }

  public void testColorReferenceInXml1() {
    // Reference to a color from a layout file
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "@color/color1", XmlAttributeValue.class);
    checkHighlightInfoColors(highlightInfo, ImmutableList.of(new Color(63, 81, 181)));
    // Click to open the color picker
    assertThat(((GutterIconRenderer)highlightInfo.getGutterIconRenderer()).getClickAction()).isNotNull();
  }

  public void testColorReferenceInXml2() {
    // Reference to a color from a layout file
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "@color/color2", XmlAttributeValue.class);
    checkHighlightInfoColors(highlightInfo, ImmutableList.of(new Color(0x303F9F)));
    // Click to open the color picker
    assertThat(((GutterIconRenderer)highlightInfo.getGutterIconRenderer()).getClickAction()).isNotNull();
  }

  public void testColorReferenceInXml3() {
    // Reference to a selector color from a layout file
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "@color/selector", XmlAttributeValue.class);
    checkHighlightInfoColors(highlightInfo, ImmutableList.of(new Color(255, 0, 0), new Color(0, 0, 255), new Color(0, 255, 0)));
  }

  public void testIconReferenceInXml() throws IOException {
    // Reference to an icon from a layout file
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "@drawable/drawable1", XmlAttributeValue.class);
    checkHighlightInfoImage(highlightInfo, "annotator/drawable1_thumbnail.png");
  }

  public void testVectorReferenceInXml() throws IOException {
    // Reference to a vector drawable from a layout file.
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "@drawable/ic_tick", XmlAttributeValue.class);
    checkHighlightInfoImage(highlightInfo, "annotator/ic_tick_thumbnail.png");
  }

  public void testLayerList() throws Exception {
    // Reference to a layer-list drawable from a layout file.
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "@drawable/layer_list", XmlAttributeValue.class);
    checkHighlightInfoImage(highlightInfo, "annotator/ic_layer_list_thumbnail.png");
  }

  public void testShape() throws Exception {
    // Reference to a layer-list drawable from a layout file.
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "@drawable/shape", XmlAttributeValue.class);
    checkHighlightInfoImage(highlightInfo, "annotator/ic_shape_thumbnail.png");
  }

  public void testFrameworkDrawable() throws Exception {
    String layoutPath = "res/layout/color_test.xml";
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer(layoutPath, "@android:drawable/ic_lock_lock", XmlAttributeValue.class);

    // The path of the drawable in framework resources.
    PathString expectedPath = new PathString(getFrameworkResourcesPath() + "/drawable-ldpi/ic_lock_lock_alpha.png");
    VirtualFile expectedFile = FileExtensions.toVirtualFile(expectedPath);
    assertThat(getNavigationTarget(highlightInfo)).isEqualTo(expectedFile);
    checkHighlightInfoImage(highlightInfo, expectedFile);
  }

  public void testThemeAttributeDrawable() throws Exception {
    // Reference to a theme attribute drawable from a layout file.
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "?android:attr/actionModeCutDrawable", XmlAttributeValue.class);
    checkHighlightInfoImage(highlightInfo, "annotator/ic_actionModeCutDrawable_thumbnail.png");
  }

  public void testAnimatedSelectorRenderedIcon() throws Exception {
    // Reference to an animated selector drawable from a layout file, rendered by layoutlib
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "@drawable/animated_selector", XmlAttributeValue.class);
    checkHighlightInfoImage(highlightInfo, "annotator/ic_rendered_image.png");
  }

  public void testBrokenDrawableFallbackIcon() throws Exception {
    // Reference to a broken custom drawable from a layout file, not rendered by layoutlib, so the fallback icon is used
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "@drawable/customDrawable", XmlAttributeValue.class);
    checkHighlightInfoImage(highlightInfo, "annotator/ic_fallback_thumbnail.png");
  }

  public void testColorThemeAttribute() {
    // Reference to a color theme attribute from a layout file.
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("res/layout/color_test.xml", "?android:attr/actionMenuTextColor", XmlAttributeValue.class);

    //noinspection InspectionUsingGrayColors
    checkHighlightInfoColors(highlightInfo, ImmutableList.of(
      new Color(255, 255, 255, 128),
      new Color(255, 255, 255, 255),
      new Color(0, 0, 0),
      new Color(0, 0, 0),
      new Color(0, 0, 0),
      new Color(255, 255, 255)));
  }

  public void testDrawableGutterActionInCode() throws IOException {
    myFixture.addClass("package p1.p2;\n" +
                       "\n" +
                       "public class DrawableTest {\n" +
                       "    public void test() {\n" +
                       "        int drawable = R.drawable.ic_launcher;\n" +
                       "    }\n" +
                       "}\n");
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("src/p1/p2/DrawableTest.java", "R.drawable.ic_launcher", PsiReferenceExpression.class);
    checkHighlightInfoImage(highlightInfo, "annotator/ic_launcher.png");
    assertThat(((GutterIconRenderer)highlightInfo.getGutterIconRenderer()).getClickAction()).isNotNull();
  }

  public void testDimenThemeAttributeNoGutterRenderer() {
    // Reference to a dimension theme attribute from a layout file.
    HighlightInfo highlightInfo =
      findHighlightInfo("res/layout/color_test.xml", "?android:attr/buttonCornerRadius", XmlAttributeValue.class);
    assertThat(highlightInfo.getGutterIconRenderer()).isNull();
  }

  public void testOnlyLargeDrawable() throws IOException {
    // Reference to a large drawable where no smaller one exists.
    myFixture.addClass("package p1.p2;\n" +
                       "\n" +
                       "public class DrawableTest {\n" +
                       "    public void test() {\n" +
                       "        int drawable = R.drawable.ic_launcher;\n" +
                       "    }\n" +
                       "}\n");
    HighlightInfo highlightInfo =
      findHighlightInfoWithGutterRenderer("src/p1/p2/DrawableTest.java", "R.drawable.ic_launcher", PsiReferenceExpression.class);
    checkHighlightInfoImage(highlightInfo, "annotator/ic_launcher.png");
  }

  public void testSettingDisablesIcon() throws IOException {
    myFixture.addClass("package p1.p2;\n" +
                       "\n" +
                       "public class DrawableTest {\n" +
                       "    public void test() {\n" +
                       "        int drawable = R.drawable.ic_launcher;\n" +
                       "    }\n" +
                       "}\n");

    GutterIconDescriptor gutterIconDescriptor = new AndroidResourceExternalAnnotatorBase.LineMarkerProvider();
    LineMarkerSettingsImpl settings = (LineMarkerSettingsImpl)ApplicationManager.getApplication().getService(LineMarkerSettings.class);
    try {
      settings.setEnabled(gutterIconDescriptor, false);

      assertThrows(NoSuchElementException.class, () -> {
        findHighlightInfoWithGutterRenderer("src/p1/p2/DrawableTest.java", "R.drawable.ic_launcher", PsiReferenceExpression.class);
      });

      settings.setEnabled(gutterIconDescriptor, true);

      // Now that it's enabled, nothing should be thrown.
      findHighlightInfoWithGutterRenderer("src/p1/p2/DrawableTest.java", "R.drawable.ic_launcher", PsiReferenceExpression.class);
    } finally {
      settings.resetEnabled(gutterIconDescriptor);
    }
  }

  @NotNull
  private String getFrameworkResourcesPath() {
    IAndroidTarget target = ConfigurationManager.getOrCreateInstance(myModule).getProjectTarget();
    assertThat(target).isNotNull();
    return target.getPath(IAndroidTarget.RESOURCES).toString();
  }

  @Nullable
  private static VirtualFile getNavigationTarget(@NotNull HighlightInfo highlightInfo) {
    assertThat(highlightInfo.getGutterIconRenderer()).isNotNull();
    assertThat(highlightInfo.getGutterIconRenderer()).isInstanceOf(GutterIconRenderer.class);
    AnAction action = ((GutterIconRenderer)highlightInfo.getGutterIconRenderer()).getClickAction();
    assertThat(action).isInstanceOf(NavigationTargetProvider.class);
    assert action != null;
    return ((NavigationTargetProvider)action).getNavigationTarget();
  }

  private void checkHighlightInfoImage(@NotNull HighlightInfo highlightInfo, @NotNull String expectedImage) throws IOException {
    File expected = new File(expectedImage);
    if (!expected.isAbsolute()) {
      expected = new File(getTestDataPath(), expectedImage);
    }
    checkHighlightInfoImage(highlightInfo, Objects.requireNonNull(VfsUtil.findFileByIoFile(expected, false)));
  }

  private void checkHighlightInfoImage(@NotNull HighlightInfo highlightInfo, @NotNull VirtualFile expectedImage) throws IOException {
    assertThat(highlightInfo.getGutterIconRenderer()).isNotNull();
    assertThat(highlightInfo.getGutterIconRenderer()).isInstanceOf(GutterIconRenderer.class);
    GutterIconRenderer renderer = (GutterIconRenderer)highlightInfo.getGutterIconRenderer();
    assertThat(renderer).isNotNull();
    Icon icon = renderer.getIcon();
    BufferedImage image = TestRenderingUtils.getImageFromIcon(icon);

    // Go through the same process as the real annotator, to handle retina correctly.
    BufferedImage baselineImage = TestRenderingUtils.getImageFromIcon(
      Objects.requireNonNull(GutterIconCache.getInstance(getProject()).getIcon(expectedImage, null, myFacet)));
    assertImageSimilar(getName(), ImageDiffUtil.convertToARGB(baselineImage), image, 5.); // 5% difference allowed.
  }

  private static void checkHighlightInfoColor(@NotNull HighlightInfo highlightInfo, @NotNull Color expectedColor) {
    assertThat(highlightInfo.getGutterIconRenderer()).isNotNull();
    assertThat(highlightInfo.getGutterIconRenderer()).isInstanceOf(GutterIconRenderer.class);
    GutterIconRenderer renderer = (GutterIconRenderer)highlightInfo.getGutterIconRenderer();
    assertThat(renderer).isNotNull();
    Icon icon = renderer.getIcon();
    assertThat(icon).isInstanceOf(ColorIcon.class);
    ColorIcon colorIcon = (ColorIcon)icon;
    Color color = colorIcon.getIconColor();
    assertThat(color).isEqualTo(expectedColor);
  }

  private static void checkHighlightInfoColors(@NotNull HighlightInfo highlightInfo, @NotNull List<Color> expectedColors) {
    assertThat(highlightInfo.getGutterIconRenderer()).isNotNull();
    assertThat(highlightInfo.getGutterIconRenderer()).isInstanceOf(GutterIconRenderer.class);
    GutterIconRenderer renderer = (GutterIconRenderer)highlightInfo.getGutterIconRenderer();
    assertThat(renderer).isNotNull();
    Icon icon = renderer.getIcon();
    assertThat(icon).isInstanceOf(MultipleColorIcon.class);
    MultipleColorIcon colorIcon = (MultipleColorIcon)icon;
    List<Color> color = colorIcon.getColors();
    assertThat(color).isEqualTo(expectedColors);
  }

  @NotNull
  private HighlightInfo findHighlightInfo(@NotNull String path, @NotNull String target, @NotNull Class<? extends PsiElement> elementClass) {
    String finalTarget = getTargetWithoutCaret(target);
    return findHighlightInfos(path, target, elementClass)
      .findFirst()
      .orElseThrow(() -> new NoSuchElementException("HighlightInfo does not exist for " + finalTarget));
  }

  @NotNull
  private HighlightInfo findHighlightInfoWithGutterRenderer(@NotNull String path,
                                                            @NotNull String target,
                                                            @NotNull Class<? extends PsiElement> elementClass) {
    String finalTarget = getTargetWithoutCaret(target);
    return findHighlightInfos(path, target, elementClass)
      .filter(info -> info.getGutterIconRenderer() != null)
      .findFirst()
      .orElseThrow(() -> new NoSuchElementException("HighlightInfo does not exist for " + finalTarget));
  }

  @NotNull
  private Stream<HighlightInfo> findHighlightInfos(@NotNull String path,
                                                   @NotNull String target,
                                                   @NotNull Class<? extends PsiElement> elementClass) {
    int caretOffset = getCaretOffset(target);
    String finalTarget = getTargetWithoutCaret(target);

    PsiFile psiFile = getPsiFile(path);
    String source = psiFile.getText();
    int dot = source.indexOf(finalTarget);
    assertThat(dot).isNotEqualTo(-1);
    dot += caretOffset;
    PsiElement element = PsiTreeUtil.findElementOfClassAtOffset(psiFile, dot, elementClass, false);
    assertThat(element).isNotNull();

    myFixture.openFileInEditor(psiFile.getVirtualFile());
    Document document = myFixture.getEditor().getDocument();
    int expectedOffset = document.getLineNumber(dot);
    return myFixture.doHighlighting().stream().filter(info -> document.getLineNumber(info.startOffset) == expectedOffset);
  }

  private List<HighlightInfo> findAllHighlightInfo(@NotNull String path) {
    PsiFile file = getPsiFile(path);
    myFixture.openFileInEditor(file.getVirtualFile());
    return myFixture.doHighlighting();
  }

  @NotNull
  private PsiFile getPsiFile(@NotNull String path) {
    VirtualFile virtualFile = myFixture.findFileInTempDir(path);
    assertThat(virtualFile).isNotNull();
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertThat(psiFile).isNotNull();
    return psiFile;
  }

  private static int getCaretOffset(@NotNull String target) {
    int caretOffset = target.indexOf('|');
    if (caretOffset < 0) {
      caretOffset = 0;
    }
    return caretOffset;
  }

  @NotNull
  private static String getTargetWithoutCaret(@NotNull String target) {
    return target.substring(getCaretOffset(target));
  }
}
