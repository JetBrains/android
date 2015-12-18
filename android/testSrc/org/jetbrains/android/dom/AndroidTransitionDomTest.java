/*
 * Copyright (C) 2014 The Android Open Source Project
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

package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.android.resources.ResourceConstants;

public class AndroidTransitionDomTest extends AndroidDomTest {
  public AndroidTransitionDomTest() {
    super(false, "dom/transition");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/" + ResourceConstants.FD_RES_TRANSITION + "/" + testFileName;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  public void testRootCompletion() throws Throwable {
    toTestCompletion("root.xml", "root_after.xml");
  }

  public void testTransitionManagerCompletion1() throws Throwable {
    toTestCompletion("transitionManager1.xml", "transitionManager1_after.xml");
  }

  public void testTransitionManagerCompletion2() throws Throwable {
    toTestCompletion("transitionManager2.xml", "transitionManager2_after.xml");
  }

  public void testTransitionManagerCompletion3() throws Throwable {
    copyFileToProject("transition_scene.xml", "res/layout/transition_scene1.xml");
    toTestCompletion("transitionManager3.xml", "transitionManager3_after.xml");
  }

  public void testTransitionManagerCompletion4() throws Throwable {
    toTestCompletion("transitionManager4.xml", "transitionManager4_after.xml");
  }

  public void testTransitionManagerCompletion5() throws Throwable {
    copyFileToProject("transition_scene.xml", "res/layout/the_transition_scene_result.xml");
    toTestCompletion("transitionManager5.xml", "transitionManager5_after.xml");
  }

  public void testTransitionManagerCompletion6() throws Throwable {
    toTestCompletion("transitionManager6.xml", "transitionManager6_after.xml");
  }

  public void testTransitionManagerCompletion7() throws Throwable {
    copyFileToProject("changebounds.xml", "res/transition/changebounds.xml");
    toTestCompletion("transitionManager7.xml", "transitionManager7_after.xml");
  }

  public void testTagNames() throws Throwable {
    toTestCompletion("tn.xml", "tn_after.xml");
  }

  public void testHighlighting() throws Throwable {
    copyFileToProject("transition_scene.xml", "res/layout/transition_scene1.xml");
    copyFileToProject("transition_scene.xml", "res/layout/transition_scene2.xml");
    copyFileToProject("changebounds.xml", "res/transition/changebounds.xml");
    doTestHighlighting("hl.xml");
  }

  public void testHighlighting2() throws Throwable {
    doTestHighlighting("hl2.xml");
  }

  public void testHighlighting3() throws Throwable {
    doTestHighlighting("hl3.xml");
  }

  // The test checks that <transition> tag can be inside <transitionSet> and can have "class" attribute
  // Regression test for http://b.android.com/78390
  public void testHighlighting4() throws Throwable {
    doTestHighlighting("hl4.xml");
  }

  public void testAttributeNames() throws Throwable {
    toTestCompletion("an.xml", "an_after.xml");
  }

  public void testAttributeValues() throws Throwable {
    toTestCompletion("av.xml", "av_after.xml");
  }
}
