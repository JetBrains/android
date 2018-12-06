package org.jetbrains.android.uipreview;

import com.android.SdkConstants;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceParser;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.xml.parsers.ParserConfigurationException;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

/**
 * Manages user created devices.  These devices come from the user's devices.xml, which is present at $HOME/.android/devices.xml.
 * The list of devices are cached and updated automatically as the user's devices.xml changes.
 */
public class UserDeviceManager {
  @NotNull private final ILogger logger = new MessageBuildingSdkLog();
  @Nullable private CachedValue<ImmutableList<Device>> myDevices;
  @Nullable private VirtualFile myDevicesFile;

  public static UserDeviceManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, UserDeviceManager.class);
  }

  private UserDeviceManager(@NotNull Project project) {
    myDevicesFile = getUserDevicesFile(logger);
    if (myDevicesFile != null) {
      myDevices = CachedValuesManager.getManager(project).createCachedValue(
        () -> new CachedValueProvider.Result<>(parseUserDevicesFile(myDevicesFile, logger), myDevicesFile));
    }
  }

  /**
   * Returns a list of user (a.k.a non SDK) devices. An empty list is returned when there are no user devices defined or
   * when the user devices file cannot be found.
   */
  @NotNull
  public ImmutableList<Device> getUserDevices() {
    if (myDevices == null) {
      return ImmutableList.of();
    }
    else {
      return myDevices.getValue();
    }
  }

  @NotNull
  private static ImmutableList<Device> parseUserDevicesFile(@Nullable VirtualFile userDevicesFile, @NotNull ILogger logger) {
    if (userDevicesFile == null) {
      return ImmutableList.of();
    }

    File ioFile = new File(userDevicesFile.getPath());
    if (!ioFile.exists()) {
      return ImmutableList.of();
    }

    final ArrayList<Device> userDevices = new ArrayList<>();
    try {
      userDevices.addAll(DeviceParser.parse(ioFile).values());
    }
    catch (SAXException e) {
      final String newName = ioFile.getAbsoluteFile() + ".old";
      File renamedConfig = new File(newName);
      int i = 0;

      while (renamedConfig.exists()) {
        renamedConfig = new File(newName + '.' + i);
        i++;
      }
      logger.error(e, "Error parsing " + ioFile.getAbsolutePath() +
                      ", backing up to " + renamedConfig.getAbsolutePath());

      if (!ioFile.renameTo(renamedConfig)) {
        logger.error(e, "Cannot rename file " + ioFile.getAbsolutePath() + " to " + renamedConfig.getAbsolutePath());
      }
    }
    catch (ParserConfigurationException e) {
      logger.error(null, "Error parsing " + ioFile.getAbsolutePath());
    }
    catch (IOException e) {
      logger.error(null, "Error parsing " + ioFile.getAbsolutePath());
    }

    return ImmutableList.copyOf(userDevices);
  }

  @Nullable
  private static VirtualFile getUserDevicesFile(ILogger logger) {
    try {
      String myFolderToStoreDevicesXml = AndroidLocation.getFolder();
      return LocalFileSystem.getInstance().findFileByIoFile(new File(myFolderToStoreDevicesXml, SdkConstants.FN_DEVICES_XML));
    }
    catch (AndroidLocation.AndroidLocationException e) {
      logger.warning("Couldn't load user devices: " + e.getMessage());
      return null;
    }
  }
}
