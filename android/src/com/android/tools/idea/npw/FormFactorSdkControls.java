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
package com.android.tools.idea.npw;

import com.android.tools.idea.stats.DistributionService;
import com.android.tools.idea.wizard.dynamic.ScopedDataBinder;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Locale;
import java.util.Set;

import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static com.android.tools.idea.npw.FormFactorUtils.getMinApiLevelKey;
import static com.android.tools.idea.npw.FormFactorUtils.getTargetComboBoxKey;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

/**
 * Collection of controls for selecting whether a form factor should be selected, what the minimum api version should be, and
 * optionally some other info.
 */
final class FormFactorSdkControls {
  private JBLabel myLabel;
  private FormFactorApiComboBox myMinSdkCombobox;
  private final FormFactor myFormFactor;
  private final int myMinApi;
  private static final ScopedStateStore.Key<String> API_FEEDBACK_KEY = createKey("API Feedback", STEP, String.class);
  private JBLabel myHelpMeChooseLabel2;
  private HyperlinkLabel myHelpMeChooseLink;
  private ChooseApiLevelDialog myChooseApiLevelDialog = new ChooseApiLevelDialog(null, -1);
  private final ScopedDataBinder myBinder;
  private JCheckBox myInclusionCheckBox;
  private JPanel myRootPanel;
  private JLabel myNotAvailableLabel;
  private JPanel myStatsPanel;
  private JPanel myLoadingStatsPanel;
  private final Disposable myDisposable;
  private final ScopedStateStore.Key<Boolean> myInclusionKey;
  private JBLabel myStatsLoadFailedLabel;
  private JBLabel myHelpMeChooseLabel1;

  /**
   * Creates a new FormFactorSdkControls.
   * Before displaying, you must call {@link #init} to populate content and set up listeners.
   *
   * @param formFactor The FormFactor these controls govern.
   * @param minApi The minimum API that should be selectable for this form factor.
   * @param disposable The parent Disposable for this component.
   * @param binder The ScopedDataBinder we will use to register our components.
   */
  public FormFactorSdkControls(FormFactor formFactor, int minApi, Disposable disposable,
                               ScopedDataBinder binder) {
    myFormFactor = formFactor;
    myMinApi = minApi;
    Disposer.register(disposable, myChooseApiLevelDialog.getDisposable());
    myBinder = binder;
    myInclusionCheckBox.setText(formFactor.toString());
    myDisposable = disposable;
    myInclusionKey = FormFactorUtils.getInclusionKey(myFormFactor);
    myHelpMeChooseLabel2.setText(getApiHelpText(0, ""));
    myHelpMeChooseLink.setHyperlinkText("Help me choose");
    // Only show SDK selector for base form factors.
    if (myFormFactor.baseFormFactor != null) {
      myLabel.setVisible(false);
      myMinSdkCombobox.setVisible(false);
    }

    if (!myFormFactor.equals(MOBILE)) {
      myHelpMeChooseLabel1.setVisible(false);
      myStatsPanel.setVisible(false);
    }

    myMinSdkCombobox.setName(myFormFactor.id + ".minSdk");
    myStatsLoadFailedLabel.setForeground(JBColor.GRAY);
  }

  /**
   * @return The key we'll use in the ScopedStateStore to indicate whether this form factor is selected.
   */
  public ScopedStateStore.Key<Boolean> getInclusionKey() {
    return myInclusionKey;
  }

  /**
   * @param state The ScopedStateStore in which to store our selections
   * @param downloadSuccess A Runnable that will be run if any valid items were found for this form factor.
   * @param downloadFailed A Runnable that will be run if no valid items were found for this form factor.
   */
  public void init(final ScopedStateStore state, final Runnable loadComplete) {
    myBinder.register(myInclusionKey, myInclusionCheckBox);

    myHelpMeChooseLink.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        Integer minApiLevel = state.get(getMinApiLevelKey(MOBILE));
        myChooseApiLevelDialog = new ChooseApiLevelDialog(null, minApiLevel == null ? 0 : minApiLevel);
        Disposer.register(myDisposable, myChooseApiLevelDialog.getDisposable());
        if (myChooseApiLevelDialog.showAndGet()) {
          int selectedApiLevel = myChooseApiLevelDialog.getSelectedApiLevel();
          ScopedDataBinder.setSelectedItem(myMinSdkCombobox, Integer.toString(selectedApiLevel));
        }
      }
    });

    myBinder.register(API_FEEDBACK_KEY, myHelpMeChooseLabel2, new ScopedDataBinder.ComponentBinding<String, JBLabel>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull JBLabel label) {
        final JBLabel referenceLabel = label;
        final String referenceString = newValue;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            referenceLabel.setText(referenceString);
          }
        });
      }
    });
    myBinder.registerValueDeriver(API_FEEDBACK_KEY, new ScopedDataBinder.ValueDeriver<String>() {
      @Nullable
      @Override
      public Set<ScopedStateStore.Key<?>> getTriggerKeys() {
        return makeSetOf(getTargetComboBoxKey(MOBILE));
      }

      @Nullable
      @Override
      public String deriveValue(@NotNull ScopedStateStore state, ScopedStateStore.Key changedKey, @Nullable String currentValue) {
        FormFactorApiComboBox.AndroidTargetComboBoxItem selectedItem = state.get(getTargetComboBoxKey(MOBILE));
        String name = Integer.toString(selectedItem == null ? 0 : selectedItem.getApiLevel());
        if (selectedItem != null && selectedItem.target != null) {
          name = selectedItem.target.getVersion().getApiString();
        }
        return getApiHelpText(selectedItem == null || !myStatsPanel.isVisible() ? 0 : selectedItem.getApiLevel(), name);
      }
    });

    myMinSdkCombobox.init(myFormFactor, myMinApi, loadComplete,
                          new Runnable() {
                            @Override
                            public void run() {
                              myInclusionCheckBox.setEnabled(true);
                              myLabel.setEnabled(true);
                              myMinSdkCombobox.setEnabled(true);
                            }
                          },
                          new Runnable() {
                            @Override
                            public void run() {
                              myInclusionCheckBox.setSelected(false);
                              myNotAvailableLabel.setVisible(true);
                            }
                          });

    myMinSdkCombobox.registerWith(myBinder);

    myMinSdkCombobox.loadSavedApi();

    if (myStatsPanel.isVisible()) {
      DistributionService.getInstance().refresh(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              ((CardLayout)myStatsPanel.getLayout()).show(myStatsPanel, "stats");
              myBinder.invokeUpdate(getTargetComboBoxKey(MOBILE));
            }
          });
        }
      }, new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              ((CardLayout)myStatsPanel.getLayout()).show(myStatsPanel, "stats");
              myBinder.invokeUpdate(getTargetComboBoxKey(MOBILE));
              myStatsLoadFailedLabel.setVisible(true);
            }
          });
        }
      });
    }
  }

  public JComponent getComponent() {
    return myRootPanel;
  }

  /**
   * Fill in the values that can be derived from the selected min SDK level:
   */
  public void deriveValues(@NotNull ScopedStateStore stateStore, @NotNull Set<ScopedStateStore.Key> modified) {
    myMinSdkCombobox.deriveValues(stateStore, modified);
  }

  private static String getApiHelpText(int selectedApi, String selectedApiName) {
    float percentage = (float)(DistributionService.getInstance().getSupportedDistributionForApiLevel(selectedApi) * 100);
    return String.format(Locale.getDefault(), "<html>By targeting API %1$s and later, your app will run on %2$s of the devices<br>that are " +
                                              "active on the Google Play Store.</html>",
                         selectedApiName,
                         percentage < 1 ? "&lt; 1%" : String.format(Locale.getDefault(), "approximately <b>%.1f%%</b>", percentage));
  }

  private void createUIComponents() {
    myLoadingStatsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    AsyncProcessIcon refreshIcon = new AsyncProcessIcon("loading");
    JLabel refreshingLabel = new JLabel("Loading Stats...");
    refreshingLabel.setForeground(JBColor.GRAY);
    myLoadingStatsPanel.add(refreshIcon);
    myLoadingStatsPanel.add(refreshingLabel);
  }
}
