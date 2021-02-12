/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import kotlin.sequences.SequencesKt;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A dialog which lets a user add/delete/modify instrumentation extra params.
 * <p>
 * There are two list-table views in the dialog. The first table lets a user provide
 * additional extra params. The second table is populated based on defined extra params
 * in their Gradle build file. You cannot add or delete a row in the second table but
 * you can still override values. In addition, the dialog provides a checkbox which
 * allows a user to select whether or not to include those gradle defined extra params.
 * If it is unchecked, all extra params defined in the Gradle build file will be ignored.
 */
public class AndroidTestExtraParamsDialog extends DialogWrapper {
  /**
   * Following swing components will be instantiated automatically right before the constructor by
   * IntelliJ GUI Designer runtime library.
   */
  private JPanel myContentPanel;
  private JPanel myUserExtraParamsTablePanel;
  private JCheckBox myIncludeGradleExtraParamsCheckBox;
  private JPanel myGradleExtraParamsTablePanel;

  private final AndroidTestExtraParamsTable myUserParamsTable;
  private final AndroidTestExtraParamsTable myGradleParamsTable;

  private final ListenerManager myListenerManager = new ListenerManager();
  private final SelectedProperty myIsIncludeGradleParams;

  /**
   * Constructs and initializes the dialog.
   *
   * @param project                    the project that owns this dialog
   * @param androidFacet               the android facet to be used to retrieve Gradle defined extra params, or null for non-gradle project
   * @param instrumentationExtraParams a user defined extra params. Must be formatted like: "-e key1 value1 -e key2 value2 ..."
   * @param includeGradleExtraParams   an initial selection of "include gradle extra param" option
   */
  public AndroidTestExtraParamsDialog(@NotNull Project project,
                                      @Nullable AndroidFacet androidFacet,
                                      @NonNull String instrumentationExtraParams,
                                      boolean includeGradleExtraParams) {
    super(project);

    init();
    setTitle("Instrumentation Extra Params");

    Collection<AndroidTestExtraParam> params = AndroidTestExtraParamKt.merge(
      AndroidTestExtraParamKt.getAndroidTestExtraParams(androidFacet),
      AndroidTestExtraParam.parseFromString(instrumentationExtraParams));

    // Initialize list-table view for user defined params.
    myUserParamsTable = new AndroidTestExtraParamsTable(true, false);
    myUserParamsTable.setValues(
      params.stream()
        .filter(p -> p.getORIGINAL_VALUE_SOURCE() == AndroidTestExtraParamSource.NONE)
        .collect(Collectors.toList()));
    myUserExtraParamsTablePanel.add(myUserParamsTable.getComponent());

    // Initialize list-table view for gradle defined params.
    myGradleParamsTable = new AndroidTestExtraParamsTable(false, true);
    myGradleParamsTable.setValues(
      params.stream()
        .filter(p -> p.getORIGINAL_VALUE_SOURCE() == AndroidTestExtraParamSource.GRADLE)
        .collect(Collectors.toList()));
    myGradleExtraParamsTablePanel.add(myGradleParamsTable.getComponent());

    // Initialize "include gradle extra params" checkbox.
    myIsIncludeGradleParams = new SelectedProperty(myIncludeGradleExtraParamsCheckBox);
    myIsIncludeGradleParams.set(includeGradleExtraParams);
    myListenerManager.listenAndFire(myIsIncludeGradleParams, value -> {
      if (value) {
        myGradleParamsTable.setEnabled();
      }
      else {
        myGradleParamsTable.setDisabled();
      }
    });
  }

  @Override
  protected void dispose() {
    super.dispose();
    myListenerManager.releaseAll();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  /**
   * Returns the current selection of the "include gradle extra params" option.
   */
  public boolean getIncludeGradleExtraParams() {
    return myIsIncludeGradleParams.get();
  }

  /**
   * Returns formatted string of current instrumentation extra params. e.g. "-e key1 value1 -e key2 value2".
   */
  public String getInstrumentationExtraParams() {
    if (!getIncludeGradleExtraParams()) {
      return getUserModifiedInstrumentationExtraParams();
    }
    return AndroidTestExtraParamKt.merge(SequencesKt.asSequence(myGradleParamsTable.getTableView().getItems().iterator()),
                                         SequencesKt.asSequence(myUserParamsTable.getTableView().getItems().iterator()))
      .stream()
      .filter(p -> !p.getNAME().isEmpty())
      .map(p -> String.format("-e %s %s", p.getNAME(), p.getVALUE()))
      .collect(Collectors.joining(" "));
  }

  /**
   * Returns formatted string of current instrumentation extra params. e.g. "-e key1 value1 -e key2 value2".
   * <p>
   * Unlike {@link #getInstrumentationExtraParams}, the string contains a user defined params and modified Gradle defined params only.
   */
  public String getUserModifiedInstrumentationExtraParams() {
    return AndroidTestExtraParamKt.merge(SequencesKt.asSequence(myGradleParamsTable.getTableView().getItems().iterator()),
                                         SequencesKt.asSequence(myUserParamsTable.getTableView().getItems().iterator()))
      .stream()
      .filter(p -> {
        if (p.getNAME().isEmpty()) {
          return false;
        }
        // Keep ones which are user defined.
        if (p.getORIGINAL_VALUE_SOURCE() == AndroidTestExtraParamSource.NONE) {
          return true;
        }
        // Also keep user modified params which are originally defined in gradle build file.
        return !Objects.equals(p.getVALUE(), p.getORIGINAL_VALUE());
      })
      .map(p -> String.format("-e %s %s", p.getNAME(), p.getVALUE()))
      .collect(Collectors.joining(" "));
  }
}
