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
package com.android.tools.idea.avdmanager;

import com.android.repository.io.FileOpUtils;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Device;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ComboboxWithBrowseButton;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.avdmanager.AvdWizardUtils.NO_SKIN;

/**
 * Combobox that populates itself with the skins used by existing devices. Also allows adding a
 * new skin by browsing.
 */
public class SkinChooser extends ComboboxWithBrowseButton implements ItemListener, ItemSelectable {
  private List<ItemListener> myListeners = Lists.newArrayList();

  public SkinChooser(@Nullable Project project) {
    setItems(getSkins());
    getComboBox().setRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        File skinFile = ((value) == null) ? NO_SKIN : (File)value;
        String skinPath = skinFile.getPath();
        if (FileUtil.filesEqual(skinFile, NO_SKIN)) {
          append("No Skin");
        }
        else if (skinPath.contains("/sdk/platforms/")) {
          append(skinPath.replaceAll(".*/sdk/platforms/(.*)/skins/(.*)", "$2 ($1)"));
        }
        else if (skinPath.contains("/sdk/system-images/")) {
          append(skinPath.replaceAll(".*/sdk/system-images/(.*)/(.*)/(.*)/skins/(.*)", "$4 ($1 $3)"));
        }
        else {
          append(skinFile.getName());
        }
      }
    });
    FileChooserDescriptor skinChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    addBrowseFolderListener("Select Custom Skin", "Select the directory containing your custom skin definition", project,
                            skinChooserDescriptor, new TextComponentAccessor<JComboBox>() {
        @Override
        public String getText(JComboBox component) {
          return ((File)component.getSelectedItem()).getPath();
        }

        @Override
        public void setText(JComboBox component, @NotNull String text) {
          List<File> items = getSkins();
          File f = new File(text);
          items.add(f);
          setItems(items);
          getComboBox().setSelectedItem(f);
        }
      });
    getComboBox().addItemListener(this);
    setTextFieldPreferredWidth(20);

  }

  private void setItems(List<File> items) {
    getComboBox().setModel(new CollectionComboBoxModel<File>(items));
  }

  private static List<File> getSkins() {
    List<Device> devices = DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevices();

    Set<File> result = Sets.newTreeSet();
    for (Device device : devices) {
      File skinFile = AvdWizardUtils.resolveSkinPath(device.getDefaultHardware().getSkinFile(), null, FileOpUtils.create());
      if (skinFile != null && skinFile.exists()) {
        result.add(skinFile);
      }
    }
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(SkinChooser.class);
    AndroidSdkHandler sdkHandler = AndroidSdkUtils.tryToChooseSdkHandler();
    for (IAndroidTarget target : sdkHandler.getAndroidTargetManager(progress).getTargets(progress)) {
      Collections.addAll(result, target.getSkins());
    }
    for (ISystemImage img : sdkHandler.getSystemImageManager(progress).getImages()) {
      Collections.addAll(result, img.getSkins());
    }

    List<File> resultList = Lists.newArrayList();
    resultList.add(NO_SKIN);
    resultList.addAll(result);
    return resultList;
  }

  @Override
  public void itemStateChanged(ItemEvent e) {
    ItemEvent newEvent = new ItemEvent(this, e.getID(), e.getItem(), e.getStateChange());
    for (ItemListener listener : myListeners) {
      listener.itemStateChanged(newEvent);
    }
  }

  @Override
  public Object[] getSelectedObjects() {
    return getComboBox().getSelectedObjects();
  }

  @Override
  public void addItemListener(ItemListener l) {
    getComboBox().addItemListener(l);
  }

  @Override
  public void removeItemListener(ItemListener l) {
    getComboBox().removeItemListener(l);
  }
}
