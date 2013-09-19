package org.jetbrains.android.compiler.artifact;

import com.android.annotations.NonNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidArtifactPropertiesEditor extends ArtifactPropertiesEditor implements ApkSigningSettingsForm {
  private final AndroidApplicationArtifactProperties myProperties;

  private JPanel myPanel;
  private JRadioButton myDebugRadio;
  private JRadioButton myReleaseSignedRadio;
  private JRadioButton myReleaseUnsignedRadio;
  private JPanel myReleaseKeyPanel;
  private JPasswordField myKeyStorePasswordField;
  private JTextField myKeyStorePathField;
  private JPasswordField myKeyPasswordField;
  private TextFieldWithBrowseButton.NoPathCompletion myKeyAliasField;
  private JButton myLoadKeyStoreButton;
  private JButton myCreateKeyStoreButton;
  private JCheckBox myProGuardCheckBox;
  private JPanel myProGuardConfigPanel;
  private JPanel myKeyStoreButtonsPanel;
  private JPanel myProGuardPanel;
  private ProGuardConfigFilesPanel myProGuardConfigFilesPanel;

  private final Artifact myArtifact;
  private final Project myProject;

  public AndroidArtifactPropertiesEditor(@Nullable final Artifact artifact,
                                         @NonNull AndroidApplicationArtifactProperties properties,
                                         @NotNull final Project project) {
    myProperties = properties;
    myArtifact = artifact;
    myProject = project;

    myKeyStoreButtonsPanel.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 5, 0));
    myProGuardPanel.setBorder(IdeBorderFactory.createEmptyBorder(10, 0, 0, 0));

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myReleaseKeyPanel, myReleaseSignedRadio.isSelected(), true);
      }
    };
    myDebugRadio.addActionListener(listener);
    myReleaseUnsignedRadio.addActionListener(listener);
    myReleaseSignedRadio.addActionListener(listener);

    AndroidUiUtil.initSigningSettingsForm(project, this);

    myProGuardCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myProGuardConfigPanel, myProGuardCheckBox.isSelected(), true);
      }
    });
  }

  private String getKeyStorePath() {
    return myKeyStorePathField.getText().trim();
  }

  @Override
  public String getTabName() {
    return "Android";
  }

  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return getSigningMode() != myProperties.getSigningMode() ||
           !getKeyStoreFileUrl().equals(myProperties.getKeyStoreUrl()) ||
           !getKeyStorePassword().equals(myProperties.getPlainKeystorePassword()) ||
           !getKeyAlias().equals(myProperties.getKeyAlias()) ||
           !getKeyPassword().equals(myProperties.getPlainKeyPassword()) ||
           myProGuardCheckBox.isSelected() != myProperties.isRunProGuard() ||
           !myProGuardConfigFilesPanel.getUrls().equals(myProperties.getProGuardCfgFiles());
  }

  @Override
  public void apply() {
    myProperties.setSigningMode(getSigningMode());
    myProperties.setKeyStoreUrl(getKeyStoreFileUrl());
    myProperties.setPlainKeystorePassword(getKeyStorePassword());
    myProperties.setKeyAlias(getKeyAlias());
    myProperties.setPlainKeyPassword(getKeyPassword());
    myProperties.setRunProGuard(myProGuardCheckBox.isSelected());
    myProperties.setProGuardCfgFiles(myProGuardConfigFilesPanel.getUrls());
  }

  @Override
  public void reset() {
    switch (myProperties.getSigningMode()) {
      case RELEASE_UNSIGNED:
        myReleaseUnsignedRadio.setSelected(true);
        myDebugRadio.setSelected(false);
        myReleaseSignedRadio.setSelected(false);
        break;
      case DEBUG:
        myReleaseUnsignedRadio.setSelected(false);
        myDebugRadio.setSelected(true);
        myReleaseSignedRadio.setSelected(false);
        break;
      case RELEASE_SIGNED:
        myReleaseUnsignedRadio.setSelected(false);
        myDebugRadio.setSelected(false);
        myReleaseSignedRadio.setSelected(true);
        break;
    }
    final String keyStoreUrl = myProperties.getKeyStoreUrl();
    myKeyStorePathField.setText(keyStoreUrl != null ? FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(keyStoreUrl)) : "");
    myKeyStorePasswordField.setText(myProperties.getPlainKeystorePassword());

    final String keyAlias = myProperties.getKeyAlias();
    myKeyAliasField.setText(keyAlias != null ? keyAlias : "");
    myKeyPasswordField.setText(myProperties.getPlainKeyPassword());

    myProGuardCheckBox.setSelected(myProperties.isRunProGuard());
    myProGuardConfigFilesPanel.setUrls(myProperties.getProGuardCfgFiles());

    UIUtil.setEnabled(myReleaseKeyPanel, myProperties.getSigningMode() == AndroidArtifactSigningMode.RELEASE_SIGNED, true);
    UIUtil.setEnabled(myProGuardConfigPanel, myProperties.isRunProGuard(), true);
  }

  @Override
  public void disposeUIResources() {
  }

  @NotNull
  private AndroidArtifactSigningMode getSigningMode() {
    if (myDebugRadio.isSelected()) {
      return AndroidArtifactSigningMode.DEBUG;
    }
    else if (myReleaseSignedRadio.isSelected()) {
      return AndroidArtifactSigningMode.RELEASE_SIGNED;
    }
    return AndroidArtifactSigningMode.RELEASE_UNSIGNED;
  }

  @NotNull
  private String getKeyStoreFileUrl() {
    final String path = getKeyStorePath();
    return VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(path));
  }

  @NotNull
  private String getKeyStorePassword() {
    return String.valueOf(myKeyStorePasswordField.getPassword());
  }

  @NotNull
  private String getKeyAlias() {
    return myKeyAliasField.getText().trim();
  }

  @NotNull
  private String getKeyPassword() {
    return String.valueOf(myKeyPasswordField.getPassword());
  }

  @Override
  public JButton getLoadKeyStoreButton() {
    return myLoadKeyStoreButton;
  }

  @Override
  public JTextField getKeyStorePathField() {
    return myKeyStorePathField;
  }

  @Override
  public JPanel getPanel() {
    return myPanel;
  }

  @Override
  public JButton getCreateKeyStoreButton() {
    return myCreateKeyStoreButton;
  }

  @Override
  public JPasswordField getKeyStorePasswordField() {
    return myKeyStorePasswordField;
  }

  @Override
  public TextFieldWithBrowseButton getKeyAliasField() {
    return myKeyAliasField;
  }

  @Override
  public JPasswordField getKeyPasswordField() {
    return myKeyPasswordField;
  }

  private void createUIComponents() {
    myProGuardConfigFilesPanel = new ProGuardConfigFilesPanel() {
      @Nullable
      @Override
      protected AndroidFacet getFacet() {
        return AndroidArtifactUtil.getPackagedFacet(myProject, myArtifact);
      }
    };
  }
}
