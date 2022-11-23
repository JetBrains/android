/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.updater.configure;

import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.api.Channel;
import com.android.repository.api.DelegatingProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileUtilKt;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.utils.HtmlBuilder;
import com.android.utils.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.JBColor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.event.AncestorEvent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configurable for the Android SDK Manager.
 * TODO(jbakermalone): implement the searchable interface more completely.
 */
public class SdkUpdaterConfigurable implements SearchableConfigurable {
  /**
   * Very rough zip decompression estimate for informational/UI purposes only. Better for it to be a bit higher than average,
   * but not too much. Most of the SDK component files are binary, which should yield 2x-3x compression rate
   * on average - at least this is the assumption we are making here.
   * <p>
   * TODO: The need for this will disappear should we revise the packages XML schema and add installation size for a given
   * platform there.
   */
  private static final int ESTIMATED_ZIP_DECOMPRESSION_RATE = 4;

  private SdkUpdaterConfigPanel myPanel;
  private Channel myCurrentChannel;
  private Runnable myChannelChangedCallback;

  @NotNull
  @Override
  public String getId() {
    return "AndroidSdkUpdater";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Android SDK Updater";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return AndroidWebHelpProvider.HELP_PREFIX + "r/studio-ui/sdk-manager.html";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myChannelChangedCallback = () -> {
      Channel channel = StudioSettingsController.getInstance().getChannel();
      if (myCurrentChannel == null) {
        myCurrentChannel = channel;
      }
      if (!Objects.equal(channel, myCurrentChannel)) {
        myCurrentChannel = channel;
        myPanel.refresh(true);
      }
    };
    myPanel =
      new SdkUpdaterConfigPanel(myChannelChangedCallback, new StudioDownloader(), StudioSettingsController.getInstance(), this);
    JComponent component = myPanel.getComponent();
    component.addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        myChannelChangedCallback.run();
      }
    });

    return myPanel.getComponent();
  }

  /**
   * Gets the {@link AndroidSdkHandler} to use. Note that the instance can change if the local sdk path is edited, and so should not be
   * cached.
   */
  AndroidSdkHandler getSdkHandler() {
    File location = myPanel.getSelectedSdkLocation();
    return AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, location == null ? null : location.toPath());
  }

  RepoManager getRepoManager() {
    return getSdkHandler().getSdkManager(new StudioLoggerProgressIndicator(getClass()));
  }

  @Override
  public boolean isModified() {
    if (myPanel.isModified()) {
      return true;
    }

    // If the user modifies the channel, comes back here, and then applies the change, we want to be able to update
    // right away. Thus we mark ourselves as modified if UpdateSettingsConfigurable is modified, and then reload in
    // apply().
    DataContext dataContext = DataManager.getInstance().getDataContext(myPanel.getComponent());
    Settings data = Settings.KEY.getData(dataContext);
    if (data != null) {
      Configurable updatesConfigurable = data.find("preferences.updates");
      if (updatesConfigurable != null) {
        return updatesConfigurable.isModified();
      }
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    boolean sourcesModified = myPanel.areSourcesModified();
    myPanel.saveSources();

    final List<LocalPackage> toDelete = new ArrayList<>();
    final Map<RemotePackage, UpdatablePackage> requestedPackages = Maps.newHashMap();
    for (PackageNodeModel model : myPanel.getStates()) {
      if (model.getState() == PackageNodeModel.SelectedState.NOT_INSTALLED) {
        if (model.getPkg().hasLocal()) {
          toDelete.add(model.getPkg().getLocal());
        }
      }
      else if (model.getState() == PackageNodeModel.SelectedState.INSTALLED &&
               (model.getPkg().isUpdate() || !model.getPkg().hasLocal())) {
        UpdatablePackage pkg = model.getPkg();
        requestedPackages.put(pkg.getRemote(), pkg);
      }
    }
    boolean found = false;
    long spaceToBeFreedUp = 0;
    long patchesDownloadSize = 0, fullInstallationsDownloadSize = 0;
    HtmlBuilder messageToDelete = new HtmlBuilder();
    if (!toDelete.isEmpty()) {
      found = true;
      messageToDelete.add("The following components will be deleted: \n");
      messageToDelete.beginList();

      try {
        spaceToBeFreedUp = ProgressManager.getInstance().runProcessWithProgressSynchronously(
          () -> getLocalInstallationSize(toDelete),
          "Gathering Package Information", true, null);
      }
      catch (ProcessCanceledException e) {
        throw new ConfigurationException("Installation was canceled.");
      }
      for (LocalPackage item : toDelete) {
        messageToDelete.listItem()
                       .add(getItemMessage(item));
      }
      messageToDelete.endList();
    }
    HtmlBuilder messageToInstall = new HtmlBuilder();
    if (!requestedPackages.isEmpty()) {
      found = true;
      messageToInstall.add("The following components will be installed: \n");
      messageToInstall.beginList();
      Multimap<RemotePackage, RemotePackage> dependencies = HashMultimap.create();
      ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
      final Queue<String> dependencyIssues = new ConcurrentLinkedQueue<>();
      ProgressIndicator dependencyIssueReporter = new DelegatingProgressIndicator(progress) {
        @Override
        public void logWarning(@NotNull String s) {
          dependencyIssues.add(s);
          super.logWarning(s);
        }

        @Override
        public void logError(@NotNull String s) {
          dependencyIssues.add(s);
          super.logError(s);
        }
      };
      RepositoryPackages packages = getRepoManager().getPackages();
      for (RemotePackage item : requestedPackages.keySet()) {
        List<RemotePackage> packageDependencies = InstallerUtil.computeRequiredPackages(ImmutableList.of(item), packages,
                                                                                        dependencyIssueReporter);
        if (packageDependencies == null) {
          String message;
          if (!dependencyIssues.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String issue : dependencyIssues) {
              sb.append(issue);
              sb.append(", ");
            }
            message = "Unable to resolve dependencies for " + item.getDisplayName() + ": " + sb;
          }
          else {
            message = "Unable to resolve dependencies for " + item.getDisplayName();
          }
          Messages.showErrorDialog((Project)null, message, "Dependency Error");
          throw new ConfigurationException(message);
        }
        for (RemotePackage dependency : packageDependencies) {
          dependencies.put(dependency, item);
        }

        messageToInstall.listItem().add(getItemMessage(item));

        Pair<Long, Boolean> itemDownloadSize = calculateDownloadSizeForPackage(item, packages);
        if (itemDownloadSize.getSecond()) {
          patchesDownloadSize += itemDownloadSize.getFirst();
        }
        else {
          fullInstallationsDownloadSize += itemDownloadSize.getFirst();
        }
      }
      for (RemotePackage dependency : dependencies.keySet()) {
        if (requestedPackages.containsKey(dependency)) {
          continue;
        }
        Set<RemotePackage> requests = Sets.newHashSet(dependencies.get(dependency));
        requests.remove(dependency);
        if (!requests.isEmpty()) {
          messageToInstall.listItem().add(dependency.getDisplayName())
            .add(" (Required by ");
          Iterator<RemotePackage> requestIterator = requests.iterator();
          messageToInstall.add(requestIterator.next().getDisplayName());
          while (requestIterator.hasNext()) {
            messageToInstall.add(", ").add(requestIterator.next().getDisplayName());
          }
          messageToInstall.add(")");
          Pair<Long, Boolean> itemDownloadSize = calculateDownloadSizeForPackage(dependency, packages);
          if (itemDownloadSize.getSecond()) {
            patchesDownloadSize += itemDownloadSize.getFirst();
          }
          else {
            fullInstallationsDownloadSize += itemDownloadSize.getFirst();
          }
        }
      }
      messageToInstall.endList();
    }

    if (found) {
      Path location = getSdkHandler().getLocation();
      Pair<HtmlBuilder, HtmlBuilder> diskUsageMessages = getDiskUsageMessages(
        location,
        fullInstallationsDownloadSize, patchesDownloadSize,
        spaceToBeFreedUp);
      // Now form the summary message ordering the constituents properly.
      HtmlBuilder message = new HtmlBuilder();
      message.openHtmlBody();
      if (diskUsageMessages.getSecond() != null) {
        message.addHtml(diskUsageMessages.getSecond().getHtml());
      }
      message.addHtml(messageToDelete.getHtml());
      message.addHtml(messageToInstall.getHtml());
      message.addHtml(diskUsageMessages.getFirst().getHtml());
      message.closeHtmlBody();
      if (confirmChange(message)) {
        if (!requestedPackages.isEmpty() || !toDelete.isEmpty()) {
          ModelWizardDialog dialog =
            SdkQuickfixUtils.createDialogForPackages(myPanel.getComponent(), requestedPackages.values(), toDelete, true);
          if (dialog != null) {
            dialog.show();
            for (RemotePackage remotePackage : requestedPackages.keySet()) {
              PackageOperation installer = getRepoManager().getInProgressInstallOperation(remotePackage);
              if (installer != null) {
                PackageOperation.StatusChangeListener listener = (installer1, progress) -> myPanel.getComponent().repaint();
                installer.registerStateChangeListener(listener);
              }
            }
          }
        }

        myPanel.refresh(sourcesModified);
      }
      else {
        throw new ConfigurationException("Installation was canceled.");
      }
    }
    else {
      // We didn't have any changes, so just reload (maybe the channel changed).
      myChannelChangedCallback.run();
    }
  }

  @VisibleForTesting
  static @NotNull String getItemMessage(@NotNull RepoPackage item) {
    String apiLevelString = item.getTypeDetails() instanceof DetailsTypes.SysImgDetailsType ?
                            " API level " + ((DetailsTypes.SysImgDetailsType)item.getTypeDetails()).getApiLevel() : "";
    String revisionOrVersion = String.format("%1$s %2$s",
                                             item.getTypeDetails() instanceof DetailsTypes.ApiDetailsType ? "revision" : "version",
                                             item.getVersion());
    return String.format("%1$s:%2$s %3$s", item.getDisplayName(),
                         apiLevelString,
                         revisionOrVersion);
  }

  private static long getLocalInstallationSize(@NotNull Collection<LocalPackage> localPackages) {
    long size = 0;
    for (LocalPackage item : localPackages) {
      if (item != null) {
        try {
          // TODO: Consider adding installation size to the package manifest.
          size += FileUtilKt.recursiveSize(item.getLocation());
        }
        catch (IOException e) {
          // skip
        }
      }
    }
    return size;
  }

  /**
   * Attempts to calculate the download size based on package's archive metadata.
   *
   * @param remotePackage the package to calculate the download size for.
   * @param packages      loaded repository packages obtained from the SDK handler.
   * @return A pair of long and boolean, where the first element denotes the calculated size,
   * and the second indicates whether it's a patch installation.
   */
  private static Pair<Long, Boolean> calculateDownloadSizeForPackage(@NotNull RemotePackage remotePackage,
                                                                     @NotNull RepositoryPackages packages) {
    LocalPackage localPackage = packages.getLocalPackages().get(remotePackage.getPath());
    Archive archive = remotePackage.getArchive();
    if (archive == null) {
      // There is not much we can do in this case, but it should "never be reached".
      return Pair.of(0L, false);
    }
    if (localPackage != null && !StudioSettingsController.getInstance().getDisableSdkPatches()) {
      Archive.PatchType patch = archive.getPatch(localPackage.getVersion());
      if (patch != null) {
        return Pair.of(patch.getSize(), true);
      }
    }
    return Pair.of(archive.getComplete().getSize(), false);
  }

  @VisibleForTesting
  static Pair<HtmlBuilder, HtmlBuilder> getDiskUsageMessages(@Nullable Path sdkRoot, long fullInstallationsDownloadSize,
                                                             long patchesDownloadSize, long spaceToBeFreedUp) {
    HtmlBuilder message = new HtmlBuilder();
    message.add("Disk usage:\n");
    boolean issueDiskSpaceWarning = false;
    message.beginList();
    if (spaceToBeFreedUp > 0) {
      message.listItem().add("Disk space that will be freed: " + new Storage(spaceToBeFreedUp).toUiString());
    }
    long totalDownloadSize = patchesDownloadSize + fullInstallationsDownloadSize;
    if (totalDownloadSize > 0) {
      message.listItem().add("Estimated download size: " + new Storage(totalDownloadSize).toUiString());
      long sdkRootUsageAfterInstallation = patchesDownloadSize + ESTIMATED_ZIP_DECOMPRESSION_RATE * fullInstallationsDownloadSize
                                           - spaceToBeFreedUp;
      message.listItem().add("Estimated disk space to be additionally occupied on SDK partition after installation: "
                             + new Storage(sdkRootUsageAfterInstallation).toUiString());
      if (sdkRoot != null) {
        long sdkRootUsableSpace = 0;
        try {
          sdkRootUsableSpace = Files.getFileStore(sdkRoot).getUsableSpace();
        }
        catch (IOException ignore) {
          // We'll just say there's 0 usable space
        }
        message.listItem().add(String.format("Currently available disk space in SDK root (%1$s): %2$s", sdkRoot.toAbsolutePath(),
                                             new Storage(sdkRootUsableSpace).toUiString()));
        long totalSdkUsableSpace = sdkRootUsableSpace + spaceToBeFreedUp;
        issueDiskSpaceWarning = (totalSdkUsableSpace < sdkRootUsageAfterInstallation);
      }
    }
    message.endList();
    if (issueDiskSpaceWarning) {
      HtmlBuilder warningMessage = new HtmlBuilder();
      warningMessage.beginColor(JBColor.RED)
                    .addBold("WARNING: There might be insufficient disk space to perform this operation. ")
                    .newline().newline()
                    .add("Estimated disk usage is presented below. ")
                    .add("Consider freeing up more disk space before proceeding. ")
                    .endColor()
                    .newline().newline();
      return Pair.of(message, warningMessage);
    }
    return Pair.of(message, null);
  }

  static boolean confirmChange(HtmlBuilder message) {
    String[] options = {Messages.getCancelButton(), Messages.getOkButton()};
    Icon icon = AllIcons.General.Warning;

    // I would use showOkCancelDialog but Mac sheet panels do not gracefully handle long messages and their buttons can display offscree
    return Messages.showIdeaMessageDialog(null, message.getHtml(), "Confirm Change", options, 1, icon, null) == 1;
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void disposeUIResources() {
    if (myPanel != null)
      Disposer.dispose(myPanel);
  }
}
