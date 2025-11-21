/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom;

import static com.android.tools.idea.model.AndroidManifestIndexQueryUtils.queryPackageNameFromManifestIndex;
import static com.android.tools.idea.testing.AndroidTestUtils.moveCaret;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.io.IOException;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;

public class AndroidDrawableResourcesDomTest extends AndroidDomTestCase {
  public AndroidDrawableResourcesDomTest() {
    super("dom/drawable");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    copyFileToProject("myDrawable.png", "res/drawable/myDrawable.png");
    copyFileToProject("otherResource.xml", "res/values/strings1.xml");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/drawable/" + testFileName;
  }

  public void testStateListHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testStateListHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testStateListCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testStateListCompletion2() throws Throwable {
    doTestCompletion();
  }

  public void testStateListCompletion3() throws Throwable {
    doTestCompletion();
  }

  public void testStateListCompletion4() throws Throwable {
    doTestCompletion();
  }

  public void testStateListCompletion5() throws Throwable {
    doTestCompletion();
  }

  public void testStateListCompletion6() throws Throwable {
    doTestCompletion();
  }

  public void testStateListCompletion7() throws Throwable {
    copyFileToProject("colors.xml", "res/values/colors.xml");
    doTestCompletion();
  }

  public void testSelectorHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testItemListHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testBitmapHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testBitmapHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testBitmapHighlighting3() throws Throwable {
    doTestHighlighting();
  }

  public void testBitmapCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testBitmapCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testNinePatchHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testNinePatchHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testNinePatchCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testNinePatchCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testLayerListHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testLayerListHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testLayerListHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testLayerListCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testLayerListCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testLayerListCompletion3() throws Throwable {
    doTestCompletion();
  }

  public void testLayerListCompletion4() throws Throwable {
    doTestCompletion();
  }

  public void testLevelListHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testLevelListCompletion1() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testLevelListCompletion2() throws Throwable {
    doTestCompletion();
  }

  public void testLevelListCompletion3() throws Throwable {
    doTestCompletion();
  }

  public void testTransitionHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testTransitionHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testTransitionCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testTransitionCompletion2() throws Throwable {
    doTestCompletion();
  }

  public void testInsetHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testInsetHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testInsetCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testInsetCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testClipHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testClipCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testClipCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testScaleHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testScaleHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testScaleHighlighting3() throws Throwable {
    doTestHighlighting();
  }

  public void testScaleCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testScaleCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testShapeHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testShapeCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testShapeCompletion2() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "gradient", "solid", "size", "stroke", "padding", "corners");
  }

  public void testAnimationListHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testAnimationListHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testAnimationListCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testAnimationListCompletion2() throws Throwable {
    doTestCompletion();
  }

  public void testAnimationListCompletion3() throws Throwable {
    doTestCompletion();
  }

  public void testAnimatedRotateCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testAnimatedRotateCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testAnimatedRotateHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testAnimatedRotateHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testRotateCompletion1() throws Throwable {
    doTestCompletion();
  }

  public void testRotateCompletion2() throws Throwable {
    doTestOnlyDrawableReferences();
  }

  public void testRotateHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testRotateHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testIncorrectRootTag() throws Throwable {
    doTestHighlighting();
  }

  public void testAnimatedVectorHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testAnimatedVectorHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testVectorHighlighting1() throws Throwable {
    doTestHighlighting();
  }

  public void testVectorHighlighting2() throws Throwable {
    doTestHighlighting();
  }

  public void testAdaptiveIconCompletion() throws Throwable {
    doTestCompletion();
  }

  public void testAdaptiveIconCompletionSubtags() throws Throwable {
    doTestCompletion();
  }

  public void testAdaptiveIconCompletionSubtags1() throws Throwable {
    doTestCompletion();
  }

  public void testRootTagCompletion() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "selector", "bitmap", "nine-patch", "layer-list", "level-list", "transition",
                             "inset", "clip", "color", "scale", "shape", "animation-list", "animated-rotate", "rotate",
                             // API 21:
                             "ripple", "vector", "animated-vector", "animated-selector", "drawable",
                             // API 26:
                             "adaptive-icon",
                             // API 28:
                             "animated-image");
  }

  public void testCustomDrawableRootTagCompletion() throws Throwable {
    String packageName = queryPackageNameFromManifestIndex(AndroidFacet.getInstance(myFixture.getModule()));
    myFixture.addClass(String.format("package %s; public class MyDrawable extends android.graphics.drawable.Drawable {};", packageName));
    doTestCompletionVariantsContains(getTestName(true) + ".xml", packageName + ".MyDrawable");
  }

  public void testInlineClip() throws Throwable {
    doTestHighlighting();
  }

  public void testSrcCompat() throws Throwable {
    doTestHighlighting();
  }

  public void testIds() throws Throwable {
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
    copyFileToProject("ids.xml");
    final VirtualFile javaFile = copyFileToProject("IdsClass.java", "src/p1/p2/IdsClass.java");
    myFixture.configureFromExistingVirtualFile(javaFile);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testDrawableHighlighting() throws Throwable {
    final VirtualFile javaFile = copyFileToProject("TestDrawable.java", "src/p1/p2/TestDrawable.java");
    myFixture.configureFromExistingVirtualFile(javaFile);
    doTestHighlighting();
  }

  public void testDrawableCompletion1() throws Throwable {
    toTestFirstCompletion("drawableCompletion1.xml", "drawableCompletion1_after.xml");
  }

  public void testDrawableCompletion2() throws Throwable {
    final VirtualFile javaFile = copyFileToProject("TestDrawable.java", "src/p1/p2/TestDrawable.java");
    myFixture.configureFromExistingVirtualFile(javaFile);
    toTestFirstCompletion("drawableCompletion2.xml", "drawableCompletion2_after.xml");
  }

  // The test checks that <transition> tag can include <animated-vector> subtag and resolve any resource references therein
  // Test for http://b.android.com/37081228
  public void testAnimatedVectorHighlighting() throws Throwable {
    PsiFile file = myFixture.addFileToProject(
      "res/drawable/foo.xml",
      "<animated-selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
      "    <transition android:fromId=\"@+id/off\" android:toId=\"@+id/on\">\n" +
      "        <animated-vector android:drawable=\"@android:color/background_dark\">\n" +
      "            <target\n" +
      "                android:name=\"button\"\n" +
      "                android:animation=\"@android:animator/fade_in\" />\n" +
      "        </animated-vector>\n" +
      "    </transition>\n" +
      "\n" +
      "</animated-selector>");
    doTestHighlighting(file.getVirtualFile());
  }

  public void testAnimatedVectorTargetElement() {
    PsiFile file = myFixture.addFileToProject(
      "res/drawable/foo.xml",
      "<animated-selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
      "    <transition android:fromId=\"@+id/off\" android:toId=\"@+id/on\">\n" +
      "        <animated-vector android:drawable=\"@android:color/background_dark\">\n" +
      "            <target\n" +
      "                android:name=\"button\"\n" +
      "                android:animation=\"@android:animator/fade_in\" />\n" +
      "        </animated-vector>\n" +
      "    </transition>\n" +
      "\n" +
      "</animated-selector>");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    moveCaret(myFixture, "@android:a|nimator/fade_in");
    PsiElement elementAtCaret = myFixture.getElementAtCaret();
    assertThat(elementAtCaret).isInstanceOf(ResourceReferencePsiElement.class);
    assertThat(((ResourceReferencePsiElement)elementAtCaret).getResourceReference())
      .isEqualTo(new ResourceReference(ResourceNamespace.ANDROID, ResourceType.ANIMATOR, "fade_in"));
  }

  private void doTestOnlyDrawableReferences() throws IOException {
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    for (String s : lookupElementStrings) {
      if (!s.startsWith("@android") && !s.startsWith("@drawable") && !s.startsWith("@color")) {
        fail("Variant " + s + " shouldn't be there");
      }
    }
  }
}
