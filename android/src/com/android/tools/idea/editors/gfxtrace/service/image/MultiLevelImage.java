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
package com.android.tools.idea.editors.gfxtrace.service.image;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.util.ui.UIUtil;

import java.awt.image.BufferedImage;

public interface MultiLevelImage {
  int getLevelCount();

  ListenableFuture<BufferedImage> getLevel(int index);

  BufferedImage EMPTY_LEVEL = UIUtil.createImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);

  MultiLevelImage EMPTY_IMAGE = new MultiLevelImage() {
    @Override
    public int getLevelCount() {
      return 1;
    }

    @Override
    public ListenableFuture<BufferedImage> getLevel(int index) {
      return Futures.immediateFuture(EMPTY_LEVEL);
    }
  };
}
