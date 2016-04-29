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
package com.android.tools.idea.run.editor;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.run.AvdComboBox;
import com.android.tools.idea.run.LaunchCompatibility;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ThreeState;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class EmulatorTargetConfigurable implements DeployTargetConfigurable<EmulatorTargetProvider.State> {
  private final Project myProject;
  private final Disposable myParentDisposable;
  private final DeployTargetConfigurableContext myContext;

  private JPanel myPanel;
  private JBLabel myMinSdkInfoMessageLabel;
  private ComboboxWithBrowseButton myAvdComboWithButton;
  private AvdComboBox myAvdCombo;
  private String myIncorrectPreferredAvd;

  public EmulatorTargetConfigurable(@NotNull Project project,
                                    @NotNull Disposable parentDisposable,
                                    @NotNull final DeployTargetConfigurableContext context) {
    myProject = project;
    myParentDisposable = parentDisposable;
    myContext = context;

    myMinSdkInfoMessageLabel.setBorder(IdeBorderFactory.createEmptyBorder(10, 0, 0, 0));
    myMinSdkInfoMessageLabel.setIcon(AllIcons.General.BalloonWarning);

    context.addModuleChangeListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myAvdCombo.startUpdatingAvds(ModalityState.current());
      }
    });
  }

  private void createUIComponents() {
    myAvdCombo = new AvdComboBox(myProject, true, false) {
      @Override
      public Module getModule() {
        return myContext.getModule();
      }
    };
    myAvdCombo.startUpdatingAvds(ModalityState.current());

    JComboBox avdComboBox = myAvdCombo.getComboBox();
    avdComboBox.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof IdDisplay) {
          setText(((IdDisplay)value).getDisplay());
        }
        else {
          setText(String.format("<html><font color='red'>Unknown AVD %1$s</font></html>", value == null ? "" : value.toString()));
        }
      }
    });

    avdComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        resetAvdCompatibilityWarningLabel();
      }
    });

    myAvdComboWithButton = new ComboboxWithBrowseButton(avdComboBox);

    Disposer.register(myParentDisposable, myAvdCombo);
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public void resetFrom(@NotNull EmulatorTargetProvider.State state, int uniqueID) {
    final JComboBox combo = myAvdCombo.getComboBox();
    final String avd = state.PREFERRED_AVD;
    if (avd != null) {
      Object item = findAvdWithName(combo, avd);
      if (item != null) {
        combo.setSelectedItem(item);
      }
      else {
        combo.setSelectedItem(null);
        myIncorrectPreferredAvd = avd;
      }
    }

    resetAvdCompatibilityWarningLabel();
  }

  @Override
  public void applyTo(@NotNull EmulatorTargetProvider.State state, int uniqueID) {
    state.PREFERRED_AVD = "";

    JComboBox combo = myAvdCombo.getComboBox();
    IdDisplay preferredAvd = (IdDisplay)combo.getSelectedItem();
    if (preferredAvd == null) {
      state.PREFERRED_AVD = myIncorrectPreferredAvd != null ? myIncorrectPreferredAvd : "";
    }
    else {
      state.PREFERRED_AVD = preferredAvd.getId();
    }
  }

  private void resetAvdCompatibilityWarningLabel() {
    String warning = getAvdCompatibilityWarning();

    if (warning != null) {
      myMinSdkInfoMessageLabel.setVisible(true);
      myMinSdkInfoMessageLabel.setText(warning);
    }
    else {
      myMinSdkInfoMessageLabel.setVisible(false);
    }
  }

  @Nullable
  private String getAvdCompatibilityWarning() {
    IdDisplay selectedItem = (IdDisplay)myAvdCombo.getComboBox().getSelectedItem();

    if (selectedItem != null) {
      final String selectedAvdName = selectedItem.getId();
      final Module module = myContext.getModule();
      if (module == null) {
        return null;
      }

      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        return null;
      }

      final AvdManager avdManager = facet.getAvdManagerSilently();
      if (avdManager == null) {
        return null;
      }

      final AvdInfo avd = avdManager.getAvd(selectedAvdName, false);
      if (avd == null || avd.getSystemImage() == null) {
        return null;
      }

      AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
      if (platform == null) {
        return null;
      }

      AndroidVersion minSdk = AndroidModuleInfo.get(facet).getRuntimeMinSdkVersion();
      LaunchCompatibility compatibility = LaunchCompatibility.canRunOnAvd(minSdk, platform.getTarget(), avd.getSystemImage());
      if (compatibility.isCompatible() == ThreeState.NO) {
        // todo: provide info about current module configuration
        return String.format("'%1$s' may be incompatible with your configuration (%2$s)", selectedAvdName,
                             StringUtil.notNullize(compatibility.getReason()));
      }
    }
    return null;
  }

  @Nullable
  private static Object findAvdWithName(@NotNull JComboBox avdCombo, @NotNull String avdName) {
    for (int i = 0, n = avdCombo.getItemCount(); i < n; i++) {
      Object item = avdCombo.getItemAt(i);
      if (item instanceof IdDisplay && avdName.equals(((IdDisplay)item).getId())) {
        return item;
      }
    }
    return null;
  }
}
