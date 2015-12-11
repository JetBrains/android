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

public class AndroidAnimatorDomTest extends AndroidDomTest {
  public AndroidAnimatorDomTest() {
    super(false, "dom/animator");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/animator/" + testFileName;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  public void testRootCompletion() throws Throwable {
    toTestCompletion("root.xml", "root_after.xml");
  }

  public void testTagNames() throws Throwable {
    toTestCompletion("tn.xml", "tn_after.xml");
  }

  public void testHighlighting() throws Throwable {
    doTestHighlighting("hl.xml");
  }

  public void testHighlighting1() throws Throwable {
    copyFileToProject("myInterpolator.xml", "res/interpolator/myInterpolator.xml");
    doTestHighlighting("hl1.xml");
  }

  public void testAttributeNames() throws Throwable {
    toTestCompletion("an1.xml", "an1_after.xml");
    toTestCompletion("an2.xml", "an2_after.xml");
  }

  public void testAttributeValues() throws Throwable {
    toTestCompletion("av.xml", "av_after.xml");
  }

  // An example of state list animator is highlighted without errors
  public void testSelectorAnimationHighlighting() throws Throwable {
    doTestHighlighting("selectorAnimationHighlighting.xml");
  }

  // <selector> top-level tag in anim resource files is completed
  public void testSelectorTagCompletion() throws Throwable {
    toTestCompletion("selector_tag_completion.xml", "selector_tag_completion_after.xml");
  }

  // Inside <selector> tag, <item> tags are completed
  public void testItemTagCompletion() throws Throwable {
    toTestCompletion("item_tag_completion.xml", "item_tag_completion_after.xml");
  }

  // Inside <item> tag, "state_" attributes are completed
  public void testItemAttributeCompletion() throws Throwable {
    toTestCompletion("item_attribute_completion.xml", "item_attribute_completion_after.xml");
  }

  // <objectAnimator> is completed as a possible subtag of <item> tag
  public void testItemContentsCompletion() throws Throwable {
    toTestCompletion("item_contents_completion.xml", "item_contents_completion_after.xml");
  }

  // <objectAnimator> is recognized as root tag
  public void testObjectAnimatorHighlighting() throws Throwable {
    doTestHighlighting("object_animator_root.xml");
  }

  // <animator> is recognized as root tag
  public void testAnimatorHighlighting() throws Throwable {
    doTestHighlighting("animator_root.xml");
  }

  // <propertyValuesHolder> and <keyframe> tags are recognized
  public void testKeyframeHighlighting() throws Throwable {
    // This test contents is commented out because it fails due to test SDK not having
    // "Keyframe" styleable
    // TODO: update test SDK to make this test pass
    //doTestHighlighting("keyframes.xml");
  }
}
