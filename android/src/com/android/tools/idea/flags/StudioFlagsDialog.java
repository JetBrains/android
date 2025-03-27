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

import static com.android.tools.idea.observable.expressions.bool.BooleanExpressions.not;

import com.android.flags.Flag;
import com.android.flags.FlagGroup;
import com.android.flags.FlagOverrides;
import com.android.flags.ImmutableFlagOverrides;
import com.android.flags.overrides.DefaultFlagOverrides;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.SpinnerLongValueProperty;
import com.android.tools.idea.observable.ui.SpinnerValueProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.LightColors;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StudioFlagsDialog extends DialogWrapper {
  /**
   * Identifier used to store the dialog location and size across invocation. See {@link #getDimensionServiceKey()}
   */
  private static final String DIMENSION_KEY = StudioFlagsDialog.class.getSimpleName();
  public static final String TITLE = "Edit Studio Flags";
  private final FlagOverrides myBackupOverrides = new DefaultFlagOverrides();

  private final BindingsManager myBindings = new BindingsManager();
  private final ListMultimap<FlagGroup, Flag<?>> myGroupedFlags;

  private JPanel myRootPanel;
  private JPanel myContentPanel;
  private JBScrollPane myScrollPane;
  private SearchTextField mySearchTextField;

  public StudioFlagsDialog(@Nullable Project project) {
    super(project);
    setupUI();
    setTitle(TITLE);

    setPreferredBounds(project);
    myGroupedFlags = collectAllFlags();

    replaceOverrides(myBackupOverrides, StudioFlagSettings.getInstance());

    updateFlagsComponents(null);

    mySearchTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        searchTextChanged(getText(e));
      }

      @NotNull
      private String getText(DocumentEvent e) {
        try {
          return e.getDocument().getText(0, e.getDocument().getLength());
        }
        catch (BadLocationException e1) {
          return "";
        }
      }
    });

    init();

    // The vscrollpane doesn't start exactly at the top for some reason. This forcefully corrects it.
    ApplicationManager.getApplication().invokeLater(() -> myScrollPane.getVerticalScrollBar().setValue(0), ModalityState.any());
  }

  private void setPreferredBounds(@Nullable Project project) {
    Window window = WindowManager.getInstance().suggestParentWindow(project);
    if (window != null) {
      Rectangle bounds = window.getBounds();

      // Window initially shows centered inside parent window, 1/2 width and 2/3rd height
      bounds.grow(-(bounds.width / 4), -(bounds.height / 6));

      // Location is set on a callback, size is set using preferred size
      // Note that these values are ignored if the window bounds have been persisted
      // in a previous invocation (see getDimensionServiceKey)
      setInitialLocationCallback(bounds::getLocation);
      myRootPanel.setPreferredSize(bounds.getSize());
    }
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySearchTextField;
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return DIMENSION_KEY;
  }

  private void searchTextChanged(@NotNull String text) {
    updateFlagsComponents(text);
    myContentPanel.revalidate();
    myContentPanel.repaint();
  }

  private void updateFlagsComponents(@Nullable String searchText) {
    // Clear panel and set default layout
    myContentPanel.removeAll();
    myContentPanel.setLayout(getFlagsPanelDefaultLayout());

    boolean isAndroidStudio = IdeInfo.getInstance().isAndroidStudio();
    // Add flags components (by group)
    myGroupedFlags.asMap().entrySet().stream()
      .filter(flag -> {
        return isAndroidStudio || shouldBeIncludedInIdea(flag);
      })
      .sorted(Comparator.comparing(entry -> entry.getKey().getDisplayName()))
      .forEach(entry -> {
        FlagGroup group = entry.getKey();
        List<Flag<?>> flagMatches = entry.getValue().stream().filter(flag -> showFlag(flag, searchText)).collect(Collectors.toList());
        if (flagMatches.isEmpty()) {
          return;
        }

        JPanel groupPanel = new JPanel(new VerticalFlowLayout(5, 0));
        TitledBorder titledBorder = BorderFactory.createTitledBorder(group.getDisplayName());
        titledBorder.setTitleFont(titledBorder.getTitleFont().deriveFont(Font.BOLD, titledBorder.getTitleFont().getSize() + 2));
        groupPanel.setBorder(titledBorder);

        boolean firstFlag = true;
        for (Flag<?> flag : flagMatches) {
          JPanel flagPanel = new JPanel(new VerticalFlowLayout(5, 0));
          if (!firstFlag) {
            flagPanel.add(new JSeparator(SwingConstants.HORIZONTAL));
          }

          JBLabel name = new JBLabel(normalizeFlagText(flag.getDisplayName()));
          name.setFont(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD));

          JBLabel id = new JBLabel("(" + flag.getId() + ")");
          id.setFont(EditorUtil.getEditorFont());

          JTextArea description = new JTextArea(normalizeFlagText(flag.getDescription()));
          description.setFont(StartupUiUtil.getLabelFont());
          description.setLineWrap(true);
          description.setWrapStyleWord(true);
          description.setEditable(false);
          description.setOpaque(false);
          description.setForeground(NamedColorUtil.getInactiveTextColor());

          FlagEditor<?> flagEditor = createFlagEditor(flag);

          HyperlinkLabel resetLink = new HyperlinkLabel("Reset to default");
          resetLink.addHyperlinkListener(new HyperlinkAdapter() {
            @Override
            protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
              flagEditor.flagProperty().clearOverride();
            }
          });
          myBindings.bind(new VisibleProperty(resetLink), flagEditor.flagProperty().isOverridden());

          JPanel flagEditorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
          flagEditorPanel.add(flagEditor.editorComponent());
          flagEditorPanel.add(resetLink);

          JPanel namePanel = new JPanel(new HorizontalLayout(UIUtil.DEFAULT_HGAP));
          namePanel.add(name);
          namePanel.add(id);
          flagPanel.add(namePanel);
          flagPanel.add(description);
          if (!flag.getDefault().getExplanation().isEmpty()) {
            JBLabel defaultValueDescription = new JBLabel(flag.getDefault().getExplanation());
            defaultValueDescription.setIcon(AllIcons.General.BalloonInformation);
            defaultValueDescription.setOpaque(true);
            flagPanel.add(defaultValueDescription);
          }

          flagPanel.add(flagEditorPanel);
          groupPanel.add(flagPanel);

          firstFlag = false;
        }

        myContentPanel.add(groupPanel);
      });

    // If search had no result, make search box red, and display "Nothing to show" (centered)
    boolean emptySearchResult = !StringUtil.isEmptyOrSpaces(searchText) && myContentPanel.getComponentCount() == 0;
    mySearchTextField.getTextEditor().setBackground(emptySearchResult ? LightColors.RED : UIUtil.getTextFieldBackground());
    if (emptySearchResult) {
      JLabel label = new JLabel();
      label.setText(IdeCoreBundle.message("message.nothingToShow"));
      label.setHorizontalAlignment(SwingConstants.CENTER);

      myContentPanel.setLayout(new BorderLayout());
      myContentPanel.add(label, BorderLayout.CENTER);
    }
  }

  private void setupUI() {
    createUIComponents();
    myRootPanel = new JPanel();
    myRootPanel.setLayout(new BorderLayout(0, 0));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new BorderLayout(0, 0));
    myRootPanel.add(panel1, BorderLayout.NORTH);
    mySearchTextField = new SearchTextField();
    panel1.add(mySearchTextField, BorderLayout.CENTER);
    myScrollPane = new JBScrollPane();
    myScrollPane.setHorizontalScrollBarPolicy(31);
    myRootPanel.add(myScrollPane, BorderLayout.CENTER);
    myScrollPane.setViewportView(myContentPanel);
  }

  private static boolean shouldBeIncludedInIdea(Map.Entry<FlagGroup, Collection<Flag<?>>> flag) {
    return switch (flag.getKey().getName()) {
      case "appinsights", "cloud", "firebasetestlab", "google.login", "studiobot" -> false;
      default -> true;
    };
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private @NotNull @NlsContexts.Label String normalizeFlagText(@NlsContexts.Label String flagText) {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      return flagText;
    }

    String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    return flagText.replace("Android Studio", productName).replace("Studio", productName);
  }

  private static boolean showFlag(@NotNull Flag<?> flag, @Nullable String searchText) {
    return StringUtil.isEmptyOrSpaces(searchText) ||
           StringUtil.containsIgnoreCase(flag.getId(), searchText) ||
           StringUtil.containsIgnoreCase(flag.getDisplayName(), searchText) ||
           StringUtil.containsIgnoreCase(flag.getDescription(), searchText);
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
      return createBooleanFlagEditor((Flag<Boolean>)flag);
    }
    else if (flag.get().getClass() == String.class) {
      return createStringFlagEditor((Flag<String>)flag);
    }
    else if (flag.get().getClass() == Integer.class) {
      return createIntegerFlagEditor((Flag<Integer>)flag);
    }
    else if (flag.get().getClass() == Long.class) {
      return createLongFlagEditor((Flag<Long>)flag);
    }
    else if (flag.get().getClass().isEnum()) {
      return createEnumFlagEditor((Flag<Enum>)flag);
    }
    else {
      throw new IllegalStateException(
        String.format("Unhandled flag type (\"%s\"): Flag<%s>", flag.getId(), flag.get().getClass().getSimpleName()));
    }
  }

  @NotNull
  private FlagEditor<String> createStringFlagEditor(Flag<String> flag) {
    return new FlagEditor<>() {
      final FlagProperty<String> myFlagProperty = new FlagProperty<>(flag);

      @NotNull
      @Override
      public FlagProperty<String> flagProperty() {
        return myFlagProperty;
      }

      @NotNull
      @Override
      public JComponent editorComponent() {
        JTextField textField = new JTextField();
        myBindings.bindTwoWay(new TextProperty(textField), myFlagProperty);
        return textField;
      }
    };
  }

  @NotNull
  private FlagEditor<Integer> createIntegerFlagEditor(Flag<Integer> flag) {
    return new FlagEditor<>() {
      final FlagProperty<Integer> myFlagProperty = new FlagProperty<>(flag);

      @NotNull
      @Override
      public FlagProperty<Integer> flagProperty() {
        return myFlagProperty;
      }

      @NotNull
      @Override
      public JComponent editorComponent() {
        JBIntSpinner spinner = new JBIntSpinner(flag.get(), Integer.MIN_VALUE, Integer.MAX_VALUE);
        myBindings.bindTwoWay(new SpinnerValueProperty(spinner), myFlagProperty);
        return spinner;
      }
    };
  }

  @NotNull
  private FlagEditor<Long> createLongFlagEditor(Flag<Long> flag) {
    return new FlagEditor<>() {
      final FlagProperty<Long> myFlagProperty = new FlagProperty<>(flag);

      @NotNull
      @Override
      public FlagProperty<Long> flagProperty() {
        return myFlagProperty;
      }

      @NotNull
      @Override
      public JComponent editorComponent() {
        SpinnerNumberModel model = new SpinnerNumberModel(flag.get(), (Long)Long.MIN_VALUE, (Long)Long.MAX_VALUE, (Long)1L);
        JSpinner spinner = new JSpinner(model);
        myBindings.bindTwoWay(new SpinnerLongValueProperty(spinner), myFlagProperty);
        return spinner;
      }
    };
  }

  @NotNull
  private FlagEditor<Boolean> createBooleanFlagEditor(Flag<Boolean> flag) {
    return new FlagEditor<>() {
      final FlagProperty<Boolean> myFlagProperty = new FlagProperty<>(flag);

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

  private <T extends Enum<T>> FlagEditor<T> createEnumFlagEditor(Flag<T> flag) {
    @SuppressWarnings("unchecked")
    Class<T> enumClass = (Class<T>)flag.get().getClass();

    return new FlagEditor<>() {
      final FlagProperty<T> myFlagProperty = new FlagProperty<>(flag);

      @NotNull
      @Override
      public FlagProperty<T> flagProperty() {
        return myFlagProperty;
      }

      @NotNull
      @Override
      public JComponent editorComponent() {
        ComboBox<T> comboBox = new ComboBox<>(enumClass.getEnumConstants());
        myBindings.bindTwoWay(ObjectProperty.wrap(new SelectedItemProperty<>(comboBox)), myFlagProperty);
        return comboBox;
      }
    };
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
      getOKAction(),
      getCancelAction()
    };
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  private void createUIComponents() {
    myContentPanel = new JPanel(getFlagsPanelDefaultLayout());
  }

  @NotNull
  private static VerticalFlowLayout getFlagsPanelDefaultLayout() {
    return new VerticalFlowLayout(5, 15);
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

    private FlagProperty(Flag<T> flag) {
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
