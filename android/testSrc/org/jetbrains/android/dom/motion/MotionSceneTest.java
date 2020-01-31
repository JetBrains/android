/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android.dom.motion;

import org.jetbrains.android.dom.AndroidDomTestCase;

public class MotionSceneTest extends AndroidDomTestCase {

  public MotionSceneTest() {
    super("dom/motion");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/xml/" + testFileName;
  }

  public void testMotionSceneSubTags() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "ConstraintSet", "Transition", "StateSet");
  }

  public void testTransitionSubTags() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "OnSwipe", "OnClick", "KeyFrameSet");
  }

  public void testConstraintSetSubTags() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "Constraint");
  }

  public void testConstraintSubTags() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "Layout", "PropertySet", "Transform", "Motion", "CustomAttribute");
  }

  public void testKeyFrameSetSubTags() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "KeyAttribute", "KeyCycle", "KeyPosition", "KeyTimeCycle", "KeyTrigger");
  }

  public void testKeyAttributeSubTags() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "CustomAttribute");
  }

  public void testKeyCycleSubTags() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "CustomAttribute");
  }

  public void testKeyTimeCycleSubTags() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "CustomAttribute");
  }

  public void testStateSetSubTags() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "State");
  }

  public void testStateSubTags() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "Variant");
  }

  // TODO: Add attribute completion tests after ConstraintLayout 2.0 are available in prebuilts

}
