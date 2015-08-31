package com.android.tools.idea.gradle.facet;

import com.android.tools.idea.gradle.structure.editors.AndroidModuleEditor;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 * for IntelliJ only, not used in Android Studio
 */
public class AndroidGradleFacetEditorTab extends FacetEditorTab {
  private final AndroidModuleEditor myModuleEditor;

  public AndroidGradleFacetEditorTab(@NotNull Project project, @NotNull String gradleProjectPath) {
    myModuleEditor = new AndroidModuleEditor(project, gradleProjectPath);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Android Gradle Module Settings";
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    return myModuleEditor.getPanel();
  }

  @Override
  public void apply() throws ConfigurationException {
    myModuleEditor.apply();
  }

  @Override
  public boolean isModified() {
    return myModuleEditor.isModified();
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myModuleEditor);
  }
}
