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
package org.jetbrains.android;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.GutterIconCache;
import com.android.tools.idea.rendering.TestRenderingUtils;
import com.google.common.collect.Iterables;
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class AndroidColorAnnotatorTest extends AndroidTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject("annotator/colors.xml", "res/values/colors1.xml");
    myFixture.copyFileToProject("annotator/colors.xml", "res/values/colors2.xml");
    myFixture.copyFileToProject("annotator/selector.xml", "res/color/selector.xml");
    myFixture.copyFileToProject("annotator/color_test.xml", "res/layout/color_test.xml");
    myFixture.copyFileToProject("annotator/ColorTest.java", "src/p1/p2/ColorTest.java");
    myFixture.copyFileToProject("annotator/values.xml", "res/values/values.xml");
    myFixture.copyFileToProject("annotator/ic_tick.xml", "res/drawable/ic_tick.xml");
    myFixture.copyFileToProject("render/imageutils/actual.png", "res/drawable-mdpi/drawable1.png");
  }

  public void testColorInValues1() {
    // Color definition in a values file
    Annotation annotation = findAnnotation("res/values/colors1.xml", "3F51B5", XmlTag.class);
    checkAnnotationColor(annotation, new Color(63, 81, 181));
  }

  public void testColorInValues2() {
    // Color definition in a values file
    Annotation annotatation = findAnnotation("res/values/colors2.xml", "303F9F", XmlTag.class);
    checkAnnotationColor(annotatation, new Color(0x303F9F));
  }

  public void testColorStateListInValues() {
    // Color definition in a color state list file
    Annotation annotation = findAnnotation("res/color/selector.xml", "ffff0000", XmlAttributeValue.class);
    checkAnnotationColor(annotation, new Color(255, 0, 0));
    annotation = findAnnotation("res/color/selector.xml", "#ff00ff00", XmlAttributeValue.class);
    checkAnnotationColor(annotation, new Color(0, 255, 0));
  }

  public void testColorReferenceInXml1() {
    // Reference to a color from a layout file
    Annotation annotation = findAnnotation("res/layout/color_test.xml", "@color/color1", XmlAttributeValue.class);
    checkAnnotationColor(annotation, new Color(63, 81, 181));
  }

  public void testColorReferenceInXml2() {
    // Reference to a color from a layout file
    Annotation annotation = findAnnotation("res/layout/color_test.xml", "@color/color2", XmlAttributeValue.class);
    checkAnnotationColor(annotation, new Color(0x303F9F));
  }

  public void testColorReferenceInXml3() {
    // Reference to a selector color from a layout file
    Annotation annotation = findAnnotation("res/layout/color_test.xml", "@color/selector", XmlAttributeValue.class);
    checkAnnotationColor(annotation, new Color(0, 255, 0));
  }

  public void testIconReferenceInXml() throws IOException {
    // Reference to an icon from a layout file
    Annotation annotation = findAnnotation("res/layout/color_test.xml", "@drawable/drawable1", XmlAttributeValue.class);
    checkAnnotationImage(annotation, "annotator/drawable1_thumbnail.png");
  }

  public void testVectorReferenceInXml() throws IOException {
    // Reference to vector a color from a layout file
    Annotation annotation = findAnnotation("res/layout/color_test.xml", "@drawable/ic_tick", XmlAttributeValue.class);
    checkAnnotationImage(annotation, "annotator/ic_tick_thumbnail.png");
  }

  public void testFrameworkDrawable() throws Exception {
    String layoutPath = "res/layout/color_test.xml";
    Annotation annotation = findAnnotation(layoutPath, "@android:drawable/ic_lock_lock", XmlAttributeValue.class);

    // Find the icon we should be rendering in layoutlib data
    Configuration configuration = AndroidColorAnnotator.pickConfiguration(myFacet, myModule, getPsiFile(layoutPath));
    assertThat(configuration).isNotNull();
    IAndroidTarget target = configuration.getTarget();
    assertThat(target).isNotNull();
    String resPath = target.getPath(IAndroidTarget.RESOURCES);

    checkAnnotationImage(annotation, resPath + "/drawable-ldpi/ic_lock_lock_alpha.png");
  }

  private void checkAnnotationImage(Annotation first, String imagePath) throws IOException {
    GutterIconRenderer renderer = first.getGutterIconRenderer();
    assertThat(renderer).isNotNull();
    Icon icon = renderer.getIcon();
    BufferedImage image = TestRenderingUtils.getImageFromIcon(icon);

    File expected = new File(imagePath);
    if (!expected.isAbsolute()) {
      expected = new File(getTestDataPath(), imagePath);
    }
    // Go through the same process as the real annotator, to handle retina correctly.
    BufferedImage baselineImage = TestRenderingUtils.getImageFromIcon(GutterIconCache.getInstance().getIcon(expected.getPath(), null));
    ImageDiffUtil.assertImageSimilar(getName(), ImageDiffUtil.convertToARGB(baselineImage), image, 5.0); // 5% difference allowed
  }

  private static void checkAnnotationColor(Annotation annotation, Color expectedColor) {
    GutterIconRenderer renderer = annotation.getGutterIconRenderer();
    assertThat(renderer).isNotNull();
    Icon icon = renderer.getIcon();
    assertThat(icon).isInstanceOf(ColorIcon.class);
    ColorIcon colorIcon = (ColorIcon)icon;
    Color color = colorIcon.getIconColor();
    assertThat(color).isEqualTo(expectedColor);
  }

  @NotNull
  private Annotation findAnnotation(String path, String target, Class<? extends PsiElement> elementClass) {
    PsiFile psiFile = getPsiFile(path);
    int caretOffset = target.indexOf('|');
    if (caretOffset != -1) {
      target = target.substring(0, caretOffset) + target.substring(caretOffset + 1);
    } else {
      caretOffset = 0;
    }
    String source = psiFile.getText();
    int dot = source.indexOf(target);
    assertThat(dot).isNotEqualTo(-1);
    dot += caretOffset;
    PsiElement element = PsiTreeUtil.findElementOfClassAtOffset(psiFile, dot, elementClass, false);
    assertThat(element).isNotNull();

    AnnotationHolderImpl holder = new AnnotationHolderImpl(new AnnotationSession(psiFile));
    AndroidColorAnnotator annotator = new AndroidColorAnnotator();
    annotator.annotate(element, holder);
    return Iterables.getOnlyElement(holder);
  }

  @NotNull
  private PsiFile getPsiFile(String path) {
    VirtualFile virtualFile = myFixture.findFileInTempDir(path);
    assertThat(virtualFile).isNotNull();
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertThat(psiFile).isNotNull();
    return psiFile;
  }
}
