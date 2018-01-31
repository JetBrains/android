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
package com.android.tools.profilers;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * This class is required for controls that want to implement drag and drop using the {@link DragAndDropListModel}.
 * This is required because of a bug in java where on the drop event we update the model and a firePropertyChange event gets triggered
 * with a null value. The suggested work around in a bug on the java site leads to a native exception and causes jank.
 * https://bugs.java.com/view_bug.do?bug_id=4760426
 */
public class DragAndDropList<T extends DragAndDropModelListElement> extends JList<T> {

  public DragAndDropList(@NotNull DragAndDropListModel<T> dataModel) {
    super(dataModel);
    setTransferHandler(new ProfilerStageTransferHandler());
    setDropMode(DropMode.INSERT);
    // Need to not hardcode this as our test will throw an exception if we set this true in test.
    setDragEnabled(!GraphicsEnvironment.isHeadless());
  }

  @Override
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    // filter property change of "dropLocation" with newValue==null,
    // since this will result in a NPE in BasicTreeUI.getDropLineRect(...)
    if(newValue!=null || !"dropLocation".equals(propertyName)) {
      super.firePropertyChange(propertyName, oldValue, newValue);
    }
  }
}
