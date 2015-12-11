/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android.dom;

import com.android.SdkConstants;

public class AndroidAnimationDomTest extends AndroidDomTest {
  public AndroidAnimationDomTest() {
    super(false, "dom/anim");
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
    doTestCompletionVarinatsContains("completion_inside_set.xml", "set", "alpha", "rotate", "translate", "scale");
  }

  public void testChildren() throws Throwable {
    toTestCompletion("tn.xml", "tn_after.xml");
  }
}
