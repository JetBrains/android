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
*
* THIS WILL BE REMOVED ONCE THE CODE GENERATOR IS INTEGRATED INTO THE BUILD.
*/
package com.android.tools.idea.editors.gfxtrace.rpc;

import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.ObjectTypeID;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ImageInfo implements BinaryObject {
  ImageFormat myFormat;
  int myWidth;
  int myHeight;
  BinaryId myData;

  // Constructs a default-initialized {@link ImageInfo}.
  public ImageInfo() {
  }

  // Constructs and decodes a {@link ImageInfo} from the {@link Decoder} d.
  public ImageInfo(Decoder d) throws IOException {
    decode(d);
  }

  public ImageFormat getFormat() {
    return myFormat;
  }

  public void setFormat(ImageFormat v) {
    myFormat = v;
  }

  public int getWidth() {
    return myWidth;
  }

  public void setWidth(int v) {
    myWidth = v;
  }

  public int getHeight() {
    return myHeight;
  }

  public void setHeight(int v) {
    myHeight = v;
  }

  public BinaryId getData() {
    return myData;
  }

  public void setData(BinaryId v) {
    myData = v;
  }

  @Override
  public void encode(@NotNull Encoder e) throws IOException {
    ObjectFactory.encode(e, this);
  }

  @Override
  public void decode(@NotNull Decoder d) throws IOException {
    ObjectFactory.decode(d, this);
  }

  @Override
  public ObjectTypeID type() {
    return ObjectFactory.ImageInfoID;
  }
}