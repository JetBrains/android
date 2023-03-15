/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.annotations.concurrency.GuardedBy;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.progress.StudioProgressRunner;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EventObject;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A table model for a {@link SystemImageList}
 */
public class SystemImageListModel extends ListTableModel<SystemImageDescription> {
  private final Project myProject;
  private final StatusIndicator myIndicator;
  private final AndroidSdkHandler mySdkHandler;
  private boolean myUpdating;
  @GuardedBy("myLock")
  private int myCompletedCalls;
  private final Object myLock = new Object();

  private static final ProgressIndicator LOGGER = new StudioLoggerProgressIndicator(SystemImageListModel.class);

  public SystemImageListModel(@Nullable Project project, @NotNull StatusIndicator indicator) {
    myProject = project;
    myIndicator = indicator;
    mySdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    setColumnInfos(ourColumnInfos);
    setSortable(true);
  }

  public void refreshLocalImagesSynchronously() {
    try {
      myIndicator.onRefreshStart("Get local images...");
      setItems(getLocalImages());
    }
    finally {
      myIndicator.onRefreshDone("", true);
    }
  }

  @Override
  public void setItems(@NotNull List<SystemImageDescription> items) {
    myUpdating = true;
    super.setItems(items);
    myUpdating = false;
  }

  public boolean isUpdating() {
    return myUpdating;
  }

  public void refreshImages(final boolean forceRefresh) {
    synchronized (myLock) {
      myCompletedCalls = 0;
    }
    myIndicator.onRefreshStart("Refreshing...");
    final List<SystemImageDescription> items = new ArrayList<>();
    RepoManager.RepoLoadedListener localComplete = packages ->
      ApplicationManager.getApplication().invokeLater(() -> {
        // getLocalImages() doesn't use SdkPackages, so it's ok that we're not using what's passed in.
        items.addAll(getLocalImages());
        // Update list in the UI immediately with the locally available system images
        setItems(items);
        // Assume the remote has not completed yet
        completedDownload("");
      }, ModalityState.any());
    RepoManager.RepoLoadedListener remoteComplete = packages ->
      ApplicationManager.getApplication().invokeLater(() -> {
        List<SystemImageDescription> remotes = getRemoteImages(packages);
        if (remotes != null) {
          items.addAll(remotes);
          setItems(items);
        }
        completedDownload("");
      }, ModalityState.any());
    Runnable error = () -> ApplicationManager.getApplication().invokeLater(
      () -> completedDownload("Error loading remote images"),
      ModalityState.any());

    StudioProgressRunner runner = new StudioProgressRunner(false, false, "Loading Images", myProject);
    mySdkHandler.getSdkManager(LOGGER)
      .load(forceRefresh ? 0 : RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, ImmutableList.of(localComplete), ImmutableList.of(remoteComplete),
            ImmutableList.of(error), runner, new StudioDownloader(), StudioSettingsController.getInstance());
  }

  // Report that one of the downloads were done.
  // If this is the first completed message continue to report "Refreshing..."
  private void completedDownload(@NotNull String message) {
    synchronized (myLock) {
      myCompletedCalls++;
      myIndicator.onRefreshDone(message, myCompletedCalls < 2);
      if (myCompletedCalls < 2) {
        myIndicator.onRefreshStart("Refreshing...");
      }
    }
  }

  private List<SystemImageDescription> getLocalImages() {
    SystemImageManager systemImageManager = mySdkHandler.getSystemImageManager(LOGGER);
    List<SystemImageDescription> items = new ArrayList<>();

    for (ISystemImage image : systemImageManager.getImages()) {
      SystemImageDescription desc = new SystemImageDescription(image);
      items.add(desc);
    }
    return items;
  }

  @Nullable
  private static List<SystemImageDescription> getRemoteImages(@NotNull RepositoryPackages packages) {
    List<SystemImageDescription> items = new ArrayList<>();
    Set<RemotePackage> infos = packages.getNewPkgs();

    if (infos.isEmpty()) {
      return null;
    }
    else {
      for (RemotePackage info : infos) {
        if (SystemImageDescription.hasSystemImage(info)) {
          SystemImageDescription image = new SystemImageDescription(info);
          items.add(image);
        }
      }
    }
    return items;
  }

  @VisibleForTesting
  @NotNull
  static String releaseDisplayName(@NotNull SystemImageDescription systemImage) {
    AndroidVersion version = systemImage.getVersion();
    String codeName = version.isPreview() ? version.getCodename()
                                          : SdkVersionInfo.getCodeName(version.getApiLevel());
    if (codeName == null) {
      codeName = "API " + version.getApiLevel();
    }
    String maybeDeprecated = systemImage.obsolete() || version.getApiLevel() < SdkVersionInfo.LOWEST_ACTIVE_API ? " (Deprecated)" : "";
    String extensionDetails =
      !version.isBaseExtension() && version.getExtensionLevel() != null ? " (Extension Level " + version.getExtensionLevel() + ")" : "";
    return codeName + extensionDetails + maybeDeprecated;
  }

  /**
   * List of columns present in our table. Each column is represented by a ColumnInfo which tells the table how to get
   * the cell value in that column for a given row item.
   */
  private final ColumnInfo[] ourColumnInfos = new ColumnInfo[] {
    new SystemImageColumnInfo("Release Name") {
      @NotNull
      @Override
      public String valueOf(SystemImageDescription systemImage) {
        return releaseDisplayName(systemImage);
      }

      @NotNull
      @Override
      public Comparator<SystemImageDescription> getComparator() {
        return Comparator.comparing(SystemImageDescription::getVersion);
      }
    },
    new SystemImageColumnInfo("API Level", JBUIScale.scale(100)) {
      @NotNull
      @Override
      public String valueOf(SystemImageDescription systemImage) {
        return systemImage.getVersion().getApiString();
      }
    },
    new SystemImageColumnInfo("ABI", JBUIScale.scale(100)) {
      @NotNull
      @Override
      public String valueOf(SystemImageDescription systemImage) {
        return systemImage.getAbiType();
      }
    },
    new SystemImageColumnInfo("Target") {
      @NotNull
      @Override
      public String valueOf(SystemImageDescription systemImage) {
        IdDisplay tag = systemImage.getTag();
        String name = systemImage.getName();
        return String.format("%1$s%2$s", name, tag.equals(SystemImage.DEFAULT_TAG) ? "" :
                                               String.format(" (%s)", tag.getDisplay()));
      }
    },
  };

  /**
   * This class extends {@link ColumnInfo} in order to pull a string value from a given
   * {@link SystemImageDescription}.
   * This is the column info used for most of our table, including the Name, Resolution, and API level columns.
   * It uses the text field renderer ({@link #getRenderer}) and allows for sorting by the lexicographical value
   * of the string displayed by the {@link JBLabel} rendered as the cell component. An explicit width may be used
   * by calling the overloaded constructor, otherwise the column will auto-scale to fill available space.
   */
  public abstract class SystemImageColumnInfo extends ColumnInfo<SystemImageDescription, String> {
    private final int myWidth;

    public SystemImageColumnInfo(@NotNull String name, int width) {
      super(name);
      myWidth = width;
    }

    public SystemImageColumnInfo(@NotNull String name) {
      this(name, -1);
    }

    @Override
    public boolean isCellEditable(SystemImageDescription systemImageDescription) {
      return systemImageDescription.isRemote();
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(SystemImageDescription o) {
      return new SystemImageDescriptionRenderer(o);
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(final SystemImageDescription o) {
      return new SystemImageDescriptionRenderer(o);
    }

    private class SystemImageDescriptionRenderer extends AbstractTableCellEditor implements TableCellRenderer {
      private final SystemImageDescription image;

      SystemImageDescriptionRenderer(SystemImageDescription o) {
        image = o;
      }

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
        JPanel panel = new JPanel(flowLayout);
        JBLabel label = new JBLabel((String)value);
        if (isSelected) {
          if (image.isRemote()) {
            panel.setBackground(UIUtil.getListSelectionBackground(false));
          } else {
            panel.setBackground(table.getSelectionBackground());
            label.setBackground(table.getSelectionBackground());
            label.setForeground(table.getSelectionForeground());
          }
          panel.setForeground(table.getSelectionForeground());
        }
        else {
          panel.setBackground(table.getBackground());
          panel.setForeground(table.getForeground());
        }
        panel.setOpaque(true);
        Font labelFont = StartupUiUtil.getLabelFont();
        if (column == 0) {
          label.setFont(labelFont.deriveFont(Font.BOLD));
        }
        if (image.isRemote()) {
          Font font = labelFont.deriveFont(label.getFont().getStyle() | Font.ITALIC);
          label.setFont(font);
          label.setForeground(UIUtil.getLabelDisabledForeground());
          // on OS X the actual text width isn't computed correctly. Compensating for that..
          if (!label.getText().isEmpty()) {
            int fontMetricsWidth = label.getFontMetrics(label.getFont()).stringWidth(label.getText());
            TextLayout l = new TextLayout(label.getText(), label.getFont(), label.getFontMetrics(label.getFont()).getFontRenderContext());
            int offset = (int)Math.ceil(l.getBounds().getWidth()) - fontMetricsWidth;
            if (offset > 0) {
              label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, offset));
            }
          }
          panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
              if (e.getKeyChar() == KeyEvent.VK_ENTER || e.getKeyChar() == KeyEvent.VK_SPACE) {
                downloadImage(image);
              }
            }
          });
        }
        panel.add(label);

        // Add a download button if applicable
        if (image.isRemote() && column == 0) {
          final JBLabel link = new JBLabel(AllIcons.Actions.Download);
          link.setBackground(table.getBackground());
          link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              downloadImage(image);
              // Clicking the download link should also select the table
              // row that the download link is located in.
              table.changeSelection(row, column, false, false);
            }
          });

          // We want the download button to always show and not be cut off
          int columnWidth = table.getColumnModel().getColumn(0).getWidth();
          double extraWidth = link.getPreferredSize().getWidth() + flowLayout.getHgap() * 3;
          if (label.getPreferredSize().getWidth() + extraWidth > columnWidth) {
            Dimension labelSize = new Dimension((int)(columnWidth - extraWidth), (int)label.getPreferredSize().getHeight());
            label.setMinimumSize(labelSize);
            label.setMaximumSize(labelSize);
            label.setPreferredSize(labelSize);
            label.setSize(labelSize);
          }

          panel.add(link);
        }
        return panel;
      }

      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        return getTableCellRendererComponent(table, value, isSelected, false, row, column);
      }

      @Override
      public Object getCellEditorValue() {
        return null;
      }

      @Override
      public boolean isCellEditable(EventObject e) {
        return true;
      }

    }

    private void downloadImage(SystemImageDescription image) {
      java.util.List<String> requestedPackages = Lists.newArrayList(image.getRemotePackage().getPath());
      ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(myProject, requestedPackages);
      if (dialog != null) {
        dialog.show();
        refreshImages(true);
      }
    }

    @Nullable
    @Override
    public Comparator<SystemImageDescription> getComparator() {
      return new Comparator<SystemImageDescription>() {
        ApiLevelComparator myComparator = new ApiLevelComparator();
        @Override
        public int compare(SystemImageDescription o1, SystemImageDescription o2) {
          int res = myComparator.compare(valueOf(o1), valueOf(o2));
          if (res == 0) {
            return o1.getTag().compareTo(o2.getTag());
          }
          return res;

        }
      };
    }

    @Override
    public int getWidth(JTable table) {
      return myWidth;
    }
  }

  public interface StatusIndicator {
    void onRefreshStart(@NotNull String message);
    void onRefreshDone(@NotNull String message, boolean localOnly);
  }
}
