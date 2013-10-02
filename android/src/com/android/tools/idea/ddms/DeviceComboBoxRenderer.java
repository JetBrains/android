package com.android.tools.idea.ddms;

import com.android.ddmlib.IDevice;
import com.android.utils.Pair;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import icons.AndroidIcons;

import javax.swing.*;
import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
public class DeviceComboBoxRenderer extends ColoredListCellRenderer {
  @Override
  protected void customizeCellRenderer(JList list,
                                       Object value,
                                       int index,
                                       boolean selected,
                                       boolean hasFocus) {
    if (value instanceof String) {
      append((String)value, SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
    else if (value instanceof IDevice) {
      IDevice d = (IDevice)value;
      setIcon(d.isEmulator() ? AndroidIcons.Ddms.Emulator2 : AndroidIcons.Ddms.RealDevice);
      List<Pair<String, SimpleTextAttributes>> components = DevicePanel.renderDeviceName(d);
      for (Pair<String, SimpleTextAttributes> c : components) {
        append(c.getFirst(), c.getSecond());
      }
    }
    else if (value == null) {
      append("[none]", SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }
}
