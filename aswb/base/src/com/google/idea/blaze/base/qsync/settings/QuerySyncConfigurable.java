/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.qsync.settings;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.project.BaseQuerySyncConversionUtility;
import com.intellij.openapi.options.BoundSearchableConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.dsl.builder.BuilderKt;
import com.intellij.ui.dsl.builder.ButtonKt;
import com.intellij.ui.dsl.builder.Cell;
import com.intellij.ui.dsl.builder.MutableProperty;
import com.intellij.ui.dsl.builder.Panel;
import com.intellij.ui.dsl.builder.Row;
import com.intellij.ui.dsl.builder.RowsRange;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import kotlin.Unit;

/** A configuration page for the settings dialog for query sync. */
class QuerySyncConfigurable extends BoundSearchableConfigurable implements Configurable {

  private final QuerySyncSettings settings = QuerySyncSettings.getInstance();

  // Provides access to enableQuerySyncCheckBoxCell for other rows
  private Cell<JBCheckBox> enableQuerySyncCheckBoxCell = null;

  public QuerySyncConfigurable() {
    super(/* displayName= */ "Query Sync", /* helpTopic= */ "", /* _id= */ "query.sync");
  }

  @CanIgnoreReturnValue
  private Row addCheckBox(
      Panel ip, String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
    return ip.row(
        /* label= */ ((JLabel) null),
        /* init= */ r -> {
          Cell<JBCheckBox> unusedCheckbox =
              r.checkBox(label)
                  .bind(
                      /* componentGet= */ AbstractButton::isSelected,
                      /* componentSet= */ (jbCheckBox, selected) -> {
                        jbCheckBox.setSelected(selected);
                        jbCheckBox.setEnabled(
                          !BaseQuerySyncConversionUtility.AUTO_CONVERT_LEGACY_SYNC_TO_QUERY_SYNC_EXPERIMENT.isEnabled());
                        return Unit.INSTANCE;
                      },
                      /* prop= */ new MutableProperty<Boolean>() {
                        @Override
                        public Boolean get() {
                          return getter.get();
                        }

                        @Override
                        public void set(Boolean selected) {
                          setter.accept(selected);
                        }
                      })
                  .enabledIf(ButtonKt.getSelected(enableQuerySyncCheckBoxCell));
          return Unit.INSTANCE;
        });
  }

  @Override
  public DialogPanel createPanel() {
    return BuilderKt.panel(
        p -> {
          // Enable query sync checkbox
          Row unusedEnableQuerySyncRow =
              p.row(
                  /* label= */ ((JLabel) null),
                  /* init= */ r -> {
                    enableQuerySyncCheckBoxCell =
                        r.checkBox("Enable Query Sync for new projects")
                            .bind(
                                /* componentGet= */ AbstractButton::isSelected,
                                /* componentSet= */ (jbCheckBox, selected) -> {
                                  jbCheckBox.setSelected(selected);
                                  // Disable the checkbox if the Query-Sync auto conversion experiment is enabled
                                  jbCheckBox.setEnabled(!BaseQuerySyncConversionUtility.AUTO_CONVERT_LEGACY_SYNC_TO_QUERY_SYNC_EXPERIMENT.isEnabled());
                                  return Unit.INSTANCE;
                                },
                                /* prop= */ new MutableProperty<Boolean>() {
                                  @Override
                                  public Boolean get() {
                                    return settings.useQuerySync();
                                  }

                                  @Override
                                  public void set(Boolean selected) {
                                    settings.enableUseQuerySync(selected);
                                  }
                                });
                    return Unit.INSTANCE;
                  });

          // Other sub options
          RowsRange unusedRowRange =
              p.indent(
                  ip -> {
                    addCheckBox(
                        ip,
                        "Display detailed dependency text in the editor",
                        settings::showDetailedInformationInEditor,
                        settings::enableShowDetailedInformationInEditor);
                    return Unit.INSTANCE;
                  });
          return Unit.INSTANCE;
        });
  }
}
