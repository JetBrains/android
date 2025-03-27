package org.jetbrains.android.formatter;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidXmlCodeStylePanel extends CodeStyleAbstractPanel {
  private final JPanel myPanel;
  private final JBCheckBox myUseCustomSettings;
  private final List<MyFileSpecificPanel> myCodeStylePanels;
  private final JPanel myFileSpecificCodeStylesPanel;

  AndroidXmlCodeStylePanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(XMLLanguage.INSTANCE, currentSettings, settings);
    myPanel = new JPanel(new BorderLayout());
    myPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    JPanel centerPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
    myPanel.add(centerPanel, BorderLayout.CENTER);
    myUseCustomSettings = new JBCheckBox("Use custom formatting settings for Android XML files");
    myPanel.add(myUseCustomSettings, BorderLayout.NORTH);

    myCodeStylePanels = new ArrayList<MyFileSpecificPanel>();

    myCodeStylePanels.add(new ManifestCodeStylePanel());
    myCodeStylePanels.add(new LayoutCodeStylePanel());
    myCodeStylePanels.add(new ValueResourcesCodeStylePanel());
    myCodeStylePanels.add(new OtherCodeStylePanel());

    myFileSpecificCodeStylesPanel = new JPanel(new GridLayout((myCodeStylePanels.size() + 1) / 2, 2, 15, 0));
    centerPanel.add(myFileSpecificCodeStylesPanel);

    myUseCustomSettings.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myFileSpecificCodeStylesPanel, myUseCustomSettings.isSelected(), true);
      }
    });

    for (MyFileSpecificPanel panel : myCodeStylePanels) {
      final JPanel titledPanel = new JPanel(new BorderLayout());
      titledPanel.setBorder(IdeBorderFactory.createTitledBorder(panel.getTitle()));
      titledPanel.add(panel, BorderLayout.CENTER);
      myFileSpecificCodeStylesPanel.add(titledPanel);
    }
  }

  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Nullable
  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return null;
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return XmlFileType.INSTANCE;
  }

  @Nullable
  @Override
  protected String getPreviewText() {
    return null;
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    final AndroidXmlCodeStyleSettings androidSettings = AndroidXmlCodeStyleSettings.getInstance(settings);
    androidSettings.USE_CUSTOM_SETTINGS = myUseCustomSettings.isSelected();

    for (MyFileSpecificPanel panel : myCodeStylePanels) {
      panel.apply(androidSettings);
    }
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    final AndroidXmlCodeStyleSettings androidSettings = AndroidXmlCodeStyleSettings.getInstance(settings);

    if (androidSettings.USE_CUSTOM_SETTINGS != myUseCustomSettings.isSelected()) {
      return true;
    }

    for (MyFileSpecificPanel panel : myCodeStylePanels) {
      if (panel.isModified(androidSettings)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    final AndroidXmlCodeStyleSettings androidSettings = AndroidXmlCodeStyleSettings.getInstance(settings);
    myUseCustomSettings.setSelected(androidSettings.USE_CUSTOM_SETTINGS);
    UIUtil.setEnabled(myFileSpecificCodeStylesPanel, androidSettings.USE_CUSTOM_SETTINGS, true);

    for (MyFileSpecificPanel panel : myCodeStylePanels) {
      panel.resetImpl(androidSettings);
    }
  }

  public abstract static class MyFileSpecificPanel<T extends AndroidXmlCodeStyleSettings.MySettings> extends JPanel {
    private JPanel myPanel;
    private JPanel myAdditionalOptionsPanel;
    private JComboBox myWrapAttributesCombo;

    protected JBCheckBox myInsertLineBreakBeforeFirstAttributeCheckBox;
    protected JBCheckBox myInsertLineBreakBeforeNamespaceDeclarationCheckBox;
    protected JBCheckBox myInsertLineBreakAfterLastAttributeCheckbox;

    private final String myTitle;
    private final ContextSpecificSettingsProviders.Provider<T> mySettingsProvider;

    protected MyFileSpecificPanel(String title, ContextSpecificSettingsProviders.Provider<T> provider) {
      setupUI();
      myTitle = title;
      mySettingsProvider = provider;
      myInsertLineBreakBeforeFirstAttributeCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
          UIUtil.setEnabled(myInsertLineBreakBeforeNamespaceDeclarationCheckBox, myInsertLineBreakBeforeFirstAttributeCheckBox.isSelected(),
                            true);
        }
      });
    }

    protected void init() {
      final JPanel panel = getAdditionalOptionsPanel();

      if (panel != null) {
        myAdditionalOptionsPanel.add(panel, BorderLayout.CENTER);
      }
      else {
        myAdditionalOptionsPanel.setVisible(false);
      }
      setLayout(new BorderLayout());
      add(myPanel, BorderLayout.CENTER);

      fillWrappingCombo(myWrapAttributesCombo);
    }

    @Nullable
    public JPanel getAdditionalOptionsPanel() {
      return null;
    }

    public final void apply(AndroidXmlCodeStyleSettings settings) {
      apply(mySettingsProvider.getSettings(settings));
    }

    protected void apply(T s) {
      s.WRAP_ATTRIBUTES = CodeStyleSettings.WrapStyle.getId((CodeStyleSettings.WrapStyle)myWrapAttributesCombo.getSelectedItem());
      s.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = myInsertLineBreakBeforeFirstAttributeCheckBox.isSelected();
      s.INSERT_LINE_BREAK_BEFORE_NAMESPACE_DECLARATION = myInsertLineBreakBeforeNamespaceDeclarationCheckBox.isSelected();
      s.INSERT_LINE_BREAK_AFTER_LAST_ATTRIBUTE = myInsertLineBreakAfterLastAttributeCheckbox.isSelected();
    }

    public final boolean isModified(AndroidXmlCodeStyleSettings settings) {
      return isModified(mySettingsProvider.getSettings(settings));
    }

    protected boolean isModified(T s) {
      if (s.WRAP_ATTRIBUTES != CodeStyleSettings.WrapStyle.getId((CodeStyleSettings.WrapStyle)myWrapAttributesCombo.getSelectedItem())) {
        return true;
      }
      if (s.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE != myInsertLineBreakBeforeFirstAttributeCheckBox.isSelected()) {
        return true;
      }
      if (s.INSERT_LINE_BREAK_BEFORE_NAMESPACE_DECLARATION != myInsertLineBreakBeforeNamespaceDeclarationCheckBox.isSelected()) {
        return true;
      }
      if (s.INSERT_LINE_BREAK_AFTER_LAST_ATTRIBUTE != myInsertLineBreakAfterLastAttributeCheckbox.isSelected()) {
        return true;
      }
      return false;
    }

    protected final void resetImpl(AndroidXmlCodeStyleSettings settings) {
      resetImpl(mySettingsProvider.getSettings(settings));
    }

    protected void resetImpl(T s) {
      myWrapAttributesCombo.setSelectedItem(CodeStyleSettings.WrapStyle.forWrapping(s.WRAP_ATTRIBUTES));
      myInsertLineBreakBeforeFirstAttributeCheckBox.setSelected(s.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE);
      myInsertLineBreakBeforeNamespaceDeclarationCheckBox.setSelected(s.INSERT_LINE_BREAK_BEFORE_NAMESPACE_DECLARATION);
      myInsertLineBreakAfterLastAttributeCheckbox.setSelected(s.INSERT_LINE_BREAK_AFTER_LAST_ATTRIBUTE);
      UIUtil.setEnabled(myInsertLineBreakBeforeNamespaceDeclarationCheckBox, s.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE, true);
    }

    @NotNull
    public String getTitle() {
      return myTitle;
    }

    private void setupUI() {
      myPanel = new JPanel();
      myPanel.setLayout(new GridLayoutManager(6, 3, new Insets(0, 0, 0, 0), -1, -1));
      final Spacer spacer1 = new Spacer();
      myPanel.add(spacer1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      myAdditionalOptionsPanel = new JPanel();
      myAdditionalOptionsPanel.setLayout(new BorderLayout(0, 0));
      myPanel.add(myAdditionalOptionsPanel, new GridConstraints(4, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                null, null, null, 0, false));
      final JBLabel jBLabel1 = new JBLabel();
      loadLabelText(jBLabel1, getMessageFromBundle("messages/ApplicationBundle", "label.wrap.attributes"));
      myPanel.add(jBLabel1,
                  new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myWrapAttributesCombo = new JComboBox();
      myPanel.add(myWrapAttributesCombo, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                             GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                             null, null, 0, false));
      myInsertLineBreakBeforeFirstAttributeCheckBox = new JBCheckBox();
      myInsertLineBreakBeforeFirstAttributeCheckBox.setText("Insert line break before first attribute");
      myPanel.add(myInsertLineBreakBeforeFirstAttributeCheckBox,
                  new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final Spacer spacer2 = new Spacer();
      myPanel.add(spacer2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
      myInsertLineBreakAfterLastAttributeCheckbox = new JBCheckBox();
      myInsertLineBreakAfterLastAttributeCheckbox.setText("Insert line break after last attribute");
      myPanel.add(myInsertLineBreakAfterLastAttributeCheckbox,
                  new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myInsertLineBreakBeforeNamespaceDeclarationCheckBox = new JBCheckBox();
      myInsertLineBreakBeforeNamespaceDeclarationCheckBox.setText("Include namespace declarations");
      myPanel.add(myInsertLineBreakBeforeNamespaceDeclarationCheckBox,
                  new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
      jBLabel1.setLabelFor(myWrapAttributesCombo);
    }

    private static Method cachedGetBundleMethod = null;

    private String getMessageFromBundle(String path, String key) {
      ResourceBundle bundle;
      try {
        Class<?> thisClass = this.getClass();
        if (cachedGetBundleMethod == null) {
          Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
          cachedGetBundleMethod = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
        }
        bundle = (ResourceBundle)cachedGetBundleMethod.invoke(null, path, thisClass);
      }
      catch (Exception e) {
        bundle = ResourceBundle.getBundle(path);
      }
      return bundle.getString(key);
    }

    /**
     * @noinspection ALL
     */
    private void loadLabelText(JLabel component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setDisplayedMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }
}

  private static class LayoutCodeStylePanel extends MyFileSpecificPanel<AndroidXmlCodeStyleSettings.LayoutSettings> {
    private JPanel myPanel;
    private JBCheckBox myInsertNewLineBeforeTagCheckBox;

    public LayoutCodeStylePanel() {
      super("Layout Files", ContextSpecificSettingsProviders.LAYOUT);
      setupUI();
      init();
    }

    @Nullable
    @Override
    public JPanel getAdditionalOptionsPanel() {
      return myPanel;
    }

    @Override
    protected boolean isModified(AndroidXmlCodeStyleSettings.LayoutSettings s) {
      if (super.isModified(s)) {
        return true;
      }
      return myInsertNewLineBeforeTagCheckBox.isSelected() != s.INSERT_BLANK_LINE_BEFORE_TAG;
    }

    @Override
    protected void resetImpl(AndroidXmlCodeStyleSettings.LayoutSettings s) {
      super.resetImpl(s);
      myInsertNewLineBeforeTagCheckBox.setSelected(s.INSERT_BLANK_LINE_BEFORE_TAG);
    }

    @Override
    protected void apply(AndroidXmlCodeStyleSettings.LayoutSettings s) {
      super.apply(s);
      s.INSERT_BLANK_LINE_BEFORE_TAG = myInsertNewLineBeforeTagCheckBox.isSelected();
    }

    private void setupUI() {
      myPanel = new JPanel();
      myPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
      myInsertNewLineBeforeTagCheckBox = new JBCheckBox();
      myInsertNewLineBeforeTagCheckBox.setText("Insert blank line before tag");
      myPanel.add(myInsertNewLineBeforeTagCheckBox,
                  new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final Spacer spacer1 = new Spacer();
      myPanel.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    }
  }

  public static class ManifestCodeStylePanel extends MyFileSpecificPanel<AndroidXmlCodeStyleSettings.ManifestSettings> {
    private final JBCheckBox myGroupTagsCheckBox;
    private JPanel myPanel;

    public ManifestCodeStylePanel() {
      super("AndroidManifest.xml", ContextSpecificSettingsProviders.MANIFEST);

      myPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
      myGroupTagsCheckBox = new JBCheckBox("Group tags with the same name");
      myPanel.add(myGroupTagsCheckBox);

      init();
    }

    @Nullable
    @Override
    public JPanel getAdditionalOptionsPanel() {
      return myPanel;
    }

    @Override
    protected void apply(AndroidXmlCodeStyleSettings.ManifestSettings s) {
      super.apply(s);
      s.GROUP_TAGS_WITH_SAME_NAME = myGroupTagsCheckBox.isSelected();
    }

    @Override
    protected boolean isModified(AndroidXmlCodeStyleSettings.ManifestSettings s) {
      if (super.isModified(s)) {
        return true;
      }
      return s.GROUP_TAGS_WITH_SAME_NAME != myGroupTagsCheckBox.isSelected();
    }

    @Override
    protected void resetImpl(AndroidXmlCodeStyleSettings.ManifestSettings s) {
      super.resetImpl(s);
      myGroupTagsCheckBox.setSelected(s.GROUP_TAGS_WITH_SAME_NAME);
    }
  }

  public static class ValueResourcesCodeStylePanel extends MyFileSpecificPanel<AndroidXmlCodeStyleSettings.ValueResourceFileSettings> {
    private final JBCheckBox myInsertLineBreaksAroundStyleCheckBox;
    private JPanel myPanel;

    public ValueResourcesCodeStylePanel() {
      super("Value Resource Files and Selectors", ContextSpecificSettingsProviders.VALUE_RESOURCE_FILE);
      myPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
      myInsertLineBreaksAroundStyleCheckBox = new JBCheckBox("Insert line breaks around style declaration");
      myPanel.add(myInsertLineBreaksAroundStyleCheckBox);

      init();
      myInsertLineBreakBeforeFirstAttributeCheckBox.setVisible(false);
      myInsertLineBreakBeforeNamespaceDeclarationCheckBox.setVisible(false);
      myInsertLineBreakAfterLastAttributeCheckbox.setVisible(false);
    }

    @Nullable
    @Override
    public JPanel getAdditionalOptionsPanel() {
      return myPanel;
    }

    @Override
    protected void apply(AndroidXmlCodeStyleSettings.ValueResourceFileSettings s) {
      super.apply(s);
      s.INSERT_LINE_BREAKS_AROUND_STYLE = myInsertLineBreaksAroundStyleCheckBox.isSelected();
    }

    @Override
    protected boolean isModified(AndroidXmlCodeStyleSettings.ValueResourceFileSettings s) {
      if (super.isModified(s)) {
        return true;
      }
      return s.INSERT_LINE_BREAKS_AROUND_STYLE != myInsertLineBreaksAroundStyleCheckBox.isSelected();
    }

    @Override
    protected void resetImpl(AndroidXmlCodeStyleSettings.ValueResourceFileSettings s) {
      super.resetImpl(s);
      myInsertLineBreaksAroundStyleCheckBox.setSelected(s.INSERT_LINE_BREAKS_AROUND_STYLE);
    }
  }

  public static class OtherCodeStylePanel extends MyFileSpecificPanel<AndroidXmlCodeStyleSettings.OtherSettings> {
    public OtherCodeStylePanel() {
      super("Other XML resource files", ContextSpecificSettingsProviders.OTHER);
      init();
    }
  }
}
