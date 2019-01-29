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

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DragSource;

/**
 * Transfer Handler class for managing drag and drop events.
 */
public final class ProfilerStageTransferHandler extends TransferHandler {
  @Override
  protected Transferable createTransferable(JComponent c) {
    JList<DragAndDropModelListElement> source = (JList<DragAndDropModelListElement>)c;
    // Update visuals on tree that we are moving an object.
    c.getRootPane().getGlassPane().setVisible(true);

    // Boilerplate transferable object, returns a single selected value from the list.
    return new Transferable() {
      @Override
      public DataFlavor[] getTransferDataFlavors() {
        DataFlavor flavor = new ObjectDataFlavor(source.getSelectedValue());
        return new DataFlavor[]{flavor};
      }

      @Override
      public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor instanceof ObjectDataFlavor;
      }

      @Override
      public Object getTransferData(DataFlavor flavor)
        throws UnsupportedFlavorException {
        if (isDataFlavorSupported(flavor)) {
          return ((ObjectDataFlavor)flavor).getObject();
        }
        else {
          throw new UnsupportedFlavorException(flavor);
        }
      }
    };
  }

  @Override
  public boolean canImport(TransferSupport info) {
    return info.isDrop() && info.isDataFlavorSupported(info.getDataFlavors()[0]);
  }

  @Override
  public int getSourceActions(JComponent c) {
    Component glassPane = c.getRootPane().getGlassPane();
    glassPane.setCursor(DragSource.DefaultMoveDrop);
    return MOVE;
  }

  @Override
  public boolean importData(TransferSupport info) {
    JList target = (JList)info.getComponent();
    target.getRootPane().getGlassPane().setVisible(false);
    boolean dropLocationIsJListDropLocation = info.getDropLocation() instanceof JList.DropLocation;
    boolean modeIsDragAndDropListModel = target.getModel() instanceof DragAndDropListModel;

    // If our drop location, or model are not valid early out.
    if (!dropLocationIsJListDropLocation || !modeIsDragAndDropListModel) {
      return false;
    }

    JList.DropLocation dropLocation = (JList.DropLocation)info.getDropLocation();
    DragAndDropListModel<DragAndDropModelListElement> model = (DragAndDropListModel<DragAndDropModelListElement>)target.getModel();
    // We use the object because if a new thread gets added while we are moving, the index could be wrong.
    DragAndDropModelListElement sourceObject = ((ObjectDataFlavor)info.getDataFlavors()[0]).getObject();
    int max = model.getSize();
    int index = dropLocation.getIndex();
    index = index < 0 ? max : index; // If it is out of range, it is appended to the end
    index = Math.min(index, max);
    model.moveElementTo(sourceObject, index);
    return true;
  }

  private static final class ObjectDataFlavor extends DataFlavor {
    DragAndDropModelListElement myObject;

    /**
     * Constructs a new ObjectDataFlavor.  This constructor is provided only for the purpose of supporting the
     * Externalizable interface.  It is not intended for public (client) use.
     */
    public ObjectDataFlavor() {
    }

    /**
     * Constructs a new ObjectDataFlavor populated with a {@link DragAndDropModelListElement}.
     * @param object The internal object to pass between drag and drop locations.
     */
    public ObjectDataFlavor(DragAndDropModelListElement object) {
      super(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + ObjectDataFlavor.class.getName(),
            null /* prevents us from needing to handle a ClassNotFoundException */);
      myObject = object;
    }

    public DragAndDropModelListElement getObject() {
      return myObject;
    }
  }
}