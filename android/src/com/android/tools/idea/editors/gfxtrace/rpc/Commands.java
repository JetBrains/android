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

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.ObjectTypeID;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

final class Commands {

  static final class GetCaptures {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      Call() {
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callGetCapturesID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      CaptureId[] myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultGetCapturesID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class GetDevices {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      Call() {
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callGetDevicesID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      DeviceId[] myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultGetDevicesID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class GetState {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      CaptureId myCapture;
      int myContextId;
      long myAfter;

      Call() {
      }

      Call(CaptureId capture, int contextId, long after) {
        myCapture = capture;
        myContextId = contextId;
        myAfter = after;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callGetStateID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      BinaryId myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultGetStateID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class GetHierarchy {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      CaptureId myCapture;
      int myContextId;

      Call() {
      }

      Call(CaptureId capture, int contextId) {
        myCapture = capture;
        myContextId = contextId;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callGetHierarchyID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      HierarchyId myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultGetHierarchyID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class GetMemoryInfo {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      CaptureId myCapture;
      int myContextId;
      long myAfter;
      MemoryRange myRng;

      Call() {
      }

      Call(CaptureId capture, int contextId, long after, MemoryRange rng) {
        myCapture = capture;
        myContextId = contextId;
        myAfter = after;
        myRng = rng;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callGetMemoryInfoID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      MemoryInfoId myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultGetMemoryInfoID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class GetFramebufferColor {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      DeviceId myDevice;
      CaptureId myCapture;
      int myContextId;
      long myAfter;
      RenderSettings mySettings;

      Call() {
      }

      Call(DeviceId device, CaptureId capture, int contextId, long after, RenderSettings settings) {
        myDevice = device;
        myCapture = capture;
        myContextId = contextId;
        myAfter = after;
        mySettings = settings;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callGetFramebufferColorID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      ImageInfoId myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultGetFramebufferColorID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class GetFramebufferDepth {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      DeviceId myDevice;
      CaptureId myCapture;
      int myContextId;
      long myAfter;

      Call() {
      }

      Call(DeviceId device, CaptureId capture, int contextId, long after) {
        myDevice = device;
        myCapture = capture;
        myContextId = contextId;
        myAfter = after;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callGetFramebufferDepthID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      ImageInfoId myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultGetFramebufferDepthID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class ReplaceAtom {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      CaptureId myCapture;
      long myAtomId;
      short myAtomType;
      Binary myData;

      Call() {
      }

      Call(CaptureId capture, long atomId, short atomType, Binary data) {
        myCapture = capture;
        myAtomId = atomId;
        myAtomType = atomType;
        myData = data;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callReplaceAtomID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      CaptureId myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultReplaceAtomID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class GetTimingInfo {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      DeviceId myDevice;
      CaptureId myCapture;
      int myContextId;
      TimingMask myMask;

      Call() {
      }

      Call(DeviceId device, CaptureId capture, int contextId, TimingMask mask) {
        myDevice = device;
        myCapture = capture;
        myContextId = contextId;
        myMask = mask;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callGetTimingInfoID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      TimingInfoId myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultGetTimingInfoID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class PrerenderFramebuffers {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      DeviceId myDevice;
      CaptureId myCapture;
      int myWidth;
      int myHeight;
      long[] myAtomIds;

      Call() {
      }

      Call(DeviceId device, CaptureId capture, int width, int height, long[] atomIds) {
        myDevice = device;
        myCapture = capture;
        myWidth = width;
        myHeight = height;
        myAtomIds = atomIds;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callPrerenderFramebuffersID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      BinaryId myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultPrerenderFramebuffersID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class ResolveAtomStream {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      AtomStreamId myId;

      Call() {
      }

      Call(AtomStreamId id) {
        myId = id;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callResolveAtomStreamID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      AtomStream myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultResolveAtomStreamID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class ResolveBinary {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      BinaryId myId;

      Call() {
      }

      Call(BinaryId id) {
        myId = id;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callResolveBinaryID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      Binary myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultResolveBinaryID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class ResolveCapture {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      CaptureId myId;

      Call() {
      }

      Call(CaptureId id) {
        myId = id;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callResolveCaptureID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      Capture myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultResolveCaptureID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class ResolveDevice {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      DeviceId myId;

      Call() {
      }

      Call(DeviceId id) {
        myId = id;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callResolveDeviceID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      Device myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultResolveDeviceID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class ResolveHierarchy {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      HierarchyId myId;

      Call() {
      }

      Call(HierarchyId id) {
        myId = id;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callResolveHierarchyID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      Hierarchy myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultResolveHierarchyID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class ResolveImageInfo {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      ImageInfoId myId;

      Call() {
      }

      Call(ImageInfoId id) {
        myId = id;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callResolveImageInfoID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      ImageInfo myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultResolveImageInfoID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class ResolveMemoryInfo {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      MemoryInfoId myId;

      Call() {
      }

      Call(MemoryInfoId id) {
        myId = id;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callResolveMemoryInfoID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      MemoryInfo myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultResolveMemoryInfoID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class ResolveSchema {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      SchemaId myId;

      Call() {
      }

      Call(SchemaId id) {
        myId = id;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callResolveSchemaID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      Schema myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultResolveSchemaID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }

  static final class ResolveTimingInfo {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      TimingInfoId myId;

      Call() {
      }

      Call(TimingInfoId id) {
        myId = id;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callResolveTimingInfoID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

    static final class Result implements com.android.tools.rpclib.rpccore.Result {
      TimingInfo myValue;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultResolveTimingInfoID;
      }

      @Override
      public void encode(@NotNull Encoder e) throws IOException {
        ObjectFactory.encode(e, this);
      }

      @Override
      public void decode(@NotNull Decoder d) throws IOException {
        ObjectFactory.decode(d, this);
      }
    }

  }
}