package org.jetbrains.android.compiler.artifact;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacetProperties;

public abstract class ProGuardConfigFilesPanel extends JPanel {

  private final JBList myList;
  private CollectionListModel<String> myModel;

  public ProGuardConfigFilesPanel() {
    super(new BorderLayout());
    myModel = new CollectionListModel<String>();
    myList = new JBList(myModel);

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList).
      setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final String path = chooseFile();

        if (path != null) {
          myModel.add(path);
        }
      }
    });
    JPanel tablePanel = decorator.setPreferredSize(new Dimension(-1, JBUI.scale(120))).createPanel();
    tablePanel.setMinimumSize(new Dimension(-1, JBUI.scale(120)));
    add(tablePanel, BorderLayout.CENTER);
    final JBLabel label = new JBLabel("Config file paths:");
    label.setBorder(JBUI.Borders.empty(0, 0, 5, 0));
    add(label, BorderLayout.NORTH);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);

    for (Component component : getComponents()) {
      UIUtil.setEnabled(component, enabled, true);
    }
  }

  private String chooseFile() {
    final AndroidFacet facet = getFacet();

    if (facet == null) {
      return null;
    }
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    final VirtualFile contentRoot = AndroidRootUtil.getMainContentRoot(facet);
    final VirtualFile file = FileChooser.chooseFile(descriptor, this, facet.getModule().getProject(), contentRoot);
    return file != null ? FileUtil.toSystemDependentName(file.getPath()) : null;
  }

  @NotNull
  public List<String> getUrls() {
    final List<String> paths = getOsPaths();

    if (paths.isEmpty()) {
      return Collections.emptyList();
    }
    final List<String> result = new ArrayList<String>(paths.size());

    for (String path : paths) {
      String url = VfsUtilCore.pathToUrl(path);
      final String sdkHome = getCanonicalSdkHome();

      if (sdkHome != null) {
        url = StringUtil.replace(url, sdkHome, AndroidFacetProperties.SDK_HOME_MACRO);
      }
      result.add(url);
    }
    return result;
  }

  @NotNull
  public List<String> getOsPaths() {
    return myModel.getItems();
  }

  public void setUrls(@NotNull List<String> urls) {
    setOsPaths(AndroidUtils.urlsToOsPaths(urls, getCanonicalSdkHome()));
  }

  public void setOsPaths(@NotNull List<String> paths) {
    myModel = new CollectionListModel<String>(paths);
    myList.setModel(myModel);
  }

  @Nullable
  private String getCanonicalSdkHome() {
    final AndroidFacet facet = getFacet();

    if (facet == null) {
      return null;
    }
    final Sdk sdk = ModuleRootManager.getInstance(facet.getModule()).getSdk();

    if (sdk == null) {
      return null;
    }
    final String homePath = sdk.getHomePath();
    return homePath != null ? FileUtil.toCanonicalPath(homePath) : null;
  }

  @Nullable
  protected abstract AndroidFacet getFacet();
}
