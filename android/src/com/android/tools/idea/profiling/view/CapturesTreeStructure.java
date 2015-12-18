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
package com.android.tools.idea.profiling.view;

import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.android.tools.idea.profiling.capture.CaptureType;
import com.android.tools.idea.profiling.view.nodes.CaptureNode;
import com.android.tools.idea.profiling.view.nodes.CaptureRootNode;
import com.android.tools.idea.profiling.view.nodes.CaptureTypeNode;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

public class CapturesTreeStructure extends SimpleTreeStructure {
  @NotNull private final Project myProject;
  @NotNull CaptureRootNode myRoot;
  @NotNull Map<Capture, CaptureNode> myCaptureNodes = Maps.newHashMap();
  @NotNull Map<CaptureType, CaptureTypeNode> myTypeNodes = Maps.newHashMap();


  public CapturesTreeStructure(@NotNull Project project) {
    myProject = project;
    myRoot = new CaptureRootNode();
  }

  public void update() {
    CaptureService service = CaptureService.getInstance(myProject);
    myRoot.clear();
    Map<CaptureType, CaptureTypeNode> types = Maps.newHashMap();
    for (CaptureType type : service.getTypes()) {
      CaptureTypeNode typeNode = myTypeNodes.get(type);
      if (typeNode == null) {
        typeNode = new CaptureTypeNode(type);
      }
      types.put(type, typeNode);
      myRoot.addType(typeNode);
    }
    myTypeNodes = types;

    Map<Capture, CaptureNode> captures = Maps.newHashMap();
    for (Map.Entry<CaptureType, Collection<Capture>> entry : service.getCapturesByType().asMap().entrySet()) {
      CaptureTypeNode typeNode = myTypeNodes.get(entry.getKey());
      typeNode.clear();
      for (Capture capture : entry.getValue()) {
        CaptureNode captureNode = myCaptureNodes.get(capture);
        if (captureNode == null) {
          captureNode = new CaptureNode(myProject, capture);
        } else {
          captureNode.update();
        }
        captures.put(capture, captureNode);
        typeNode.addCapture(captureNode);
      }
    }
    myCaptureNodes = captures;
  }

  public CaptureNode getNode(Capture capture) {
    return myCaptureNodes.get(capture);
  }

  @Override
  public Object getRootElement() {
    return myRoot;
  }
}
