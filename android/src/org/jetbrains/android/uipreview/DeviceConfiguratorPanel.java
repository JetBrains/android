// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.uipreview;

import static com.android.ide.common.resources.configuration.LocaleQualifier.FAKE_VALUE;

import com.android.ide.common.resources.LocaleManager;
import com.android.ide.common.resources.configuration.CountryCodeQualifier;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.KeyboardStateQualifier;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.resources.configuration.NavigationMethodQualifier;
import com.android.ide.common.resources.configuration.NavigationStateQualifier;
import com.android.ide.common.resources.configuration.NetworkCodeQualifier;
import com.android.ide.common.resources.configuration.NightModeQualifier;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.ScreenDimensionQualifier;
import com.android.ide.common.resources.configuration.ScreenHeightQualifier;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.ide.common.resources.configuration.ScreenRatioQualifier;
import com.android.ide.common.resources.configuration.ScreenRoundQualifier;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.ide.common.resources.configuration.ScreenWidthQualifier;
import com.android.ide.common.resources.configuration.SmallestScreenWidthQualifier;
import com.android.ide.common.resources.configuration.TextInputMethodQualifier;
import com.android.ide.common.resources.configuration.TouchScreenQualifier;
import com.android.ide.common.resources.configuration.UiModeQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.Density;
import com.android.resources.Keyboard;
import com.android.resources.KeyboardState;
import com.android.resources.LayoutDirection;
import com.android.resources.Navigation;
import com.android.resources.NavigationState;
import com.android.resources.NightMode;
import com.android.resources.ResourceEnum;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenRound;
import com.android.resources.ScreenSize;
import com.android.resources.TouchScreen;
import com.android.resources.UiMode;
import com.android.tools.idea.rendering.FlagManager;
import com.google.common.collect.ImmutableMap;
import com.google.common.math.IntMath;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionListener;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DeviceConfiguratorPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(DeviceConfiguratorPanel.class);

  private JBList<ResourceQualifier> myAvailableQualifiersList;
  private JButton myAddQualifierButton;
  private JButton myRemoveQualifierButton;
  private JPanel myQualifierOptionsPanel;

  private final Map<String, MyQualifierEditor<? extends ResourceQualifier>> myEditors = new HashMap<>();

  private final FolderConfiguration myAvailableQualifiersConfig = FolderConfiguration.createDefault();
  private final FolderConfiguration myChosenQualifiersConfig = new FolderConfiguration();
  private FolderConfiguration myActualQualifiersConfig = new FolderConfiguration();
  private JBList<ResourceQualifier> myChosenQualifiersList;

  private final DocumentListener myUpdatingDocumentListener = new DocumentAdapter() {
    @Override
    protected void textChanged(@NotNull DocumentEvent e) {
      applyEditors();
    }
  };
  public final ListSelectionListener myUpdatingListListener = listSelectionEvent -> applyEditors();

  @SuppressWarnings("unchecked")
  public DeviceConfiguratorPanel() {
    super(new BorderLayout());

    createUIComponents();

    myChosenQualifiersConfig.reset();

    for (ResourceQualifier qualifier : myAvailableQualifiersConfig.getQualifiers()) {
      final String name = qualifier.getShortName();
      if (qualifier instanceof CountryCodeQualifier) {
        myEditors.put(name, new MyCountryCodeEditor());
      }
      else if (qualifier instanceof NetworkCodeQualifier) {
        myEditors.put(name, new MyNetworkCodeEditor());
      }
      else if (qualifier instanceof KeyboardStateQualifier) {
        myEditors.put(name, new MyKeyboardStateEditor());
      }
      else if (qualifier instanceof NavigationMethodQualifier) {
        myEditors.put(name, new MyNavigationMethodEditor());
      }
      else if (qualifier instanceof NavigationStateQualifier) {
        myEditors.put(name, new MyNavigationStateEditor());
      }
      else if (qualifier instanceof DensityQualifier) {
        myEditors.put(name, new MyDensityEditor());
      }
      else if (qualifier instanceof ScreenDimensionQualifier) {
        myEditors.put(name, new MyScreenDimensionEditor());
      }
      else if (qualifier instanceof ScreenOrientationQualifier) {
        myEditors.put(name, new MyScreenOrientationEditor());
      }
      else if (qualifier instanceof ScreenRatioQualifier) {
        myEditors.put(name, new MyScreenRatioEditor());
      }
      else if (qualifier instanceof ScreenSizeQualifier) {
        myEditors.put(name, new MyScreenSizeEditor());
      }
      else if (qualifier instanceof TextInputMethodQualifier) {
        myEditors.put(name, new MyTextInputMethodEditor());
      }
      else if (qualifier instanceof TouchScreenQualifier) {
        myEditors.put(name, new MyTouchScreenEditor());
      }
      else if (qualifier instanceof VersionQualifier) {
        myEditors.put(name, new MyVersionEditor());
      }
      else if (qualifier instanceof NightModeQualifier) {
        myEditors.put(name, new MyNightModeEditor());
      }
      else if (qualifier instanceof UiModeQualifier) {
        myEditors.put(name, new MyUiModeEditor());
      }
      else if (qualifier instanceof LocaleQualifier) {
        myEditors.put(name, new MyLocaleEditor());
      }
      else if (qualifier instanceof SmallestScreenWidthQualifier) {
        myEditors.put(name, new MySmallestScreenWidthEditor());
      }
      else if (qualifier instanceof ScreenWidthQualifier) {
        myEditors.put(name, new MyScreenWidthEditor());
      }
      else if (qualifier instanceof ScreenHeightQualifier) {
        myEditors.put(name, new MyScreenHeightEditor());
      }
      else if (qualifier instanceof LayoutDirectionQualifier) {
        myEditors.put(name, new MyLayoutDirectionEditor());
      }
      else if (qualifier instanceof ScreenRoundQualifier) {
        myEditors.put(name, new MyScreenRoundEditor());
      } else {
        LOG.info("Missing editor for qualifier " + qualifier);
      }
    }

    for (String name : myEditors.keySet()) {
      myQualifierOptionsPanel.add(myEditors.get(name).getComponent(), name);
    }

    myAvailableQualifiersList.setCellRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      if (value == null) return;
      label.setText(value.getShortName());
      label.setIcon(getResourceIcon(value));
    }));

    myChosenQualifiersList.setCellRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      if (value == null) return;
      ResourceQualifier qualifier = getActualQualifier(value);
      String shortDisplayValue = qualifier.getShortDisplayValue();
      label.setText(shortDisplayValue != null && !shortDisplayValue.isEmpty() ?
                    shortDisplayValue : qualifier.getShortName() + " (?)");
      label.setIcon(getResourceIcon(qualifier));
    }));

    myAddQualifierButton.addActionListener(e -> {
      final ResourceQualifier selectedQualifier = myAvailableQualifiersList.getSelectedValue();
      if (selectedQualifier == null) {
        return;
      }
      final int index = myAvailableQualifiersList.getSelectedIndex();

      myAvailableQualifiersConfig.removeQualifier(selectedQualifier);
      myChosenQualifiersConfig.addQualifier(selectedQualifier);

      updateLists();
      applyEditors();

      if (index >= 0) {
        myAvailableQualifiersList.setSelectedIndex(Math.min(index, myAvailableQualifiersList.getItemsCount() - 1));
      }
      myChosenQualifiersList.setSelectedValue(selectedQualifier, true);
    });

    myRemoveQualifierButton.addActionListener(e -> {
      final ResourceQualifier selectedQualifier = myChosenQualifiersList.getSelectedValue();
      if (selectedQualifier == null) {
        return;
      }
      final int index = myChosenQualifiersList.getSelectedIndex();

      myChosenQualifiersConfig.removeQualifier(selectedQualifier);
      myAvailableQualifiersConfig.addQualifier(selectedQualifier);
      updateLists();
      applyEditors();

      if (index >= 0) {
        myChosenQualifiersList.setSelectedIndex(Math.min(index, myChosenQualifiersList.getItemsCount() - 1));
      }
    });

    myAvailableQualifiersList.addListSelectionListener(e -> updateButtons());

    myChosenQualifiersList.addListSelectionListener(e -> {
      updateButtons();
      updateQualifierEditor();
    });
  }

  private static final ImmutableMap<String, Icon> OUR_ICONS = ImmutableMap.<String, Icon>builder()
    .put(UiModeQualifier.NAME, StudioIcons.DeviceConfiguration.UI_MODE)
    .put(NightModeQualifier.NAME, StudioIcons.DeviceConfiguration.NIGHT_MODE)
    .put(ScreenDimensionQualifier.NAME, StudioIcons.DeviceConfiguration.DIMENSION)
    .put(DensityQualifier.NAME, StudioIcons.DeviceConfiguration.DENSITY)
    .put(ScreenHeightQualifier.NAME, StudioIcons.DeviceConfiguration.SCREEN_HEIGHT)
    .put(KeyboardStateQualifier.NAME, StudioIcons.DeviceConfiguration.KEYBOARD)
    .put(LocaleQualifier.NAME, StudioIcons.DeviceConfiguration.LOCALE)
    .put(CountryCodeQualifier.NAME, StudioIcons.DeviceConfiguration.COUNTRY_CODE)
    .put(NetworkCodeQualifier.NAME, StudioIcons.DeviceConfiguration.NETWORK_CODE)
    .put(NavigationStateQualifier.NAME, StudioIcons.DeviceConfiguration.NAVIGATION_STATE)
    .put(NavigationMethodQualifier.NAME, StudioIcons.DeviceConfiguration.NAVIGATION_METHOD)
    .put(ScreenOrientationQualifier.NAME, StudioIcons.DeviceConfiguration.ORIENTATION)
    .put(ScreenRatioQualifier.NAME, StudioIcons.DeviceConfiguration.SCREEN_RATIO)
    .put(ScreenSizeQualifier.NAME, StudioIcons.DeviceConfiguration.SCREEN_SIZE)
    .put(SmallestScreenWidthQualifier.NAME, StudioIcons.DeviceConfiguration.SMALLEST_SCREEN_SIZE)
    .put(ScreenWidthQualifier.NAME, StudioIcons.DeviceConfiguration.SCREEN_WIDTH)
    .put(TextInputMethodQualifier.NAME, StudioIcons.DeviceConfiguration.TEXT_INPUT)
    .put(TouchScreenQualifier.NAME, StudioIcons.DeviceConfiguration.TOUCH_SCREEN)
    .put(LayoutDirectionQualifier.NAME, StudioIcons.DeviceConfiguration.LAYOUT_DIRECTION)
    .put(ScreenRoundQualifier.NAME, StudioIcons.DeviceConfiguration.SCREEN_ROUNDNESS)
    // TODO: Get dedicated icon for the API version
    .put(VersionQualifier.NAME, StudioIcons.DeviceConfiguration.VERSION)
    .build();

  @Nullable
  private static Icon getResourceIcon(ResourceQualifier qualifier) {
    return OUR_ICONS.get(qualifier.getName());
  }

  public void init(@NotNull FolderConfiguration config) {
    myChosenQualifiersConfig.set(config);
    myAvailableQualifiersConfig.substract(config);

    for (ResourceQualifier qualifier : config.getQualifiers()) {
      final MyQualifierEditor<ResourceQualifier> editor = (MyQualifierEditor<ResourceQualifier>)myEditors.get(qualifier.getShortName());
      if (editor != null) {
        editor.reset(qualifier);
      }
    }
  }

  public abstract void applyEditors();

  public void updateAll() {
    updateLists();
    updateButtons();
    updateQualifierEditor();
    applyEditors();
  }

  public void doApplyEditors() throws InvalidOptionValueException {
    try {
      final FolderConfiguration newConfig = new FolderConfiguration();

      for (ResourceQualifier qualifier : myChosenQualifiersConfig.getQualifiers()) {
        final MyQualifierEditor<? extends ResourceQualifier> editor = myEditors.get(qualifier.getShortName());
        if (editor != null) {
          newConfig.addQualifier(editor.apply());
        }
      }
      myActualQualifiersConfig = newConfig;
    }
    finally {
      myAvailableQualifiersList.repaint();
      myChosenQualifiersList.repaint();
    }
  }

  private ResourceQualifier getActualQualifier(ResourceQualifier qualifier) {
    for (ResourceQualifier qualifier1 : myActualQualifiersConfig.getQualifiers()) {
      if (Objects.equals(qualifier1.getShortName(), qualifier.getShortName())) {
        return qualifier1;
      }
    }
    return qualifier;
  }

  private void updateQualifierEditor() {
    final ResourceQualifier selectedQualifier = myChosenQualifiersList.getSelectedValue();
    if (selectedQualifier != null && myEditors.containsKey(selectedQualifier.getShortName())) {
      final CardLayout layout = (CardLayout)myQualifierOptionsPanel.getLayout();
      layout.show(myQualifierOptionsPanel, selectedQualifier.getShortName());
      myQualifierOptionsPanel.setVisible(true);
    }
    else {
      myQualifierOptionsPanel.setVisible(false);
    }
  }

  private void updateButtons() {
    myAddQualifierButton.setEnabled(myAvailableQualifiersList.getSelectedIndex() >= 0);
    myRemoveQualifierButton.setEnabled(myChosenQualifiersList.getSelectedIndex() >= 0);
  }

  private void updateLists() {
    Object qualifier = myAvailableQualifiersList.getSelectedValue();
    final ResourceQualifier[] availableQualifiers = filterUnsupportedQualifiers(myAvailableQualifiersConfig.getQualifiers());
    myAvailableQualifiersList.setModel(new CollectionListModel<>(availableQualifiers));
    myAvailableQualifiersList.setSelectedValue(qualifier, true);

    if (myAvailableQualifiersList.getSelectedValue() == null && myAvailableQualifiersList.getItemsCount() > 0) {
      myAvailableQualifiersList.setSelectedIndex(0);
    }

    qualifier = myChosenQualifiersList.getSelectedValue();
    final ResourceQualifier[] chosenQualifiers = filterUnsupportedQualifiers(myChosenQualifiersConfig.getQualifiers());
    myChosenQualifiersList.setModel(new CollectionListModel<>(chosenQualifiers));
    myChosenQualifiersList.setSelectedValue(qualifier, true);

    if (myChosenQualifiersList.getSelectedValue() == null && myChosenQualifiersList.getItemsCount() > 0) {
      myChosenQualifiersList.setSelectedIndex(0);
    }
  }

  private ResourceQualifier[] filterUnsupportedQualifiers(ResourceQualifier[] qualifiers) {
    return Arrays.stream(qualifiers)
      .filter(q -> myEditors.containsKey(q.getShortName()))
      .toArray(ResourceQualifier[]::new);
  }

  public FolderConfiguration getConfiguration() {
    return myActualQualifiersConfig;
  }

  private void createUIComponents() {
    myQualifierOptionsPanel = new JPanel(new CardLayout()) {
      @Override
      public Dimension getPreferredSize() {
        // Simulate a width weight of 50%
        return new Dimension(getParent().getWidth() / 2, -1);
      }
    };

    final JPanel leftPanel = new JPanel(new BorderLayout(JBUI.scale(5), JBUI.scale(5)));
    myAvailableQualifiersList = new JBList<>();
    myAvailableQualifiersList.setMinimumSize(JBUI.size(10, 10));
    JBLabel label = new JBLabel(AndroidBundle.message("android.layout.preview.edit.configuration.available.qualifiers.label"));
    label.setLabelFor(myAvailableQualifiersList);
    leftPanel.add(label, BorderLayout.NORTH);
    leftPanel.add(new JBScrollPane(myAvailableQualifiersList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                   ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);

    final JPanel rightPanel = new JPanel(new BorderLayout(JBUI.scale(5), JBUI.scale(5)));
    myChosenQualifiersList = new JBList<>();
    myChosenQualifiersList.setMinimumSize(JBUI.size(10, 10));
    label = new JBLabel(AndroidBundle.message("android.layout.preview.edit.configuration.chosen.qualifiers.label"));
    label.setLabelFor(myChosenQualifiersList);
    rightPanel.add(label, BorderLayout.NORTH);
    rightPanel.add(new JBScrollPane(myChosenQualifiersList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);

    final JPanel buttonsPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE, 0, 0, true, false));
    myAddQualifierButton = new JButton(">>");
    buttonsPanel.add(myAddQualifierButton);
    myRemoveQualifierButton = new JButton("<<");
    buttonsPanel.add(myRemoveQualifierButton);

    add(getListsPanel(buttonsPanel, leftPanel, rightPanel), BorderLayout.CENTER);
    add(myQualifierOptionsPanel, BorderLayout.EAST);
  }

  @NotNull
  private static JPanel getListsPanel(JPanel buttonsPanel, JPanel leftPanel, JPanel rightPanel) {
    final int gap = 5;

    final JPanel listsPanel = new JPanel(new AbstractLayoutManager() {
      @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
      @Override
      public Dimension preferredLayoutSize(Container target) {
        return new Dimension(-1, -1);
      }

      @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
      @Override
      public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
          final Insets insets = target.getInsets();
          int top = insets.top;
          int bottom = target.getHeight() - insets.bottom;
          int left = insets.left;
          int right = target.getWidth() - insets.right;

          final int middleWidth = buttonsPanel.getPreferredSize().width + gap * 2;
          final int listWidth = (right - left - middleWidth) / 2;
          final int height = bottom - top;

          leftPanel.setSize(listWidth, height);
          rightPanel.setSize(listWidth, height);
          buttonsPanel.setSize(middleWidth, height);

          leftPanel.setBounds(left, top, listWidth, height);
          rightPanel.setBounds(right - listWidth, top, listWidth, height);
          buttonsPanel.setBounds(left + listWidth + gap, top, middleWidth - gap * 2, height);
        }
      }
    });
    listsPanel.add(leftPanel);
    listsPanel.add(buttonsPanel);
    listsPanel.add(rightPanel);
    return listsPanel;
  }

  private abstract static class MyQualifierEditor<T extends ResourceQualifier> {
    abstract JComponent getComponent();

    abstract void reset(@NotNull T qualifier);

    @NotNull
    abstract T apply() throws InvalidOptionValueException;
  }

  private class MyCountryCodeEditor extends MyQualifierEditor<CountryCodeQualifier> {
    private static final int NUM_DIGITS = 3;
    private static final int MIN_VALUE = IntMath.pow(10, NUM_DIGITS - 1);
    private static final int MAX_VALUE = IntMath.pow(10, NUM_DIGITS) - 1;
    private final JTextField myTextField = new JTextField(NUM_DIGITS);

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel("<html><body>Mobile country code<br>(" + NUM_DIGITS + " digits):</body></html>");
      label.setLabelFor(myTextField);
      myTextField.setColumns(NUM_DIGITS);
      myTextField.getDocument().addDocumentListener(myUpdatingDocumentListener);
      panel.add(label);
      panel.add(myTextField);
      return panel;
    }

    @Override
    void reset(@NotNull CountryCodeQualifier qualifier) {
      myTextField.setText(Integer.toString(qualifier.getCode()));
    }

    @NotNull
    @Override
    CountryCodeQualifier apply() throws InvalidOptionValueException {
      if (myTextField.getText().length() != NUM_DIGITS) {
        throw new InvalidOptionValueException("Country code must contain " + NUM_DIGITS + " digits");
      }
      try {
        final int code = Integer.parseInt(myTextField.getText());
        if (code < MIN_VALUE || code > MAX_VALUE) {
          throw new InvalidOptionValueException("Incorrect country code");
        }
        return new CountryCodeQualifier(code);
      }
      catch (NumberFormatException e) {
        throw new InvalidOptionValueException("Country code must be a number");
      }
    }
  }

  private class MyNetworkCodeEditor extends MyQualifierEditor<NetworkCodeQualifier> {
    private static final int MAX_DIGITS = 3;
    private static final int MAX_VALUE = IntMath.pow(10, MAX_DIGITS) - 1;
    private final JTextField myTextField = new JTextField(MAX_DIGITS);

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel("<html><body>Mobile network code<br>(1-" + MAX_DIGITS + " digits):</body></html>");
      panel.add(label);
      label.setLabelFor(myTextField);
      myTextField.getDocument().addDocumentListener(myUpdatingDocumentListener);
      panel.add(myTextField);
      return panel;
    }

    @Override
    void reset(@NotNull NetworkCodeQualifier qualifier) {
      myTextField.setText(Integer.toString(qualifier.getCode()));
    }

    @NotNull
    @Override
    NetworkCodeQualifier apply() throws InvalidOptionValueException {
      try {
        final int code = Integer.parseInt(myTextField.getText());
        if (code <= 0 || code > MAX_VALUE) {
          throw new InvalidOptionValueException("Incorrect network code");
        }
        return new NetworkCodeQualifier(code);
      }
      catch (NumberFormatException e) {
        throw new InvalidOptionValueException("Network code must be a number");
      }
    }
  }

  private abstract class MyComboBoxEditor<T extends ResourceQualifier, U extends ResourceEnum> extends MyQualifierEditor<T> {
    private final ComboBoxModel<U> myModel;
    private final ComboBox<U> myComboBox = new ComboBox<>();

    protected MyComboBoxEditor(@NotNull ComboBoxModel<U> model) {
      myModel = model;
    }

    @Override
    JComponent getComponent() {
      myComboBox.addActionListener(e -> applyEditors());
      myComboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> label.setText(value.getShortDisplayValue())));

      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel(getCaption());
      label.setLabelFor(myComboBox);
      myComboBox.setModel(myModel);
      panel.add(label);
      panel.add(myComboBox);
      return panel;
    }

    @NotNull
    protected abstract String getCaption();

    @Override
    void reset(@NotNull T qualifier) {
      final U value = getValue(qualifier);
      if (value != null) {
        myComboBox.setSelectedItem(value);
      }
      else if (myComboBox.getItemCount() > 0) {
        myComboBox.setSelectedIndex(0);
      }
    }

    protected abstract U getValue(@NotNull T qualifier);

    @NotNull
    protected abstract T getQualifier(@NotNull U value);

    @NotNull
    protected abstract String getErrorMessage();

    @NotNull
    @Override
    T apply() throws InvalidOptionValueException {
      final U selectedItem = (U)myComboBox.getSelectedItem();
      if (selectedItem == null) {
        throw new InvalidOptionValueException(getErrorMessage());
      }
      return getQualifier(selectedItem);
    }
  }

  private abstract class MyEnumBasedEditor<T extends ResourceQualifier, U extends Enum<U> & ResourceEnum> extends MyComboBoxEditor<T, U> {
    private final ComboBox<U> myComboBox = new ComboBox<>();

    protected MyEnumBasedEditor(@NotNull Class<U> enumClass) {
      super(new EnumComboBoxModel<>(enumClass));
    }

    @Override
    void reset(@NotNull T qualifier) {
      final U value = getValue(qualifier);
      if (value != null) {
        myComboBox.setSelectedItem(value);
      }
      else if (myComboBox.getItemCount() > 0) {
        myComboBox.setSelectedIndex(0);
      }
    }
  }

  private class MyScreenSizeEditor extends MyEnumBasedEditor<ScreenSizeQualifier, ScreenSize> {
    private MyScreenSizeEditor() {
      super(ScreenSize.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Screen size:";
    }

    @NotNull
    @Override
    protected ScreenSize getValue(@NotNull ScreenSizeQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenSizeQualifier getQualifier(@NotNull ScreenSize value) {
      return new ScreenSizeQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify screen size";
    }
  }

  private class MyScreenOrientationEditor extends MyEnumBasedEditor<ScreenOrientationQualifier, ScreenOrientation> {
    private MyScreenOrientationEditor() {
      super(ScreenOrientation.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Screen orientation:";
    }

    @NotNull
    @Override
    protected ScreenOrientation getValue(@NotNull ScreenOrientationQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenOrientationQualifier getQualifier(@NotNull ScreenOrientation value) {
      return new ScreenOrientationQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify screen orientation";
    }
  }

  private class MyScreenRatioEditor extends MyEnumBasedEditor<ScreenRatioQualifier, ScreenRatio> {
    private MyScreenRatioEditor() {
      super(ScreenRatio.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Screen ratio:";
    }

    @NotNull
    @Override
    protected ScreenRatio getValue(@NotNull ScreenRatioQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenRatioQualifier getQualifier(@NotNull ScreenRatio value) {
      return new ScreenRatioQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify screen ratio";
    }
  }

  /**
   * Specialized combo box model which filters out enum values that are marked as not interesting. This
   * is to discourage app developers from creating specialized resource folders for specific densities.
   */
  private static class DensityComboBoxModel extends AbstractListModel<Density> implements ComboBoxModel<Density> {
    private final List<Density> myList;
    private Density mySelected;

    private DensityComboBoxModel() {
      myList = new ArrayList<>();
      for (Density density : Density.values()) {
        if (density.isRecommended()) {
          myList.add(density);
        }
      }
      mySelected = myList.get(0);
    }

    @Override
    public int getSize() {
      return myList.size();
    }

    @Override
    public Density getElementAt(int index) {
      return myList.get(index);
    }

    @Override
    public void setSelectedItem(Object item) {
      @SuppressWarnings("unchecked") Density e = (Density)item;
      setSelectedItem(e);
    }

    public void setSelectedItem(Density item) {
      mySelected = item;
      fireContentsChanged(this, 0, getSize());
    }

    @Override
    public Density getSelectedItem() {
      return mySelected;
    }
  }

  private class MyDensityEditor extends MyComboBoxEditor<DensityQualifier, Density> {
    private MyDensityEditor() {
      super(new DensityComboBoxModel());
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Density:";
    }

    @NotNull
    @Override
    protected Density getValue(@NotNull DensityQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected DensityQualifier getQualifier(@NotNull Density value) {
      return new DensityQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify density";
    }
  }

  private class MyTouchScreenEditor extends MyEnumBasedEditor<TouchScreenQualifier, TouchScreen> {
    private MyTouchScreenEditor() {
      super(TouchScreen.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Touch screen:";
    }

    @NotNull
    @Override
    protected TouchScreen getValue(@NotNull TouchScreenQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected TouchScreenQualifier getQualifier(@NotNull TouchScreen value) {
      return new TouchScreenQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify touch screen";
    }
  }

  private class MyKeyboardStateEditor extends MyEnumBasedEditor<KeyboardStateQualifier, KeyboardState> {
    private MyKeyboardStateEditor() {
      super(KeyboardState.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Keyboard state:";
    }

    @NotNull
    @Override
    protected KeyboardState getValue(@NotNull KeyboardStateQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected KeyboardStateQualifier getQualifier(@NotNull KeyboardState value) {
      return new KeyboardStateQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify keyboard state";
    }
  }

  private class MyTextInputMethodEditor extends MyEnumBasedEditor<TextInputMethodQualifier, Keyboard> {
    private MyTextInputMethodEditor() {
      super(Keyboard.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Text input method:";
    }

    @NotNull
    @Override
    protected Keyboard getValue(@NotNull TextInputMethodQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected TextInputMethodQualifier getQualifier(@NotNull Keyboard value) {
      return new TextInputMethodQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify text input method";
    }
  }

  private class MyNavigationStateEditor extends MyEnumBasedEditor<NavigationStateQualifier, NavigationState> {

    private MyNavigationStateEditor() {
      super(NavigationState.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Navigation state:";
    }

    @NotNull
    @Override
    protected NavigationState getValue(@NotNull NavigationStateQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected NavigationStateQualifier getQualifier(@NotNull NavigationState value) {
      return new NavigationStateQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify navigation state";
    }
  }

  private class MyNavigationMethodEditor extends MyEnumBasedEditor<NavigationMethodQualifier, Navigation> {
    private MyNavigationMethodEditor() {
      super(Navigation.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Navigation method:";
    }

    @NotNull
    @Override
    protected Navigation getValue(@NotNull NavigationMethodQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected NavigationMethodQualifier getQualifier(@NotNull Navigation value) {
      return new NavigationMethodQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify navigation method";
    }
  }

  private class MyLayoutDirectionEditor extends MyEnumBasedEditor<LayoutDirectionQualifier, LayoutDirection> {
    private MyLayoutDirectionEditor() {
      super(LayoutDirection.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Layout direction:";
    }

    @NotNull
    @Override
    protected LayoutDirection getValue(@NotNull LayoutDirectionQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected LayoutDirectionQualifier getQualifier(@NotNull LayoutDirection value) {
      return new LayoutDirectionQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify layout direction";
    }
  }

  private class MyScreenDimensionEditor extends MyQualifierEditor<ScreenDimensionQualifier> {
    private final JTextField mySizeField1 = new JTextField();
    private final JTextField mySizeField2 = new JTextField();

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel("Screen dimension in dp:");
      label.setLabelFor(mySizeField1);
      panel.add(label);
      panel.add(mySizeField1);
      panel.add(mySizeField2);
      mySizeField1.getDocument().addDocumentListener(myUpdatingDocumentListener);
      mySizeField2.getDocument().addDocumentListener(myUpdatingDocumentListener);
      return panel;
    }

    @Override
    void reset(@NotNull ScreenDimensionQualifier qualifier) {
      final int value1 = qualifier.getValue1();
      if (value1 >= 0) {
        mySizeField1.setText(Integer.toString(value1));
      }

      final int value2 = qualifier.getValue2();
      if (value2 >= 0) {
        mySizeField2.setText(Integer.toString(value2));
      }
    }

    @NotNull
    @Override
    ScreenDimensionQualifier apply() throws InvalidOptionValueException {
      try {
        final int size1 = Integer.parseInt(mySizeField1.getText());
        final int size2 = Integer.parseInt(mySizeField2.getText());

        if (size1 <= 0 || size2 <= 0) {
          throw new InvalidOptionValueException("Incorrect screen dimension");
        }
        return new ScreenDimensionQualifier(size1, size2);
      }
      catch (NumberFormatException e) {
        LOG.debug(e);
        throw new InvalidOptionValueException("Incorrect screen dimension");
      }
    }
  }

  private class MyVersionEditor extends MyQualifierEditor<VersionQualifier> {
    private final JTextField myTextField = new JTextField(3);

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel("Platform API level:");
      panel.add(label);
      label.setLabelFor(myTextField);
      myTextField.getDocument().addDocumentListener(myUpdatingDocumentListener);
      panel.add(myTextField);
      return panel;
    }

    @Override
    void reset(@NotNull VersionQualifier qualifier) {
      myTextField.setText(Integer.toString(qualifier.getVersion()));
    }

    @NotNull
    @Override
    VersionQualifier apply() throws InvalidOptionValueException {
      try {
        final int apiLevel = Integer.parseInt(myTextField.getText().trim());
        if (apiLevel < 0) {
          throw new InvalidOptionValueException("Incorrect API level");
        }
        return new VersionQualifier(apiLevel);
      }
      catch (NumberFormatException e) {
        throw new InvalidOptionValueException("Incorrect API level");
      }
    }
  }

  private class MyNightModeEditor extends MyEnumBasedEditor<NightModeQualifier, NightMode> {
    protected MyNightModeEditor() {
      super(NightMode.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Night mode:";
    }

    @Override
    protected NightMode getValue(@NotNull NightModeQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected NightModeQualifier getQualifier(@NotNull NightMode value) {
      return new NightModeQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify night mode";
    }
  }

  private class MyUiModeEditor extends MyEnumBasedEditor<UiModeQualifier, UiMode> {
    private MyUiModeEditor() {
      super(UiMode.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "UI mode:";
    }

    @Override
    protected UiMode getValue(@NotNull UiModeQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected UiModeQualifier getQualifier(@NotNull UiMode value) {
      return new UiModeQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify UI mode";
    }
  }

  private class MyLocaleEditor extends MyQualifierEditor<LocaleQualifier> {
    private final JBList<String> myLanguageList = new JBList<>();
    private final JBList<String> myRegionList = new JBList<>();
    private JBCheckBox myShowAllRegions;
    private JBLabel myWarningsLabel;

    // Special language comparator: We want to prefer 2-letter language codes.
    private static final Comparator<String> LOCALE_COMPARATOR =
      Comparator.comparingInt(String::length).thenComparing(String.CASE_INSENSITIVE_ORDER);

    @Override
    JComponent getComponent() {
      GridBagConstraints gridBagConstraints;
      JPanel pane = new JPanel(new GridBagLayout());
      pane.setBorder(JBUI.Borders.emptyLeft(20)); // pad 20 pixels on the left hand side to space out the two views

      myShowAllRegions = new JBCheckBox("Show All Regions", false);
      myWarningsLabel = new JBLabel("BCP 47 tags (3-letter languages or regions) will only match on API 21");
      myWarningsLabel.setIcon(AllIcons.General.BalloonWarning);
      myWarningsLabel.setVisible(false);
      JBLabel languageLabel = new JBLabel("Language:");
      JBLabel languageTip = new JBLabel("Tip: Type in list to filter");
      JBLabel regionLabel = new JBLabel("Specific Region Only:");

      SortedListModel<String> languageModel = new SortedListModel<>(LOCALE_COMPARATOR);
      languageModel.addAll(LocaleManager.getLanguageCodes(true));
      myLanguageList.setModel(languageModel);
      myLanguageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myLanguageList.setCellRenderer(FlagManager.get().getLanguageCodeCellRenderer());
      JBScrollPane scroll = new JBScrollPane(myLanguageList);
      JComponent languagePane = ListWithFilter.wrap(myLanguageList, scroll, FlagManager.getLanguageNameMapper());
      languageLabel.setLabelFor(myLanguageList);
      languageTip.setFont(JBUI.Fonts.miniFont());

      myRegionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myRegionList.setCellRenderer(FlagManager.get().getRegionCodeCellRenderer());
      updateRegionList(null);
      // We can't enable filter lists on the region mode, since fast search doesn't seem to
      // work on models that can change after creation.
      JComponent regionPane = new JBScrollPane(myRegionList);

      Insets insets = JBUI.insetsLeft(20);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
      pane.add(languageLabel, gridBagConstraints);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gridBagConstraints.anchor = GridBagConstraints.LINE_START;
      gridBagConstraints.insets = insets;
      pane.add(regionLabel, gridBagConstraints);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.fill = GridBagConstraints.BOTH;
      gridBagConstraints.weightx = 1;
      gridBagConstraints.weighty = 1;
      pane.add(languagePane, gridBagConstraints);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gridBagConstraints.fill = GridBagConstraints.BOTH;
      gridBagConstraints.weightx = 1;
      gridBagConstraints.weighty = 1;
      gridBagConstraints.insets = insets;
      pane.add(regionPane, gridBagConstraints);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.anchor = GridBagConstraints.EAST;
      pane.add(languageTip, gridBagConstraints);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gridBagConstraints.anchor = GridBagConstraints.LINE_START;
      gridBagConstraints.insets = insets;
      pane.add(myShowAllRegions, gridBagConstraints);
      gridBagConstraints = new GridBagConstraints();
      gridBagConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gridBagConstraints.anchor = GridBagConstraints.LINE_START;
      pane.add(myWarningsLabel, gridBagConstraints);

      myLanguageList.addListSelectionListener(myUpdatingListListener);
      myRegionList.addListSelectionListener(myUpdatingListListener);
      // If selecting languages, attempt to pick relevant regions, if applicable
      myLanguageList.addListSelectionListener(listSelectionEvent -> updateRegionList(myLanguageList.getSelectedValue()));
      myShowAllRegions.addItemListener(e -> updateRegionList(myLanguageList.getSelectedValue()));
      return pane;
    }

    /** Populate the region list based on an optional language selection */
    private void updateRegionList(@Nullable String languageCode) {
      SortedListModel<String> regionModel = new SortedListModel<>(
        // First FAKE_VALUE, but for boolean-valued comparators, false is first.
        Comparator.<String, Boolean>comparing(FAKE_VALUE::equals).reversed()
          .thenComparing(LOCALE_COMPARATOR));
      regionModel.add(FAKE_VALUE);
      if (!myShowAllRegions.isSelected() && languageCode != null) {
        regionModel.addAll(LocaleManager.getRelevantRegions(languageCode));
      } else {
        regionModel.addAll(LocaleManager.getRegionCodes(true));
      }
      myRegionList.setModel(regionModel);
      if (languageCode != null && regionModel.getSize() > 0) {
        myRegionList.setSelectedIndex(0);
      }
    }

    @Override
    void reset(@NotNull LocaleQualifier qualifier) {
      if (qualifier.isValid() && !qualifier.hasFakeValue()) {
        String language = qualifier.getLanguage();
        String region = qualifier.getRegion();
        ListModel<String> languageModel = myLanguageList.getModel();
        ListModel<String> regionModel = myRegionList.getModel();

        if (language != null) {
          for (int i = 0, n = languageModel.getSize(); i < n; i++) {
            if (language.equals(languageModel.getElementAt(i))) {
              myLanguageList.setSelectedIndex(i);
              break;
            }
          }
        }
        if (region != null) {
          boolean found = false;
          for (int i = 0, n = regionModel.getSize(); i < n; i++) {
            if (region.equals(regionModel.getElementAt(i))) {
              myRegionList.setSelectedIndex(i);
              found = true;
              break;
            }
          }
          if (!found && !myShowAllRegions.isSelected()) {
            myShowAllRegions.setSelected(true);
            for (int i = 0, n = regionModel.getSize(); i < n; i++) {
              if (region.equals(regionModel.getElementAt(i))) {
                myRegionList.setSelectedIndex(i);
                break;
              }
            }
          }
        } else {
          myRegionList.setSelectedIndex(-1);
        }
      } else {
        myLanguageList.setSelectedIndex(-1);
        myRegionList.setSelectedIndex(-1);
      }
    }

    @NotNull
    @Override
    LocaleQualifier apply() throws InvalidOptionValueException {
      String selectedLanguage = myLanguageList.getSelectedValue();
      if (selectedLanguage == null) {
        throw new InvalidOptionValueException("Select a language tag");
      }
      String selectedRegion = myRegionList.getSelectedValue();
      if (FAKE_VALUE.equals(selectedRegion)) {
        selectedRegion = null;
      }
      myWarningsLabel.setVisible(selectedLanguage.length() > 2 || selectedRegion != null && selectedRegion.length() > 2);
      return new LocaleQualifier(null, selectedLanguage, selectedRegion, null);
    }
  }

  private abstract class MySizeEditorBase<T extends ResourceQualifier> extends MyQualifierEditor<T> {
    private final JTextField myTextField = new JTextField(3);
    private final String myLabelText;

    protected MySizeEditorBase(String labelText) {
      myLabelText = labelText;
    }

    @Override
    JComponent getComponent() {
      final JPanel panel = new JPanel(new VerticalFlowLayout());
      final JBLabel label = new JBLabel(myLabelText);
      panel.add(label);
      label.setLabelFor(myTextField);
      myTextField.getDocument().addDocumentListener(myUpdatingDocumentListener);
      panel.add(myTextField);
      return panel;
    }

    @Override
    void reset(@NotNull T qualifier) {
      myTextField.setText(Integer.toString(getValue(qualifier)));
    }

    protected abstract int getValue(@NotNull T qualifier);

    @NotNull
    protected abstract T createQualifier(int value);

    protected abstract String getErrorMessage();

    @NotNull
    @Override
    T apply() throws InvalidOptionValueException {
      try {
        final int value = Integer.parseInt(myTextField.getText().trim());
        if (value < 0) {
          throw new InvalidOptionValueException(getErrorMessage());
        }
        return createQualifier(value);
      }
      catch (NumberFormatException e) {
        throw new InvalidOptionValueException(getErrorMessage());
      }
    }
  }

  private class MySmallestScreenWidthEditor extends MySizeEditorBase<SmallestScreenWidthQualifier> {
    private MySmallestScreenWidthEditor() {
      super("Smallest screen width:");
    }

    @Override
    protected int getValue(@NotNull SmallestScreenWidthQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected SmallestScreenWidthQualifier createQualifier(int value) {
      return new SmallestScreenWidthQualifier(value);
    }

    @Override
    protected String getErrorMessage() {
      return "Incorrect smallest screen width";
    }
  }

  private class MyScreenWidthEditor extends MySizeEditorBase<ScreenWidthQualifier> {
    private MyScreenWidthEditor() {
      super("Screen width:");
    }

    @Override
    protected int getValue(@NotNull ScreenWidthQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenWidthQualifier createQualifier(int value) {
      return new ScreenWidthQualifier(value);
    }

    @Override
    protected String getErrorMessage() {
      return "Incorrect screen width";
    }
  }

  private class MyScreenHeightEditor extends MySizeEditorBase<ScreenHeightQualifier> {
    private MyScreenHeightEditor() {
      super("Screen height:");
    }

    @Override
    protected int getValue(@NotNull ScreenHeightQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenHeightQualifier createQualifier(int value) {
      return new ScreenHeightQualifier(value);
    }

    @Override
    protected String getErrorMessage() {
      return "Incorrect screen height";
    }
  }

  private class MyScreenRoundEditor extends MyEnumBasedEditor<ScreenRoundQualifier, ScreenRound> {
    private MyScreenRoundEditor() {
      super(ScreenRound.class);
    }

    @NotNull
    @Override
    protected String getCaption() {
      return "Screen roundness:";
    }

    @Override
    protected ScreenRound getValue(@NotNull ScreenRoundQualifier qualifier) {
      return qualifier.getValue();
    }

    @NotNull
    @Override
    protected ScreenRoundQualifier getQualifier(@NotNull ScreenRound value) {
      return new ScreenRoundQualifier(value);
    }

    @NotNull
    @Override
    protected String getErrorMessage() {
      return "Specify the screen shape";
    }
  }
}
