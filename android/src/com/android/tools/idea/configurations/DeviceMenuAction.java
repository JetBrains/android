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
import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.avdmanager.ModuleAvds;
import com.android.tools.idea.ddms.screenshot.DeviceArtPainter;
import com.android.tools.idea.npw.FormFactor;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

import static com.android.ide.common.rendering.HardwareConfigHelper.*;

public class DeviceMenuAction extends DropDownAction {
  private static final boolean LIST_RECENT_DEVICES = false;
  private final ConfigurationHolder myRenderContext;

  public DeviceMenuAction(@NotNull ConfigurationHolder renderContext) {
    super("", "Device in Editor", AndroidIcons.NeleIcons.VirtualDevice);
    myRenderContext = renderContext;
    Presentation presentation = getTemplatePresentation();
    updatePresentation(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    updatePresentation(e.getPresentation());
  }

  private void updatePresentation(Presentation presentation) {
    Configuration configuration = myRenderContext.getConfiguration();
    boolean visible = configuration != null;
    if (visible) {
      Device device = configuration.getDevice();
      String label = getDeviceLabel(device, true);
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
    String name = device.getDisplayName();

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
            if (name.equals("Nexus 7 (2012)")) {
              return "Nexus 7";
            }
            else {
              return name.substring(begin, end).trim();
            }
          }
        }
      }

      String skipPrefix = "Android ";
      name = StringUtil.trimStart(name, skipPrefix);
    }

    return name;
  }

  /**
   * Similar to {@link FormFactor#getFormFactor(Device)}
   * but (a) distinguishes between tablets and phones, and (b) uses the new Nele icons
   */
  public Icon getDeviceClassIcon(@Nullable Device device) {
    if (device != null) {
      if (isWear(device)) {
        return AndroidIcons.NeleIcons.Wear;
      }
      else if (isTv(device)) {
        return AndroidIcons.NeleIcons.Tv;
      }

      // Glass, Car not yet in the device list

      if (DeviceArtPainter.isTablet(device)) {
        return AndroidIcons.NeleIcons.Tablet;
      }
    }

    return AndroidIcons.NeleIcons.Phone;
  }

  @Override
  protected boolean updateActions() {
    removeAll();
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return true;
    }
    Device current = configuration.getDevice();
    ConfigurationManager configurationManager = configuration.getConfigurationManager();
    List<Device> deviceList = configurationManager.getDevices();

    if (LIST_RECENT_DEVICES) {
      List<Device> recent = configurationManager.getDevices();
      if (recent.size() > 1) {
        boolean separatorNeeded = false;
        for (Device device : recent) {
          String label = getLabel(device, isNexus(device));
          Icon icon = getDeviceClassIcon(device);
          add(new SetDeviceAction(myRenderContext, label, device, icon, device == current));
          separatorNeeded = true;
        }
        if (separatorNeeded) {
          addSeparator();
        }
      }
    }

    AndroidFacet facet = AndroidFacet.getInstance(configurationManager.getModule());
    if (facet == null) {
      // Unlikely, but has happened - see http://b.android.com/68091
      return true;
    }

    if (!deviceList.isEmpty()) {
      Map<String, List<Device>> manufacturers = new TreeMap<>();
      for (Device device : deviceList) {
        List<Device> devices;
        if (manufacturers.containsKey(device.getManufacturer())) {
          devices = manufacturers.get(device.getManufacturer());
        }
        else {
          devices = new ArrayList<>();
          manufacturers.put(device.getManufacturer(), devices);
        }
        devices.add(device);
      }
      List<Device> nexus = new ArrayList<>();
      Map<FormFactor, List<Device>> deviceMap = Maps.newEnumMap(FormFactor.class);
      for (FormFactor factor : FormFactor.values()) {
        deviceMap.put(factor, Lists.newArrayList());
      }
      for (List<Device> devices : manufacturers.values()) {
        for (Device device : devices) {
          if (isNexus(device) && !device.getManufacturer().equals(HardwareConfig.MANUFACTURER_GENERIC)
              && !isWear(device) && !isTv(device)) {
            nexus.add(device);
          }
          else {
            deviceMap.get(FormFactor.getFormFactor(device)).add(device);
          }
        }
      }

      sortDevicesByScreenSize(nexus);
      for (List<Device> list : splitDevicesByScreenSize(nexus)) {
        addNexusDeviceSection(this, current, list);
        addSeparator();
      }
      addDeviceSection(this, current, deviceMap, false, FormFactor.WEAR);
      addSeparator();
      addDeviceSection(this, current, deviceMap, false, FormFactor.TV);
      addSeparator();

      AvdManager avdManager = ModuleAvds.getInstance(facet).getAvdManagerSilently();
      if (avdManager != null) {
        boolean separatorNeeded = false;
        boolean first = true;
        for (AvdInfo avd : avdManager.getValidAvds()) {
          Device device = configurationManager.createDeviceForAvd(avd);
          if (device != null) {
            String avdName = "AVD: " + avd.getName();
            boolean selected = current != null && (current.getDisplayName().equals(avdName) || current.getId().equals(avdName));
            Icon icon = first ? getDeviceClassIcon(device) : null;
            add(new SetDeviceAction(myRenderContext, avdName, device, icon, selected));
            first = false;
            separatorNeeded = true;
          }
        }

        if (separatorNeeded) {
          addSeparator();
        }
      }

      DefaultActionGroup genericGroup = new DefaultActionGroup("_Generic Phones and Tablets", true);
      sortDevicesByScreenSize(deviceMap.get(FormFactor.MOBILE));
      addDeviceSection(genericGroup, current, deviceMap, true, FormFactor.MOBILE);
      add(genericGroup);
    }

    add(new RunAndroidAvdManagerAction("Add Device Definition..."));

    return true;
  }

  private static List<List<Device>> splitDevicesByScreenSize(List<Device> devices) {
    List<List<Device>> lists = Lists.newArrayList();
    List<Device> list = Lists.newArrayListWithExpectedSize(6);
    int prevGroup = -1;
    for (Device device : devices) {
      int group = sizeGroup(device);
      if (group != prevGroup) {
        prevGroup = group;
        list = Lists.newArrayListWithExpectedSize(6);
        lists.add(list);
      }
      list.add(device);
    }

    return lists;
  }

  private static int sizeGroup(Device device) {
    double diagonalLength = device.getDefaultHardware().getScreen().getDiagonalLength();
    if (diagonalLength < 5) {
      return 1;
    }
    else if (diagonalLength < 6.5) {
      return 2;
    }
    else {
      return 3;
    }
  }

  private void addNexusDeviceSection(@NotNull DefaultActionGroup group, @Nullable Device current, @NotNull List<Device> devices) {
    boolean first = true;
    for (final Device device : devices) {
      String label = getLabel(device, true /*nexus*/);
      Icon icon = first ? getDeviceClassIcon(device) : null;
      first = false;
      add(new SetDeviceAction(myRenderContext, label, device, icon, current == device));
    }
  }

  private void addDeviceSection(@NotNull DefaultActionGroup group,
                                @Nullable Device current,
                                @NotNull Map<FormFactor, List<Device>> deviceMap,
                                boolean reverse,
                                @NotNull FormFactor factor) {
    List<Device> generic = deviceMap.get(factor);
    if (reverse) {
      Collections.reverse(generic);
    }
    boolean first = true;
    for (final Device device : generic) {
      String label = getLabel(device, isNexus(device));
      Icon icon = first ? getDeviceClassIcon(device) : null;
      group.add(new SetDeviceAction(myRenderContext, label, device, icon, current == device));
      first = false;
    }
  }

  private String getLabel(Device device, boolean isNexus) {
    // See if there is a better match, and if so, display it in the menu action
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration != null) {
      VirtualFile better = ConfigurationMatcher.getBetterMatch(configuration, device, null, null, null);
      if (better != null) {
        return ConfigurationAction.getBetterMatchLabel(device.getDisplayName(), better, configuration.getFile());
      }
    }

    return isNexus ? getNexusMenuLabel(device) : getGenericLabel(device);
  }

  private class SetDeviceAction extends ConfigurationAction {
    private final Device myDevice;

    public SetDeviceAction(@NotNull ConfigurationHolder renderContext,
                           @NotNull final String title,
                           @NotNull final Device device,
                           @Nullable Icon defaultIcon,
                           final boolean select) {
      super(renderContext, title);
      myDevice = device;
      if (select) {
        getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
      }
      else if (ConfigurationAction.isBetterMatchLabel(title)) {
        getTemplatePresentation().setIcon(ConfigurationAction.getBetterMatchIcon());
      }
      else if (defaultIcon != null) {
        getTemplatePresentation().setIcon(defaultIcon);
      }
    }

    @Override
    protected void updatePresentation(@NotNull Presentation presentation) {
      DeviceMenuAction.this.updatePresentation(presentation);
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      // Attempt to jump to the default orientation of the new device; for example, if you're viewing a layout in
      // portrait orientation on a Nexus 4 (its default), and you switch to a Nexus 10, we jump to landscape orientation
      // (its default) unless of course there is a different layout that is the best fit for that device.
      Device prevDevice = configuration.getDevice();
      State prevState = configuration.getDeviceState();
      String newState = prevState != null ? prevState.getName() : null;
      if (prevDevice != null && prevState != null && prevState.isDefaultState() &&
          !myDevice.getDefaultState().getName().equals(prevState.getName()) &&
          configuration.getEditedConfig().getScreenOrientationQualifier() == null) {
        VirtualFile file = configuration.getFile();
        if (file != null) {
          String name = myDevice.getDefaultState().getName();
          if (ConfigurationMatcher.getBetterMatch(configuration, myDevice, name, null, null) == null) {
            newState = name;
          }
        }
      }

      if (newState != null) {
        configuration.setDeviceStateName(newState);
      }
      if (commit) {
        configuration.getConfigurationManager().selectDevice(myDevice);
      }
      else {
        configuration.setDevice(myDevice, true);
      }
    }
  }
}
