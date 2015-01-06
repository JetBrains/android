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
package com.android.tools.idea.editors.gfxtrace.controllers.modeldata;

import com.android.tools.rpclib.rpc.AtomGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScrubberFrameData {
  private String myLabel;
  private AtomGroup myHierarchyReference;
  private long myAtomId;

  public String getLabel() {
    return myLabel;
  }

  @NotNull
  public ScrubberFrameData setLabel(@NotNull String label) {
    myLabel = label;
    return this;
  }

  public long getAtomId() {
    return myAtomId;
  }

  @NotNull
  public ScrubberFrameData setAtomId(long atomId) {
    myAtomId = atomId;
    return this;
  }

  @Nullable
  public AtomGroup getHierarchyReference() {
    return myHierarchyReference;
  }

  @NotNull
  public ScrubberFrameData setHierarchyReference(@NotNull AtomGroup reference) {
    myHierarchyReference = reference;
    return this;
  }
}
