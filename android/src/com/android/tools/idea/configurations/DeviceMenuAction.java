/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import com.android.annotations.Nullable;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import icons.AndroidIcons;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceMenuAction extends FlatComboAction {
  private static final String NEXUS = "Nexus";       //$NON-NLS-1$
  private static final String GENERIC = "Generic";   //$NON-NLS-1$
  private static final Pattern PATTERN = Pattern.compile("(\\d+\\.?\\d*)in (.+?)( \\(.*Nexus.*\\))?"); //$NON-NLS-1$

  private final ConfigurationToolBar myConfigurationToolBar;

  public DeviceMenuAction(@NotNull ConfigurationToolBar configurationToolBar) {
    myConfigurationToolBar = configurationToolBar;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("The virtual device to render the layout with");
    presentation.setIcon(AndroidIcons.Display);
    updatePresentation(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    updatePresentation(e.getPresentation());
  }

  private void updatePresentation(Presentation presentation) {
    Configuration configuration = myConfigurationToolBar.getConfiguration();
    boolean visible = configuration != null;
    if (visible) {
      String label = getDeviceLabel(configuration.getDevice(), true);
      presentation.setText(label);
    }
    if (visible != presentation.isVisible()) {
      presentation.setVisible(visible);
    }
  }

  /**
   * Returns a suitable label to use to display the given device
   *
   * @param device the device to produce a label for
   * @param brief  if true, generate a brief label (suitable for a toolbar
   *               button), otherwise a fuller name (suitable for a menu item)
   * @return the label
   */
  public static String getDeviceLabel(@Nullable Device device, boolean brief) {
    if (device == null) {
      return "";
    }
    String name = device.getName();

    if (brief) {
      // Produce a really brief summary of the device name, suitable for
      // use in the narrow space available in the toolbar for example
      int nexus = name.indexOf("Nexus"); //$NON-NLS-1$
      if (nexus != -1) {
        int begin = name.indexOf('(');
        if (begin != -1) {
          begin++;
          int end = name.indexOf(')', begin);
          if (end != -1) {
            return name.substring(begin, end).trim();
          }
        }
      }
    }

    return name;
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup(null, true);
    Configuration configuration = myConfigurationToolBar.getConfiguration();
    if (configuration == null) {
      return group;
    }
    AndroidPlatform platform = myConfigurationToolBar.getPlatform();
    if (platform == null) {
      return group;
    }

    Device current = configuration.getDevice();
    List<Device> deviceList = configuration.getConfigurationManager().getDevices();

    // Group the devices by manufacturer, then put them in the menu.
    // If we don't have anything but Nexus devices, group them together rather than
    // make many manufacturer submenus.
    boolean haveNexus = false;
    if (!deviceList.isEmpty()) {
      Map<String, List<Device>> manufacturers = new TreeMap<String, List<Device>>();
      for (Device device : deviceList) {
        List<Device> devices;
        if (isNexus(device)) {
          haveNexus = true;
        }
        if (manufacturers.containsKey(device.getManufacturer())) {
          devices = manufacturers.get(device.getManufacturer());
        }
        else {
          devices = new ArrayList<Device>();
          manufacturers.put(device.getManufacturer(), devices);
        }
        devices.add(device);
      }
      List<Device> nexus = new ArrayList<Device>();
      List<Device> generic = new ArrayList<Device>();
      if (haveNexus) {
        // Nexus
        for (List<Device> devices : manufacturers.values()) {
          for (Device device : devices) {
            if (isNexus(device)) {
              if (device.getManufacturer().equals(GENERIC)) {
                generic.add(device);
              }
              else {
                nexus.add(device);
              }
            }
            else {
              generic.add(device);
            }
          }
        }
      }

      if (!nexus.isEmpty()) {
        sortNexusList(nexus);
        for (final Device device : nexus) {
          group.add(new SetDeviceAction(getNexusLabel(device), device, current == device));
        }

        group.addSeparator();
      }

      // Generate the generic menu.
      Collections.reverse(generic);
      for (final Device device : generic) {
        group.add(new SetDeviceAction(getGenericLabel(device), device, current == device));
      }
    }

// TODO: Add multiconfiguration editing
//        @SuppressWarnings("unused")
//        MenuItem separator = new MenuItem(menu, SWT.SEPARATOR);
//
//        ConfigurationMenuListener.addTogglePreviewModeAction(menu,
//                "Preview All Screens", chooser, RenderPreviewMode.SCREENS);
//

    return group;
  }

  private static String getNexusLabel(Device d) {
    String name = d.getName();
    Screen screen = d.getDefaultHardware().getScreen();
    float length = (float)screen.getDiagonalLength();
    return String.format(Locale.US, "%1$s (%3$s\", %2$s)", name, getResolutionString(d), Float.toString(length));
  }

  private static String getGenericLabel(Device d) {
    // * Replace "'in'" with '"' (e.g. 2.7" QVGA instead of 2.7in QVGA)
    // * Use the same precision for all devices (all but one specify decimals)
    // * Add some leading space such that the dot ends up roughly in the
    //   same space
    // * Add in screen resolution and density
    String name = d.getName();
    if (name.equals("3.7 FWVGA slider")) {                        //$NON-NLS-1$
      // Fix metadata: this one entry doesn't have "in" like the rest of them
      name = "3.7in FWVGA slider";                              //$NON-NLS-1$
    }

    Matcher matcher = PATTERN.matcher(name);
    if (matcher.matches()) {
      String size = matcher.group(1);
      String n = matcher.group(2);
      int dot = size.indexOf('.');
      if (dot == -1) {
        size += ".0";
        dot = size.length() - 2;
      }
      for (int i = 0; i < 2 - dot; i++) {
        size = ' ' + size;
      }
      name = size + "\" " + n;
    }

    return String.format(Locale.US, "%1$s (%2$s)", name, getResolutionString(d));
  }

  @Nullable
  private static String getResolutionString(Device device) {
    Screen screen = device.getDefaultHardware().getScreen();
    return String.format(Locale.US, "%1$d \u00D7 %2$d: %3$s", // U+00D7: Unicode multiplication sign
                         screen.getXDimension(), screen.getYDimension(), screen.getPixelDensity().getResourceValue());
  }

  private static boolean isGeneric(Device device) {
    return device.getManufacturer().equals(GENERIC);
  }

  private static boolean isNexus(Device device) {
    return device.getName().contains(NEXUS);
  }

  private static void sortNexusList(List<Device> list) {
    Collections.sort(list, new Comparator<Device>() {
      @Override
      public int compare(Device device1, Device device2) {
        // Descending order of age
        return nexusRank(device2) - nexusRank(device1);
      }

      private int nexusRank(Device device) {
        String name = device.getName();
        if (name.endsWith(" One")) {     //$NON-NLS-1$
          return 1;
        }
        if (name.endsWith(" S")) {       //$NON-NLS-1$
          return 2;
        }
        if (name.startsWith("Galaxy")) { //$NON-NLS-1$
          return 3;
        }
        if (name.endsWith(" 7")) {       //$NON-NLS-1$
          return 4;
        }
        if (name.endsWith(" 10")) {       //$NON-NLS-1$
          return 5;
        }
        if (name.endsWith(" 4")) {       //$NON-NLS-1$
          return 6;
        }

        return 7;
      }
    });
  }

  private class SetDeviceAction extends ToggleAction {

    private final Device myDevice;
    private boolean mySelected;

    public SetDeviceAction(final String title, final Device device, final boolean select) {
      super(title);
      myDevice = device;
      mySelected = select;
      if (select) {
        getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
      }
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySelected;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySelected = state;
      if (state) {
        Configuration configuration = myConfigurationToolBar.getConfiguration();
        if (configuration != null) {
          configuration.setDevice(myDevice, true);
        }
      }
    }
  }
}
