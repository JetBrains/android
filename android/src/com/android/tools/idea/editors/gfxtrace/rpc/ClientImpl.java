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
  private final Broadcaster myBroadcaster;
  private final ExecutorService myExecutorService;

  public ClientImpl(ExecutorService executorService, InputStream in, OutputStream out, int mtu) {
    myExecutorService = executorService;
    myBroadcaster = new Broadcaster(in, out, mtu, myExecutorService);
  }

  @Override
  public Future<CaptureId[]> GetCaptures() {
    return myExecutorService.submit(new GetCapturesCallable());
  }

  @Override
  public Future<DeviceId[]> GetDevices() {
    return myExecutorService.submit(new GetDevicesCallable());
  }

  @Override
  public Future<BinaryId> GetState(CaptureId capture, int contextId, long after) {
    return myExecutorService.submit(new GetStateCallable(capture, contextId, after));
  }

  @Override
  public Future<HierarchyId> GetHierarchy(CaptureId capture, int contextId) {
    return myExecutorService.submit(new GetHierarchyCallable(capture, contextId));
  }

  @Override
  public Future<MemoryInfoId> GetMemoryInfo(CaptureId capture, int contextId, long after, MemoryRange rng) {
    return myExecutorService.submit(new GetMemoryInfoCallable(capture, contextId, after, rng));
  }

  @Override
  public Future<ImageInfoId> GetFramebufferColor(DeviceId device, CaptureId capture, int contextId, long after, RenderSettings settings) {
    return myExecutorService.submit(new GetFramebufferColorCallable(device, capture, contextId, after, settings));
  }

  @Override
  public Future<ImageInfoId> GetFramebufferDepth(DeviceId device, CaptureId capture, int contextId, long after) {
    return myExecutorService.submit(new GetFramebufferDepthCallable(device, capture, contextId, after));
  }

  @Override
  public Future<CaptureId> ReplaceAtom(CaptureId capture, long atomId, short atomType, Binary data) {
    return myExecutorService.submit(new ReplaceAtomCallable(capture, atomId, atomType, data));
  }

  @Override
  public Future<TimingInfoId> GetTimingInfo(DeviceId device, CaptureId capture, int contextId, TimingMask mask) {
    return myExecutorService.submit(new GetTimingInfoCallable(device, capture, contextId, mask));
  }

  @Override
  public Future<BinaryId> PrerenderFramebuffers(DeviceId device, CaptureId capture, int width, int height, long[] atomIds) {
    return myExecutorService.submit(new PrerenderFramebuffersCallable(device, capture, width, height, atomIds));
  }

  @Override
  public Future<AtomStream> ResolveAtomStream(AtomStreamId id) {
    return myExecutorService.submit(new ResolveAtomStreamCallable(id));
  }

  @Override
  public Future<Binary> ResolveBinary(BinaryId id) {
    return myExecutorService.submit(new ResolveBinaryCallable(id));
  }

  @Override
  public Future<Capture> ResolveCapture(CaptureId id) {
    return myExecutorService.submit(new ResolveCaptureCallable(id));
  }

  @Override
  public Future<Device> ResolveDevice(DeviceId id) {
    return myExecutorService.submit(new ResolveDeviceCallable(id));
  }

  @Override
  public Future<Hierarchy> ResolveHierarchy(HierarchyId id) {
    return myExecutorService.submit(new ResolveHierarchyCallable(id));
  }

  @Override
  public Future<ImageInfo> ResolveImageInfo(ImageInfoId id) {
    return myExecutorService.submit(new ResolveImageInfoCallable(id));
  }

  @Override
  public Future<MemoryInfo> ResolveMemoryInfo(MemoryInfoId id) {
    return myExecutorService.submit(new ResolveMemoryInfoCallable(id));
  }

  @Override
  public Future<Schema> ResolveSchema(SchemaId id) {
    return myExecutorService.submit(new ResolveSchemaCallable(id));
  }

  @Override
  public Future<TimingInfo> ResolveTimingInfo(TimingInfoId id) {
    return myExecutorService.submit(new ResolveTimingInfoCallable(id));
  }


  private class GetCapturesCallable implements Callable<CaptureId[]> {
    private final Commands.GetCaptures.Call myCall;

    private GetCapturesCallable() {
      myCall = new Commands.GetCaptures.Call();
    }

    @Override
    public CaptureId[] call() throws Exception {
      Commands.GetCaptures.Result result = (Commands.GetCaptures.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class GetDevicesCallable implements Callable<DeviceId[]> {
    private final Commands.GetDevices.Call myCall;

    private GetDevicesCallable() {
      myCall = new Commands.GetDevices.Call();
    }

    @Override
    public DeviceId[] call() throws Exception {
      Commands.GetDevices.Result result = (Commands.GetDevices.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class GetStateCallable implements Callable<BinaryId> {
    private final Commands.GetState.Call myCall;

    private GetStateCallable(CaptureId capture, int contextId, long after) {
      myCall = new Commands.GetState.Call(capture, contextId, after);
    }

    @Override
    public BinaryId call() throws Exception {
      Commands.GetState.Result result = (Commands.GetState.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class GetHierarchyCallable implements Callable<HierarchyId> {
    private final Commands.GetHierarchy.Call myCall;

    private GetHierarchyCallable(CaptureId capture, int contextId) {
      myCall = new Commands.GetHierarchy.Call(capture, contextId);
    }

    @Override
    public HierarchyId call() throws Exception {
      Commands.GetHierarchy.Result result = (Commands.GetHierarchy.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class GetMemoryInfoCallable implements Callable<MemoryInfoId> {
    private final Commands.GetMemoryInfo.Call myCall;

    private GetMemoryInfoCallable(CaptureId capture, int contextId, long after, MemoryRange rng) {
      myCall = new Commands.GetMemoryInfo.Call(capture, contextId, after, rng);
    }

    @Override
    public MemoryInfoId call() throws Exception {
      Commands.GetMemoryInfo.Result result = (Commands.GetMemoryInfo.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class GetFramebufferColorCallable implements Callable<ImageInfoId> {
    private final Commands.GetFramebufferColor.Call myCall;

    private GetFramebufferColorCallable(DeviceId device, CaptureId capture, int contextId, long after, RenderSettings settings) {
      myCall = new Commands.GetFramebufferColor.Call(device, capture, contextId, after, settings);
    }

    @Override
    public ImageInfoId call() throws Exception {
      Commands.GetFramebufferColor.Result result = (Commands.GetFramebufferColor.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class GetFramebufferDepthCallable implements Callable<ImageInfoId> {
    private final Commands.GetFramebufferDepth.Call myCall;

    private GetFramebufferDepthCallable(DeviceId device, CaptureId capture, int contextId, long after) {
      myCall = new Commands.GetFramebufferDepth.Call(device, capture, contextId, after);
    }

    @Override
    public ImageInfoId call() throws Exception {
      Commands.GetFramebufferDepth.Result result = (Commands.GetFramebufferDepth.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class ReplaceAtomCallable implements Callable<CaptureId> {
    private final Commands.ReplaceAtom.Call myCall;

    private ReplaceAtomCallable(CaptureId capture, long atomId, short atomType, Binary data) {
      myCall = new Commands.ReplaceAtom.Call(capture, atomId, atomType, data);
    }

    @Override
    public CaptureId call() throws Exception {
      Commands.ReplaceAtom.Result result = (Commands.ReplaceAtom.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class GetTimingInfoCallable implements Callable<TimingInfoId> {
    private final Commands.GetTimingInfo.Call myCall;

    private GetTimingInfoCallable(DeviceId device, CaptureId capture, int contextId, TimingMask mask) {
      myCall = new Commands.GetTimingInfo.Call(device, capture, contextId, mask);
    }

    @Override
    public TimingInfoId call() throws Exception {
      Commands.GetTimingInfo.Result result = (Commands.GetTimingInfo.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class PrerenderFramebuffersCallable implements Callable<BinaryId> {
    private final Commands.PrerenderFramebuffers.Call myCall;

    private PrerenderFramebuffersCallable(DeviceId device, CaptureId capture, int width, int height, long[] atomIds) {
      myCall = new Commands.PrerenderFramebuffers.Call(device, capture, width, height, atomIds);
    }

    @Override
    public BinaryId call() throws Exception {
      Commands.PrerenderFramebuffers.Result result = (Commands.PrerenderFramebuffers.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class ResolveAtomStreamCallable implements Callable<AtomStream> {
    private final Commands.ResolveAtomStream.Call myCall;

    private ResolveAtomStreamCallable(AtomStreamId id) {
      myCall = new Commands.ResolveAtomStream.Call(id);
    }

    @Override
    public AtomStream call() throws Exception {
      Commands.ResolveAtomStream.Result result = (Commands.ResolveAtomStream.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class ResolveBinaryCallable implements Callable<Binary> {
    private final Commands.ResolveBinary.Call myCall;

    private ResolveBinaryCallable(BinaryId id) {
      myCall = new Commands.ResolveBinary.Call(id);
    }

    @Override
    public Binary call() throws Exception {
      Commands.ResolveBinary.Result result = (Commands.ResolveBinary.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class ResolveCaptureCallable implements Callable<Capture> {
    private final Commands.ResolveCapture.Call myCall;

    private ResolveCaptureCallable(CaptureId id) {
      myCall = new Commands.ResolveCapture.Call(id);
    }

    @Override
    public Capture call() throws Exception {
      Commands.ResolveCapture.Result result = (Commands.ResolveCapture.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class ResolveDeviceCallable implements Callable<Device> {
    private final Commands.ResolveDevice.Call myCall;

    private ResolveDeviceCallable(DeviceId id) {
      myCall = new Commands.ResolveDevice.Call(id);
    }

    @Override
    public Device call() throws Exception {
      Commands.ResolveDevice.Result result = (Commands.ResolveDevice.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class ResolveHierarchyCallable implements Callable<Hierarchy> {
    private final Commands.ResolveHierarchy.Call myCall;

    private ResolveHierarchyCallable(HierarchyId id) {
      myCall = new Commands.ResolveHierarchy.Call(id);
    }

    @Override
    public Hierarchy call() throws Exception {
      Commands.ResolveHierarchy.Result result = (Commands.ResolveHierarchy.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class ResolveImageInfoCallable implements Callable<ImageInfo> {
    private final Commands.ResolveImageInfo.Call myCall;

    private ResolveImageInfoCallable(ImageInfoId id) {
      myCall = new Commands.ResolveImageInfo.Call(id);
    }

    @Override
    public ImageInfo call() throws Exception {
      Commands.ResolveImageInfo.Result result = (Commands.ResolveImageInfo.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class ResolveMemoryInfoCallable implements Callable<MemoryInfo> {
    private final Commands.ResolveMemoryInfo.Call myCall;

    private ResolveMemoryInfoCallable(MemoryInfoId id) {
      myCall = new Commands.ResolveMemoryInfo.Call(id);
    }

    @Override
    public MemoryInfo call() throws Exception {
      Commands.ResolveMemoryInfo.Result result = (Commands.ResolveMemoryInfo.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class ResolveSchemaCallable implements Callable<Schema> {
    private final Commands.ResolveSchema.Call myCall;

    private ResolveSchemaCallable(SchemaId id) {
      myCall = new Commands.ResolveSchema.Call(id);
    }

    @Override
    public Schema call() throws Exception {
      Commands.ResolveSchema.Result result = (Commands.ResolveSchema.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
  private class ResolveTimingInfoCallable implements Callable<TimingInfo> {
    private final Commands.ResolveTimingInfo.Call myCall;

    private ResolveTimingInfoCallable(TimingInfoId id) {
      myCall = new Commands.ResolveTimingInfo.Call(id);
    }

    @Override
    public TimingInfo call() throws Exception {
      Commands.ResolveTimingInfo.Result result = (Commands.ResolveTimingInfo.Result)myBroadcaster.Send(myCall);
      return result.myValue;
    }
  }
}