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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.rpc.*;
import com.android.tools.rpclib.rpccore.RpcException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class ImageFetcher {
  @NotNull private static final Logger LOG = Logger.getInstance(ImageFetcher.class);
  @NotNull private Client myClient;
  private DeviceId myDeviceId;
  private CaptureId myCaptureId;
  private Integer myContextId;

  public ImageFetcher(@NotNull Client client) {
    myClient = client;
  }

  public void prepareFetch(@NotNull DeviceId deviceId, @NotNull CaptureId captureId, @NotNull Integer contextId) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDeviceId = deviceId;
    myCaptureId = captureId;
    myContextId = contextId;
  }

  @Nullable
  public ImageFetchHandle queueColorImage(long atomId, RenderSettings settings) {
    try {
      return new ImageFetchHandle(myClient.GetFramebufferColor(myDeviceId, myCaptureId, myContextId, atomId, settings).get());
    }
    catch (InterruptedException e) {
      LOG.error(e);
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (RpcException e) {
      LOG.error(e);
    }
    return null;
  }

  @Nullable
  public ImageFetchHandle queueDepthImage(long atomId) {
    try {
      return new ImageFetchHandle(myClient.GetFramebufferDepth(myDeviceId, myCaptureId, myContextId, atomId).get());
    }
    catch (InterruptedException e) {
      LOG.error(e);
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (RpcException e) {
      LOG.error(e);
    }
    return null;
  }

  @Nullable
  public FetchedImage resolveImage(@NotNull ImageFetchHandle handle) {
    try {
      ImageInfo imageInfo = handle.getImageInfo();
      if (imageInfo == null) {
        imageInfo = myClient.ResolveImageInfo(handle.getImageInfoId()).get();
      }

      handle.setImageInfo(imageInfo);
      Binary binary = handle.getBinary();
      if (binary == null) {
        binary = myClient.ResolveBinary(imageInfo.getData()).get();
        handle.setBinary(binary);
      }

      handle.setBinary(binary);
      return new FetchedImage(handle.getImageInfo(), binary);
    }
    catch (InterruptedException e) {
      LOG.error(e);
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
    catch (RpcException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  public static class ImageFetchHandle {
    @NotNull private ImageInfoId myImageInfoId;
    private ImageInfo myImageInfo;
    private Binary myBinary;

    private ImageFetchHandle(@NotNull ImageInfoId imageInfoId) {
      myImageInfoId = imageInfoId;
    }

    public ImageInfo getImageInfo() {
      return myImageInfo;
    }

    public void setImageInfo(@NotNull ImageInfo imageInfo) {
      myImageInfo = imageInfo;
    }

    public Binary getBinary() {
      return myBinary;
    }

    public void setBinary(@NotNull Binary binary) {
      myBinary = binary;
    }

    @NotNull
    private ImageInfoId getImageInfoId() {
      return myImageInfoId;
    }
  }
}
