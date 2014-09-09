/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.*;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.intellij.openapi.Disposable;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class ApkUploaderService implements AndroidDebugBridge.IDeviceChangeListener, Disposable {

  /**
   * A map from device serial -> apk path -> content hashcode.
   * The path used is the remote path which is basically the package name. This way the cache gives us, for each device, the hashcode
   * of each application that was uploaded.
   */
  private final Map<String, Map<String, HashCode>> myCache = Maps.newHashMap();

  public ApkUploaderService() {
    AndroidDebugBridge.addDeviceChangeListener(this);
  }

  @Override
  public void dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(this);
  }

  public UploadResult uploadApk(IDevice device, String localPath, String remotePath, SyncService.ISyncProgressMonitor monitor)
    throws AdbCommandRejectedException, IOException, TimeoutException, SyncException {

    HashCode hash = Files.hash(new File(localPath), Hashing.goodFastHash(32));

    String serial = device.getSerialNumber();
    Map<String, HashCode> cache = myCache.get(serial);
    if (cache != null) {
      HashCode got = cache.get(remotePath);
      if (hash.equals(got)) {
        return UploadResult.CACHED;
      }
      else {
        // Remove it in case there is an error uploading the apk.
        cache.remove(remotePath);
      }
    }
    else {
      cache = Maps.newHashMap();
      myCache.put(serial, cache);
    }

    SyncService service = device.getSyncService();
    if (service == null) {
      return UploadResult.FAILED;
    }
    service.pushFile(localPath, remotePath, monitor);
    cache.put(remotePath, hash);
    return UploadResult.SUCCESS;
  }

  @Override
  public void deviceConnected(IDevice device) {
  }

  @Override
  public void deviceDisconnected(IDevice device) {
    myCache.remove(device.getSerialNumber());
  }

  @Override
  public void deviceChanged(IDevice device, int changeMask) {
  }

  public enum UploadResult {
    SUCCESS,
    FAILED,
    CACHED
  }
}
