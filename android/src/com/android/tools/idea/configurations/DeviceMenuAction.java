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
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.rendering.multi.RenderPreviewMode;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

import static com.android.ide.common.rendering.HardwareConfigHelper.*;

public class DeviceMenuAction extends FlatComboAction {
  private static final boolean LIST_RECENT_DEVICES = false;
  private final RenderContext myRenderContext;

  public DeviceMenuAction(@NotNull RenderContext renderContext) {
    myRenderContext = renderContext;
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
    Configuration configuration = myRenderContext.getConfiguration();
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
            } else {
              return name.substring(begin, end).trim();
            }
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
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return group;
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
          group.add(new SetDeviceAction(myRenderContext, label, device, device == current));
          separatorNeeded = true;
        }
        if (separatorNeeded) {
          group.addSeparator();
        }
      }
    }

    AndroidFacet facet = AndroidFacet.getInstance(configurationManager.getModule());
    if (facet == null) {
      // Unlikely, but has happened - see http://b.android.com/68091
      return group;
    }
    final AvdManager avdManager = facet.getAvdManagerSilently();
    if (avdManager != null) {
      boolean separatorNeeded = false;
      for (AvdInfo avd : avdManager.getValidAvds()) {
        Device device = configurationManager.createDeviceForAvd(avd);
        if (device != null) {
          String avdName = "AVD: " + avd.getName();
          boolean selected = current != null && (current.getDisplayName().equals(avdName) || current.getId().equals(avdName));
          group.add(new SetDeviceAction(myRenderContext, avdName, device, selected));
          separatorNeeded = true;
        }
      }

      if (separatorNeeded) {
        group.addSeparator();
      }
    }

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
              if (device.getManufacturer().equals(MANUFACTURER_GENERIC)) {
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
          if (device.getId().equals("Nexus 5")) {
            // Hide Nexus 5 if using an older layoutlib than API 19 revision 2, due to rendering bugs
            // fixed in that revision
            IAndroidTarget target = myRenderContext.getConfiguration().getTarget();
            if (target == null) {
              continue;
            }
            AndroidVersion version = target.getVersion();
            if (version.getApiLevel() < 19 || version.getApiLevel() == 19 && target.getRevision() < 2) {
              continue;
            }
          }
          String label = getLabel(device, true /*nexus*/);
          group.add(new SetDeviceAction(myRenderContext, label, device, current == device));
        }

        group.addSeparator();
      }

      // Generate the generic menu.
      Collections.reverse(generic);
      for (final Device device : generic) {
        String label = getLabel(device, false /*nexus*/);
        group.add(new SetDeviceAction(myRenderContext, label, device, current == device));
      }
    }

    group.addSeparator();
    group.add(new RunAndroidAvdManagerAction("Add Device Definition..."));
    group.addSeparator();
    if (RenderPreviewMode.getCurrent() != RenderPreviewMode.SCREENS) {
      ConfigurationMenuAction.addScreenSizeAction(myRenderContext, group);
    } else {
      ConfigurationMenuAction.addRemovePreviewsAction(myRenderContext, group);
    }

    return group;
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

    return isNexus ? getNexusLabel(device) : getGenericLabel(device);
  }

  private class SetDeviceAction extends ConfigurationAction {
    private final Device myDevice;

    public SetDeviceAction(@NotNull RenderContext renderContext,
                           @NotNull final String title,
                           @NotNull final Device device,
                           final boolean select) {
      super(renderContext, title);
      myDevice = device;
      if (select) {
        getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
      } else if (ConfigurationAction.isBetterMatchLabel(title)) {
        getTemplatePresentation().setIcon(ConfigurationAction.getBetterMatchIcon());
      }
    }

    @Override
    protected void updatePresentation() {
      DeviceMenuAction.this.updatePresentation(DeviceMenuAction.this.getTemplatePresentation());
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      // Attempt to jump to the default orientation of the new device; for example, if you're viewing a layout in
      // portrait orientation on a Nexus 4 (its default), and you switch to a Nexus 10, we jump to landscape orientation
      // (its default) unless of course there is a different layout that is the best fit for that device.
      Device prevDevice = configuration.getDevice();
      State prevState = configuration.getDeviceState();
      String newState = prevState != null ? prevState.getName() : null;
      if (prevDevice != null && prevState != null && configuration.getDeviceState() == prevDevice.getDefaultState() &&
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
      } else {
        configuration.setDevice(myDevice, true);
      }
    }
  }
}
