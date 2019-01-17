/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.runtimePipeline;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.devices.Abi;
import com.google.common.base.Charsets;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public abstract class DeployableFileManager {
  protected static final String DEVICE_BASE_DIR = "/data/local/tmp/";

  @NotNull protected final IDevice myDevice;

  protected DeployableFileManager(@NotNull IDevice device) {
    myDevice = device;
  }

  private static Logger getLogger() {
    return Logger.getInstance(DeployableFileManager.class);
  }

  /**
   * @return the String representing the subdirectory under {@code DEVICE_BASE_DIR} where files will be deployed
   */
  @NotNull
  protected abstract String getDeviceSubDir();

  @NotNull
  public String getDeviceFullDir() {
    return DEVICE_BASE_DIR + getDeviceSubDir();
  }

  /**
   * Copies a file from host (where Studio is running) to the device.
   * If executable, then the abi is taken into account.
   */
  protected void copyFileToDevice(@NotNull DeployableFile hostFile)
    throws AdbCommandRejectedException, IOException {
    final File dir = hostFile.getDir();

    if (!hostFile.isExecutable()) {
      File file = new File(dir, hostFile.getFileName());
      pushFileToDevice(file, hostFile.getFileName(), hostFile.isExecutable());
      return;
    }

    if (!hostFile.isAbiDependent()) {
      Abi abi = getBestAbi(hostFile);
      File file = new File(dir, abi + "/" + hostFile.getFileName());
      pushFileToDevice(file, hostFile.getFileName(), true);
    }
    else {
      String format = hostFile.getOnDeviceAbiFileNameFormat();
      assert format != null;
      for (Abi abi : getBestAbis(hostFile)) {
        File file = new File(dir, abi + "/" + hostFile.getFileName());
        pushFileToDevice(file, String.format(format, abi.getCpuArch()), true);
      }
    }
  }

  private void pushFileToDevice(File file, String fileName, boolean executable)
    throws AdbCommandRejectedException, IOException {
    try {
      // TODO: Handle the case where we don't have file for this platform.
      if (file == null) {
        throw new RuntimeException(String.format("File %s could not be found for device: %s", fileName, myDevice));
      }
      /*
       * If copying the agent fails, we will attach the previous version of the agent
       * Hence we first delete old agent before copying new one
       */
      getLogger().info(String.format("Pushing %s to %s...", fileName, getDeviceFullDir()));
      myDevice.executeShellCommand("rm -f " + getDeviceFullDir() + fileName, new NullOutputReceiver());
      myDevice.executeShellCommand("mkdir -p " + getDeviceFullDir(), new NullOutputReceiver());
      myDevice.pushFile(file.getAbsolutePath(), getDeviceFullDir() + fileName);

      if (executable) {
        /*
         * In older devices, chmod letter usage isn't fully supported but CTS tests have been added for it since.
         * Hence we first try the letter scheme which is guaranteed in newer devices, and fall back to the octal scheme only if necessary.
         */
        ChmodOutputListener chmodListener = new ChmodOutputListener();
        myDevice.executeShellCommand("chmod +x " + getDeviceFullDir() + fileName, chmodListener);
        if (chmodListener.hasErrors()) {
          myDevice.executeShellCommand("chmod 777 " + getDeviceFullDir() + fileName, new NullOutputReceiver());
        }
      }
      getLogger().info(String.format("Successfully pushed %s to %s.", fileName, getDeviceFullDir()));
    }
    catch (TimeoutException | SyncException | ShellCommandUnresponsiveException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private Abi getBestAbi(@NotNull DeployableFile hostFile) {
    return getBestAbis(hostFile).get(0);
  }

  @NotNull
  private List<Abi> getBestAbis(@NotNull DeployableFile hostFile) {
    final File dir = hostFile.getDir();
    List<Abi> supportedAbis = myDevice.getAbis()
                                      .stream()
                                      .map(abi -> Abi.getEnum(abi))
                                      .filter(abi -> new File(dir, abi + "/" + hostFile.getFileName()).exists())
                                      .collect(Collectors.toList());

    List<Abi> bestAbis = new ArrayList<>();
    Set<String> seenCpuArch = new HashSet<>();
    for (Abi abi : supportedAbis) {
      if (!seenCpuArch.contains(abi.getCpuArch())) {
        seenCpuArch.add(abi.getCpuArch());
        bestAbis.add(abi);
      }
    }
    return bestAbis;
  }

  private static class ChmodOutputListener implements IShellOutputReceiver {
    /**
     * When chmod fails to modify permissions, the following "Bad mode" error string is output.
     * This listener checks if the string is present to validate if chmod was successful.
     */
    private static final String BAD_MODE = "Bad mode";

    private boolean myHasErrors;

    @Override
    public void addOutput(byte[] data, int offset, int length) {
      String s = new String(data, Charsets.UTF_8);
      myHasErrors = s.contains(BAD_MODE);
    }

    @Override
    public void flush() {

    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    private boolean hasErrors() {
      return myHasErrors;
    }
  }
}
