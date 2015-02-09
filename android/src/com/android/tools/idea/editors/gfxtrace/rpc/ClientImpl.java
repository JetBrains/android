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

import com.android.tools.rpclib.rpccore.Broadcaster;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ClientImpl implements Client {
  private final Broadcaster broadcaster;
  private final ExecutorService executorService;

  public ClientImpl(ExecutorService executorService, InputStream in, OutputStream out, int mtu) {
    this.executorService = executorService;
    broadcaster = new Broadcaster(in, out, mtu);
  }

  @Override
  public Future<CaptureId[]> GetCaptures() {
    return executorService.submit(new GetCapturesCallable());
  }

  @Override
  public Future<DeviceId[]> GetDevices() {
    return executorService.submit(new GetDevicesCallable());
  }

  @Override
  public Future<BinaryId> GetState(CaptureId capture, long contextId, long after) {
    return executorService.submit(new GetStateCallable(capture, contextId, after));
  }

  @Override
  public Future<HierarchyId> GetHierarchy(CaptureId capture, long contextId) {
    return executorService.submit(new GetHierarchyCallable(capture, contextId));
  }

  @Override
  public Future<MemoryInfoId> GetMemoryInfo(CaptureId capture, long contextId, long after, MemoryRange rng) {
    return executorService.submit(new GetMemoryInfoCallable(capture, contextId, after, rng));
  }

  @Override
  public Future<ImageInfoId> GetFramebufferColor(DeviceId device, CaptureId capture, long contextId, long after, RenderSettings settings) {
    return executorService.submit(new GetFramebufferColorCallable(device, capture, contextId, after, settings));
  }

  @Override
  public Future<ImageInfoId> GetFramebufferDepth(DeviceId device, CaptureId capture, long contextId, long after) {
    return executorService.submit(new GetFramebufferDepthCallable(device, capture, contextId, after));
  }

  @Override
  public Future<BinaryId> GetGlErrorCodes(DeviceId device, CaptureId capture, long contextId) {
    return executorService.submit(new GetGlErrorCodesCallable(device, capture, contextId));
  }

  @Override
  public Future<CaptureId> ReplaceAtom(CaptureId capture, long atomId, int atomType, Binary data) {
    return executorService.submit(new ReplaceAtomCallable(capture, atomId, atomType, data));
  }

  @Override
  public Future<TimingInfoId> GetTimingInfo(DeviceId device, CaptureId capture, long contextId, TimingMask mask) {
    return executorService.submit(new GetTimingInfoCallable(device, capture, contextId, mask));
  }

  @Override
  public Future<AtomStream> ResolveAtomStream(AtomStreamId id) {
    return executorService.submit(new ResolveAtomStreamCallable(id));
  }

  @Override
  public Future<Binary> ResolveBinary(BinaryId id) {
    return executorService.submit(new ResolveBinaryCallable(id));
  }

  @Override
  public Future<Capture> ResolveCapture(CaptureId id) {
    return executorService.submit(new ResolveCaptureCallable(id));
  }

  @Override
  public Future<Device> ResolveDevice(DeviceId id) {
    return executorService.submit(new ResolveDeviceCallable(id));
  }

  @Override
  public Future<Hierarchy> ResolveHierarchy(HierarchyId id) {
    return executorService.submit(new ResolveHierarchyCallable(id));
  }

  @Override
  public Future<ImageInfo> ResolveImageInfo(ImageInfoId id) {
    return executorService.submit(new ResolveImageInfoCallable(id));
  }

  @Override
  public Future<MemoryInfo> ResolveMemoryInfo(MemoryInfoId id) {
    return executorService.submit(new ResolveMemoryInfoCallable(id));
  }

  @Override
  public Future<Schema> ResolveSchema(SchemaId id) {
    return executorService.submit(new ResolveSchemaCallable(id));
  }

  @Override
  public Future<TimingInfo> ResolveTimingInfo(TimingInfoId id) {
    return executorService.submit(new ResolveTimingInfoCallable(id));
  }


  private class GetCapturesCallable implements Callable<CaptureId[]> {
    private final Commands.GetCaptures.Call call;

    private GetCapturesCallable() {
      this.call = new Commands.GetCaptures.Call();
    }

    @Override
    public CaptureId[] call() throws Exception {
      Commands.GetCaptures.Result result = (Commands.GetCaptures.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class GetDevicesCallable implements Callable<DeviceId[]> {
    private final Commands.GetDevices.Call call;

    private GetDevicesCallable() {
      this.call = new Commands.GetDevices.Call();
    }

    @Override
    public DeviceId[] call() throws Exception {
      Commands.GetDevices.Result result = (Commands.GetDevices.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class GetStateCallable implements Callable<BinaryId> {
    private final Commands.GetState.Call call;

    private GetStateCallable(CaptureId capture, long contextId, long after) {
      this.call = new Commands.GetState.Call(capture, contextId, after);
    }

    @Override
    public BinaryId call() throws Exception {
      Commands.GetState.Result result = (Commands.GetState.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class GetHierarchyCallable implements Callable<HierarchyId> {
    private final Commands.GetHierarchy.Call call;

    private GetHierarchyCallable(CaptureId capture, long contextId) {
      this.call = new Commands.GetHierarchy.Call(capture, contextId);
    }

    @Override
    public HierarchyId call() throws Exception {
      Commands.GetHierarchy.Result result = (Commands.GetHierarchy.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class GetMemoryInfoCallable implements Callable<MemoryInfoId> {
    private final Commands.GetMemoryInfo.Call call;

    private GetMemoryInfoCallable(CaptureId capture, long contextId, long after, MemoryRange rng) {
      this.call = new Commands.GetMemoryInfo.Call(capture, contextId, after, rng);
    }

    @Override
    public MemoryInfoId call() throws Exception {
      Commands.GetMemoryInfo.Result result = (Commands.GetMemoryInfo.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class GetFramebufferColorCallable implements Callable<ImageInfoId> {
    private final Commands.GetFramebufferColor.Call call;

    private GetFramebufferColorCallable(DeviceId device, CaptureId capture, long contextId, long after, RenderSettings settings) {
      this.call = new Commands.GetFramebufferColor.Call(device, capture, contextId, after, settings);
    }

    @Override
    public ImageInfoId call() throws Exception {
      Commands.GetFramebufferColor.Result result = (Commands.GetFramebufferColor.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class GetFramebufferDepthCallable implements Callable<ImageInfoId> {
    private final Commands.GetFramebufferDepth.Call call;

    private GetFramebufferDepthCallable(DeviceId device, CaptureId capture, long contextId, long after) {
      this.call = new Commands.GetFramebufferDepth.Call(device, capture, contextId, after);
    }

    @Override
    public ImageInfoId call() throws Exception {
      Commands.GetFramebufferDepth.Result result = (Commands.GetFramebufferDepth.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class GetGlErrorCodesCallable implements Callable<BinaryId> {
    private final Commands.GetGlErrorCodes.Call call;

    private GetGlErrorCodesCallable(DeviceId device, CaptureId capture, long contextId) {
      this.call = new Commands.GetGlErrorCodes.Call(device, capture, contextId);
    }

    @Override
    public BinaryId call() throws Exception {
      Commands.GetGlErrorCodes.Result result = (Commands.GetGlErrorCodes.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class ReplaceAtomCallable implements Callable<CaptureId> {
    private final Commands.ReplaceAtom.Call call;

    private ReplaceAtomCallable(CaptureId capture, long atomId, int atomType, Binary data) {
      this.call = new Commands.ReplaceAtom.Call(capture, atomId, atomType, data);
    }

    @Override
    public CaptureId call() throws Exception {
      Commands.ReplaceAtom.Result result = (Commands.ReplaceAtom.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class GetTimingInfoCallable implements Callable<TimingInfoId> {
    private final Commands.GetTimingInfo.Call call;

    private GetTimingInfoCallable(DeviceId device, CaptureId capture, long contextId, TimingMask mask) {
      this.call = new Commands.GetTimingInfo.Call(device, capture, contextId, mask);
    }

    @Override
    public TimingInfoId call() throws Exception {
      Commands.GetTimingInfo.Result result = (Commands.GetTimingInfo.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class ResolveAtomStreamCallable implements Callable<AtomStream> {
    private final Commands.ResolveAtomStream.Call call;

    private ResolveAtomStreamCallable(AtomStreamId id) {
      this.call = new Commands.ResolveAtomStream.Call(id);
    }

    @Override
    public AtomStream call() throws Exception {
      Commands.ResolveAtomStream.Result result = (Commands.ResolveAtomStream.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class ResolveBinaryCallable implements Callable<Binary> {
    private final Commands.ResolveBinary.Call call;

    private ResolveBinaryCallable(BinaryId id) {
      this.call = new Commands.ResolveBinary.Call(id);
    }

    @Override
    public Binary call() throws Exception {
      Commands.ResolveBinary.Result result = (Commands.ResolveBinary.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class ResolveCaptureCallable implements Callable<Capture> {
    private final Commands.ResolveCapture.Call call;

    private ResolveCaptureCallable(CaptureId id) {
      this.call = new Commands.ResolveCapture.Call(id);
    }

    @Override
    public Capture call() throws Exception {
      Commands.ResolveCapture.Result result = (Commands.ResolveCapture.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class ResolveDeviceCallable implements Callable<Device> {
    private final Commands.ResolveDevice.Call call;

    private ResolveDeviceCallable(DeviceId id) {
      this.call = new Commands.ResolveDevice.Call(id);
    }

    @Override
    public Device call() throws Exception {
      Commands.ResolveDevice.Result result = (Commands.ResolveDevice.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class ResolveHierarchyCallable implements Callable<Hierarchy> {
    private final Commands.ResolveHierarchy.Call call;

    private ResolveHierarchyCallable(HierarchyId id) {
      this.call = new Commands.ResolveHierarchy.Call(id);
    }

    @Override
    public Hierarchy call() throws Exception {
      Commands.ResolveHierarchy.Result result = (Commands.ResolveHierarchy.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class ResolveImageInfoCallable implements Callable<ImageInfo> {
    private final Commands.ResolveImageInfo.Call call;

    private ResolveImageInfoCallable(ImageInfoId id) {
      this.call = new Commands.ResolveImageInfo.Call(id);
    }

    @Override
    public ImageInfo call() throws Exception {
      Commands.ResolveImageInfo.Result result = (Commands.ResolveImageInfo.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class ResolveMemoryInfoCallable implements Callable<MemoryInfo> {
    private final Commands.ResolveMemoryInfo.Call call;

    private ResolveMemoryInfoCallable(MemoryInfoId id) {
      this.call = new Commands.ResolveMemoryInfo.Call(id);
    }

    @Override
    public MemoryInfo call() throws Exception {
      Commands.ResolveMemoryInfo.Result result = (Commands.ResolveMemoryInfo.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class ResolveSchemaCallable implements Callable<Schema> {
    private final Commands.ResolveSchema.Call call;

    private ResolveSchemaCallable(SchemaId id) {
      this.call = new Commands.ResolveSchema.Call(id);
    }

    @Override
    public Schema call() throws Exception {
      Commands.ResolveSchema.Result result = (Commands.ResolveSchema.Result)broadcaster.Send(call);
      return result.value;
    }
  }

  private class ResolveTimingInfoCallable implements Callable<TimingInfo> {
    private final Commands.ResolveTimingInfo.Call call;

    private ResolveTimingInfoCallable(TimingInfoId id) {
      this.call = new Commands.ResolveTimingInfo.Call(id);
    }

    @Override
    public TimingInfo call() throws Exception {
      Commands.ResolveTimingInfo.Result result = (Commands.ResolveTimingInfo.Result)broadcaster.Send(call);
      return result.value;
    }
  }
}