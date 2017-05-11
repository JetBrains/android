package com.android.tools.idea.uibuilder.options;

import com.android.tools.idea.IdeInfo;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class NlOptionsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private JBCheckBox myPreferXml;
  private JBCheckBox myShowLint;
  private JPanel myContentPanel;
  private JBCheckBox myHide;

  private AndroidEditorSettings.GlobalState myState = AndroidEditorSettings.getInstance().getGlobalState();

  @NotNull
  @Override
  public String getId() {
    return "nele.options";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myContentPanel;
  }

  @Override
  public boolean isModified() {
    return isPreferXml() != myState.isPreferXmlEditor() ||
           isShowLint() != myState.isShowLint() ||
           isHideNonLayout() != myState.isHideForNonLayoutFiles();
  }

  @Override
  public void apply() throws ConfigurationException {
    myState.setPreferXmlEditor(isPreferXml());
    myState.setShowLint(isShowLint());
    myState.setHideForNonLayoutFiles(isHideNonLayout());
  }

  @Override
  public void reset() {
    myPreferXml.setSelected(myState.isPreferXmlEditor());
    myShowLint.setSelected(myState.isShowLint());
    myHide.setSelected(myState.isHideForNonLayoutFiles());
  }

  @Override
  public void disposeUIResources() {
  }

  @Nls
  @Override
  public String getDisplayName() {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      return "Layout Editor";
    } else {
      return "Android Layout Editor";
    }
  }

  private boolean isPreferXml() {
    return myPreferXml.isSelected();
  }

  private boolean isShowLint() {
    return myShowLint.isSelected();
  }

  private boolean isHideNonLayout() {
    return myHide.isSelected();
  }
}
