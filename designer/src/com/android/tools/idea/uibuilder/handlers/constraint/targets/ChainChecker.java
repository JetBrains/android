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

import static com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities.*;
import static com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities.ourBottomAttributes;
import static com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities.ourTopAttributes;

/**
 * Check if the component is in a chain and remembers it
 */
public class ChainChecker {
  private boolean myIsInHorizontalChain = false;
  private boolean myIsInVerticalChain = false;
  private SceneComponent myHorizontalChainHead;
  private SceneComponent myVerticalChainHead;

  protected boolean checkIsInChain(SceneComponent component) {
    myHorizontalChainHead = null;
    myVerticalChainHead = null;
    myIsInHorizontalChain = false;
    myIsInVerticalChain = false;
    if (isInChain(ourRightAttributes, ourLeftAttributes, component)
        || isInChain(ourLeftAttributes, ourRightAttributes, component)) {
      myIsInHorizontalChain = true;
    }
    if (myIsInHorizontalChain) {
      myHorizontalChainHead = findChainHead(component, ourLeftAttributes, ourRightAttributes);
    }

    if (isInChain(ourBottomAttributes, ourTopAttributes, component)
        || isInChain(ourTopAttributes, ourBottomAttributes, component)) {
      myIsInVerticalChain = true;
    }
    if (myIsInVerticalChain) {
      myVerticalChainHead = findChainHead(component, ourTopAttributes, ourBottomAttributes);
    }
    return myIsInHorizontalChain || myIsInVerticalChain;
  }

  public boolean isInHorizontalChain() { return myIsInHorizontalChain; }
  public boolean isInVerticalChain() { return myIsInVerticalChain; }
  public SceneComponent getHorizontalChainHead() { return myHorizontalChainHead; }
  public SceneComponent getVerticalChainHead() { return myVerticalChainHead; }
}
