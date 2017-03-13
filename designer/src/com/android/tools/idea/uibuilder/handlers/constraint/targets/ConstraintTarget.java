/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint.targets;

import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.target.BaseTarget;

import static com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities.*;

/**
 * Implements common checks on attributes of a ConstraintLayout child
 * TODO: removes, switch to ConstraintComponentUtilities instead
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public abstract class ConstraintTarget extends BaseTarget {

  protected boolean myIsInHorizontalChain = false;
  protected boolean myIsInVerticalChain = false;
  protected SceneComponent myHorizontalChainHead;
  protected SceneComponent myVerticalChainHead;

  protected boolean checkIsInChain() {
    myHorizontalChainHead = null;
    myVerticalChainHead = null;
    myIsInHorizontalChain = false;
    myIsInVerticalChain = false;
    if (isInChain(ourRightAttributes, ourLeftAttributes, myComponent)
        || isInChain(ourLeftAttributes, ourRightAttributes, myComponent)) {
      myIsInHorizontalChain = true;
    }
    if (myIsInHorizontalChain) {
      myHorizontalChainHead = findChainHead(myComponent, ourLeftAttributes, ourRightAttributes);
    }

    if (isInChain(ourBottomAttributes, ourTopAttributes, myComponent)
        || isInChain(ourTopAttributes, ourBottomAttributes, myComponent)) {
      myIsInVerticalChain = true;
    }
    if (myIsInVerticalChain) {
      myVerticalChainHead = findChainHead(myComponent, ourTopAttributes, ourBottomAttributes);
    }
    return myIsInHorizontalChain || myIsInVerticalChain;
  }
}
