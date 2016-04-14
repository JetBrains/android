/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.dom.animator.fileDescriptions;

import com.android.resources.ResourceFolderType;
import org.jetbrains.android.dom.AbstractSingleRootFileDescription;
import org.jetbrains.android.dom.animator.Selector;

/**
 * Documentation:
 *   https://developer.android.com/training/material/animations.html#ViewState
 *
 * Framework code: AnimatorInflater#loadStateListAnimator
 *
 * Framework code uses "anim" as folder type for animator state lists, but
 * (as described in {@link org.jetbrains.android.dom} package documentation)
 * it's used only for generating error message. Given that contents of "item"
 * tags should be animators that are recommended to be defined in "animator"
 * folder, it makes more sense to use "animator" folder for state lists as well.
 *
 * Documentation on which folder is preferred for state lists is yet to be found.
 */
public class AnimatorStateListDomFileDescription extends AbstractSingleRootFileDescription<Selector> {
  public AnimatorStateListDomFileDescription() {
    super(Selector.class, "selector", ResourceFolderType.ANIMATOR);
  }
}
