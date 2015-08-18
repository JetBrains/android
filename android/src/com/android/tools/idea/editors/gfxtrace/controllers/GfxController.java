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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.EnumInfoCache;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.ScrubberLabelData;
import com.android.tools.idea.editors.gfxtrace.rpc.AtomStream;
import com.android.tools.idea.editors.gfxtrace.rpc.Schema;
import com.android.tools.idea.editors.gfxtrace.schema.AtomReader;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;
import java.util.List;

public interface GfxController {
  String SELECT_CAPTURE = "Select a capture";

  void startLoad();

  void commitData(@NotNull GfxContextChangeState state);

  void clear();

  void clearCache();

  class CaptureChangeState {
    public AtomStream myAtomStream;
    public Schema mySchema;

    public CaptureChangeState() {
    }

    public CaptureChangeState(@NotNull AtomStream atomStream, @NotNull Schema schema) {
      myAtomStream = atomStream;
      mySchema = schema;
    }
  }

  class GfxContextChangeState {
    public CaptureChangeState myCaptureChangeState;
    public AtomReader myAtomReader;
    public EnumInfoCache myEnumInfoCache;
    public TreeNode myTreeRoot;
    public List<ScrubberLabelData> myScrubberList;

    public GfxContextChangeState() {
      myCaptureChangeState = new CaptureChangeState();
    }
  }
}
