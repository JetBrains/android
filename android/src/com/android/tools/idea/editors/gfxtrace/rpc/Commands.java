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
      CaptureId[] value;

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
      DeviceId[] value;

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
      CaptureId capture;
      long contextId;
      long after;

      Call() {
      }

      Call(CaptureId capture, long contextId, long after) {
        this.capture = capture;
        this.contextId = contextId;
        this.after = after;
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
      BinaryId value;

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
      CaptureId capture;
      long contextId;

      Call() {
      }

      Call(CaptureId capture, long contextId) {
        this.capture = capture;
        this.contextId = contextId;
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
      HierarchyId value;

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
      CaptureId capture;
      long contextId;
      long after;
      MemoryRange rng;

      Call() {
      }

      Call(CaptureId capture, long contextId, long after, MemoryRange rng) {
        this.capture = capture;
        this.contextId = contextId;
        this.after = after;
        this.rng = rng;
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
      MemoryInfoId value;

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
      DeviceId device;
      CaptureId capture;
      long contextId;
      long after;
      RenderSettings settings;

      Call() {
      }

      Call(DeviceId device, CaptureId capture, long contextId, long after, RenderSettings settings) {
        this.device = device;
        this.capture = capture;
        this.contextId = contextId;
        this.after = after;
        this.settings = settings;
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
      ImageInfoId value;

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
      DeviceId device;
      CaptureId capture;
      long contextId;
      long after;

      Call() {
      }

      Call(DeviceId device, CaptureId capture, long contextId, long after) {
        this.device = device;
        this.capture = capture;
        this.contextId = contextId;
        this.after = after;
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
      ImageInfoId value;

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

  static final class GetGlErrorCodes {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      DeviceId device;
      CaptureId capture;
      long contextId;

      Call() {
      }

      Call(DeviceId device, CaptureId capture, long contextId) {
        this.device = device;
        this.capture = capture;
        this.contextId = contextId;
      }

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.callGetGlErrorCodesID;
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
      BinaryId value;

      @Override
      public ObjectTypeID type() {
        return ObjectFactory.resultGetGlErrorCodesID;
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
      CaptureId capture;
      long atomId;
      int atomType;
      Binary data;

      Call() {
      }

      Call(CaptureId capture, long atomId, int atomType, Binary data) {
        this.capture = capture;
        this.atomId = atomId;
        this.atomType = atomType;
        this.data = data;
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
      CaptureId value;

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
      DeviceId device;
      CaptureId capture;
      long contextId;
      TimingMask mask;

      Call() {
      }

      Call(DeviceId device, CaptureId capture, long contextId, TimingMask mask) {
        this.device = device;
        this.capture = capture;
        this.contextId = contextId;
        this.mask = mask;
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
      TimingInfoId value;

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

  static final class ResolveAtomStream {
    static final class Call implements com.android.tools.rpclib.rpccore.Call {
      AtomStreamId id;

      Call() {
      }

      Call(AtomStreamId id) {
        this.id = id;
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
      AtomStream value;

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
      BinaryId id;

      Call() {
      }

      Call(BinaryId id) {
        this.id = id;
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
      Binary value;

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
      CaptureId id;

      Call() {
      }

      Call(CaptureId id) {
        this.id = id;
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
      Capture value;

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
      DeviceId id;

      Call() {
      }

      Call(DeviceId id) {
        this.id = id;
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
      Device value;

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
      HierarchyId id;

      Call() {
      }

      Call(HierarchyId id) {
        this.id = id;
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
      Hierarchy value;

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
      ImageInfoId id;

      Call() {
      }

      Call(ImageInfoId id) {
        this.id = id;
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
      ImageInfo value;

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
      MemoryInfoId id;

      Call() {
      }

      Call(MemoryInfoId id) {
        this.id = id;
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
      MemoryInfo value;

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
      SchemaId id;

      Call() {
      }

      Call(SchemaId id) {
        this.id = id;
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
      Schema value;

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
      TimingInfoId id;

      Call() {
      }

      Call(TimingInfoId id) {
        this.id = id;
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
      TimingInfo value;

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