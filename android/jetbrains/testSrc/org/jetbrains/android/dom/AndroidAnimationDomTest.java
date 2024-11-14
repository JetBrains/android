// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.android.dom;

import com.android.SdkConstants;

public class AndroidAnimationDomTest extends AndroidDomTestCase {
  public AndroidAnimationDomTest() {
    super("dom/anim");
  }

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/anim/" + testFileName;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  public void testRootCompletion() throws Throwable {
    toTestCompletion("root.xml", "root_after.xml");
  }

  public void testHighlighting() throws Throwable {
    doTestHighlighting("hl.xml");
  }

  // Test highlighting for an example from
  // http://developer.android.com/guide/topics/graphics/view-animation.html#tween-animation
  public void testNestedSetsHighlighting() throws Throwable {
    doTestHighlighting("nested_sets.xml");
  }

  // Test XML highlighting when root tag is not "set"
  public void testScaleRootHighlighting() throws Throwable {
    doTestHighlighting("scale_root.xml");
  }

  // Previously, <animation-list> was registered as possible root tag in anim/ folder
  // However, that's not the case as http://developer.android.com/guide/topics/resources/animation-resource.html#Frame
  // recommends putting it in "drawable" resource folder. This unit test is a regression check
  // that <animation-list> is marked as an error
  public void testAnimationListHighlighting() throws Throwable {
    doTestHighlighting("animation_list.xml");
  }

  // Interpolators are highlighted correctly, including attributes
  public void testAccelerateInterpolatorHighlighting() throws Throwable {
    doTestHighlighting("accelerate_interpolator.xml");
  }

  // <layoutAnimation> tag is highlighted correctly, including attributes
  public void testLayoutAnimationHighlighting() throws Throwable {
    doTestHighlighting("layout_animation.xml");
  }

  // <gridLayoutAnimation> tag is highlighted correctly, including attributes
  public void testGridLayoutAnimationHighlighting() throws Throwable {
    doTestHighlighting("grid_layout_animation.xml");
  }

  // Completion inside <set> tag used to provide all the possible root tags for anim/
  // resources files and more. This is a regression test that only available tags are completed
  public void testTagCompletionInsideSet() throws Throwable {
    doTestCompletionVariantsContains("completion_inside_set.xml", "set", "alpha", "rotate", "translate", "scale");
  }

  public void testChildren() throws Throwable {
    toTestCompletion("tn.xml", "tn_after.xml");
  }

  public void testAlphaAttributes() throws Throwable {
    // Regression test for b/224990755
    doTestCompletionVariantsContains(
      "completion_alpha_attributes.xml",
      "android:zAdjustment", // Attribute from Animation
      "android:fromAlpha" // Attribute from AlphaAnimation
    );
  }

  public void testRotateAttributes() throws Throwable {
    // Regression test for b/224990755
    doTestCompletionVariantsContains(
      "completion_rotate_attributes.xml",
      "android:zAdjustment", // Attribute from Animation
      "android:fromDegrees" // Attribute from RotateAnimation
    );
  }

  public void testScaleAttributes() throws Throwable {
    // Regression test for b/224990755
    doTestCompletionVariantsContains(
      "completion_scale_attributes.xml",
      "android:zAdjustment", // Attribute from Animation
      "android:fromXScale" // Attribute from ScaleAnimation
    );
  }

  public void testSetAttributes() throws Throwable {
    // Regression test for b/224990755
    doTestCompletionVariantsContains(
      "completion_set_attributes.xml",
      "android:zAdjustment", // Attribute from Animation
      "android:shareInterpolator" // Attribute from AnimationSet
    );
  }

  public void testTranslateAttributes() throws Throwable {
    // Regression test for b/224990755
    doTestCompletionVariantsContains(
      "completion_translate_attributes.xml",
      "android:zAdjustment", // Attribute from Animation
      "android:fromXDelta" // Attribute from TranslateAnimation
    );
  }
}
