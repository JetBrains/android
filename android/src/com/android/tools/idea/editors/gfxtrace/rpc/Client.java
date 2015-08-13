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

import com.android.tools.rpclib.rpccore.RpcException;

import java.io.IOException;
import java.util.concurrent.Future;

public interface Client {
  Future<CaptureId[]> GetCaptures() throws IOException, RpcException;
  Future<DeviceId[]> GetDevices() throws IOException, RpcException;
  Future<BinaryId> GetState(CaptureId capture, int contextId, long after) throws IOException, RpcException;
  Future<HierarchyId> GetHierarchy(CaptureId capture, int contextId) throws IOException, RpcException;
  Future<MemoryInfoId> GetMemoryInfo(CaptureId capture, int contextId, long after, MemoryRange rng) throws IOException, RpcException;
  Future<ImageInfoId> GetFramebufferColor(DeviceId device, CaptureId capture, int contextId, long after, RenderSettings settings) throws IOException, RpcException;
  Future<ImageInfoId> GetFramebufferDepth(DeviceId device, CaptureId capture, int contextId, long after) throws IOException, RpcException;
  Future<CaptureId> ReplaceAtom(CaptureId capture, long atomId, short atomType, Binary data) throws IOException, RpcException;
  Future<TimingInfoId> GetTimingInfo(DeviceId device, CaptureId capture, int contextId, TimingMask mask) throws IOException, RpcException;
  Future<BinaryId> PrerenderFramebuffers(DeviceId device, CaptureId capture, int width, int height, long[] atomIds) throws IOException, RpcException;
  Future<AtomStream> ResolveAtomStream(AtomStreamId id) throws IOException, RpcException;
  Future<Binary> ResolveBinary(BinaryId id) throws IOException, RpcException;
  Future<Capture> ResolveCapture(CaptureId id) throws IOException, RpcException;
  Future<Device> ResolveDevice(DeviceId id) throws IOException, RpcException;
  Future<Hierarchy> ResolveHierarchy(HierarchyId id) throws IOException, RpcException;
  Future<ImageInfo> ResolveImageInfo(ImageInfoId id) throws IOException, RpcException;
  Future<MemoryInfo> ResolveMemoryInfo(MemoryInfoId id) throws IOException, RpcException;
  Future<Schema> ResolveSchema(SchemaId id) throws IOException, RpcException;
  Future<TimingInfo> ResolveTimingInfo(TimingInfoId id) throws IOException, RpcException;
}