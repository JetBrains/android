/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.flags;

import com.android.flags.Flag;
import com.android.flags.FlagGroup;
import com.android.flags.FlagOverrides;
import com.android.flags.ImmutableFlagOverrides;
import com.android.flags.overrides.DefaultFlagOverrides;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Comparator;

import static com.android.tools.idea.observable.expressions.bool.BooleanExpressions.not;

public final class StudioFlagsDialog extends DialogWrapper {
  public static final String TITLE = "Edit Studio Flags";
  private final FlagOverrides myBackupOverrides = new DefaultFlagOverrides();

  private final BindingsManager myBindings = new BindingsManager();
  private final ListMultimap<FlagGroup, Flag<?>> myGroupedFlags;

  private JPanel myRootPanel;
  private JPanel myContentPanel;
  private JBScrollPane myScrollPane;

  public StudioFlagsDialog() {
    super(null);
    setTitle(TITLE);
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

    myRootPanel.setPreferredSize(new Dimension((int)(screenSize.width * 0.5), (int)(screenSize.height * 0.8)));

    myGroupedFlags = collectAllFlags();

    replaceOverrides(myBackupOverrides, StudioFlagSettings.getInstance());

    myGroupedFlags.asMap().entrySet().stream()
      .sorted(Comparator.comparing(entry -> entry.getKey().getDisplayName()))
      .forEach(entry -> {
        FlagGroup group = entry.getKey();
        Collection<Flag<?>> flags = entry.getValue();
        if (flags.isEmpty()) {
          return;
        }

        JPanel groupPanel = new JPanel(new VerticalFlowLayout(5, 0));
        TitledBorder titledBorder = BorderFactory.createTitledBorder(group.getDisplayName());
        titledBorder.setTitleFont(titledBorder.getTitleFont().deriveFont(Font.BOLD, titledBorder.getTitleFont().getSize() + 2));
        groupPanel.setBorder(titledBorder);

        boolean firstFlag = true;
        for (Flag<?> flag : flags) {
          JPanel flagPanel = new JPanel(new VerticalFlowLayout(5, 0));
          if (!firstFlag) {
            flagPanel.add(new JSeparator(SwingConstants.HORIZONTAL));
          }

          JBLabel name = new JBLabel(flag.getDisplayName());
          name.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
          name.setToolTipText(flag.getId());

          JTextArea description = new JTextArea(flag.getDescription());
          description.setFont(UIUtil.getLabelFont());
          description.setLineWrap(true);
          description.setWrapStyleWord(true);
          description.setEditable(false);
          description.setOpaque(false);
          description.setForeground(UIUtil.getInactiveTextColor());

          FlagEditor<?> flagEditor = createFlagEditor(flag);

          HyperlinkLabel resetLink = new HyperlinkLabel("Reset to default");
          resetLink.addHyperlinkListener(new HyperlinkAdapter() {
            @Override
            protected void hyperlinkActivated(HyperlinkEvent e) {
              flagEditor.flagProperty().clearOverride();
            }
          });
          myBindings.bind(new VisibleProperty(resetLink), flagEditor.flagProperty().isOverridden());

          JPanel flagEditorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
          flagEditorPanel.add(flagEditor.editorComponent());
          flagEditorPanel.add(resetLink);

          flagPanel.add(name);
          flagPanel.add(description);
          flagPanel.add(flagEditorPanel);
          groupPanel.add(flagPanel);

          firstFlag = false;
        }

        myContentPanel.add(groupPanel);
      });

    init();

    // The vscrollpane doesn't start exactly at the top for some reason. This forcefully corrects it.
    ApplicationManager.getApplication().invokeLater(() -> myScrollPane.getVerticalScrollBar().setValue(0), ModalityState.any());
  }

  private void replaceOverrides(FlagOverrides overridesDest, ImmutableFlagOverrides overridesSrc) {
    overridesDest.clear();
    for (Flag<?> flag : myGroupedFlags.values()) {
      String flagValue = overridesSrc.get(flag);
      if (flagValue != null) {
        overridesDest.put(flag, flagValue);
      }
    }
  }

  /**
   * Use reflection to fetch all flags (and their owning groups) within the {@link StudioFlags} class.
   */
  private static ListMultimap<FlagGroup, Flag<?>> collectAllFlags() {
    ListMultimap<FlagGroup, Flag<?>> allFlags = ArrayListMultimap.create();
    for (Field field : StudioFlags.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        try {
          Object instance = field.get(null);
          if (instance instanceof Flag) {
            Flag flag = (Flag)instance;
            allFlags.put(flag.getGroup(), flag);
          }
        }
        catch (IllegalAccessException ignored) {
        }
      }
    }
    return allFlags;
  }

  @SuppressWarnings("unchecked") // Flag<?> carefully cast to Flag<T>
  @NotNull
  private FlagEditor<?> createFlagEditor(Flag<?> flag) {
    if (flag.get().getClass() == Boolean.class) {
      Flag<Boolean> boolFlag = ((Flag<Boolean>)flag);
      return new FlagEditor<Boolean>() {
        FlagProperty<Boolean> myFlagProperty = new FlagProperty<>(boolFlag);

        @NotNull
        @Override
        public FlagProperty<Boolean> flagProperty() {
          return myFlagProperty;
        }

        @NotNull
        @Override
        public JComponent editorComponent() {
          JPanel choicesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
          JRadioButton onButton = new JRadioButton("On");
          JRadioButton offButton = new JRadioButton("Off");

          ButtonGroup group = new ButtonGroup();
          group.add(onButton);
          group.add(offButton);

          myBindings.bind(new SelectedProperty(onButton), myFlagProperty);
          myBindings.bind(new SelectedProperty(offButton), not(myFlagProperty));
          onButton.addActionListener(l -> myFlagProperty.set(true));
          offButton.addActionListener(l -> myFlagProperty.set(false));

          choicesPanel.add(onButton);
          choicesPanel.add(offButton);
          return choicesPanel;
        }
      };
    }
    else {
      throw new IllegalStateException(
        String.format("Unhandled flag type (\"%s\"): Flag<%s>", flag.getId(), flag.get().getClass().getSimpleName()));
    }
  }

  @Override
  public void doCancelAction() {
    replaceOverrides(StudioFlagSettings.getInstance(), myBackupOverrides);
    super.doCancelAction();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{
      getCancelAction(),
      getOKAction()
    };
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  private void createUIComponents() {
    myContentPanel = new JPanel(new VerticalFlowLayout(5, 15));
  }

  private interface FlagEditor<T> {
    @NotNull
    FlagProperty<T> flagProperty();

    @NotNull
    JComponent editorComponent();
  }

  /**
   * A class which wraps a {@link Flag} with a property shell, so that changes to the flag's value
   * can be listened and bound to.
   */
  private static final class FlagProperty<T> extends AbstractProperty<T> {
    private final Flag<T> myFlag;
    private final BoolProperty myOverridden;

    public FlagProperty(Flag<T> flag) {
      myFlag = flag;
      myOverridden = new BoolValueProperty(myFlag.isOverridden());
    }

    @NotNull
    @Override
    public T get() {
      return myFlag.get();
    }

    public ObservableBool isOverridden() {
      return myOverridden;
    }

    @Override
    protected void setDirectly(@NotNull T value) {
      myFlag.override(value);
      myOverridden.set(true);
    }

    public void clearOverride() {
      myFlag.clearOverride();
      myOverridden.set(false);
      notifyInvalidated();
    }
  }
}
