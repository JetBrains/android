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

  public void testRootCompletion1() throws Throwable {
    toTestCompletion("root1.xml", "root1_after.xml");
  }

  public void testHighlighting() throws Throwable {
    doTestHighlighting("hl.xml");
  }

  public void testChildren() throws Throwable {
    toTestCompletion("tn.xml", "tn_after.xml");
  }

  // An example of statelist animator is highlighted without errors
  public void testSelectorAnimationHiglighting() throws Throwable {
    doTestHighlighting("selectorAnimationHighlighting.xml");
  }

  // <selector> top-level tag in anim resource files is completed
  public void testSelectorTagCompletion() throws Throwable {
    toTestCompletion("selector_tag_completion.xml", "selector_tag_completion_after.xml");
  }

  // Inside <selector> tag, <item> tags are autocompleted
  public void testItemTagCompletion() throws Throwable {
    toTestCompletion("item_tag_completion.xml", "item_tag_completion_after.xml");
  }

  // Inside <item> tag, "state_" attributes are autocompleted
  public void testItemAttributeCompletion() throws Throwable {
    toTestCompletion("item_attribute_completion.xml", "item_attribute_completion_after.xml");
  }

  // <objectAnimator> is completed as a possible subtag of <item> tag
  public void testItemContentsCompletion() throws Throwable {
    toTestCompletion("item_contents_completion.xml", "item_contents_completion_after.xml");
  }

}
