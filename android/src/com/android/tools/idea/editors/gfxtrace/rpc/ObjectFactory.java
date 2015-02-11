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

import com.android.tools.rpclib.binary.*;

import java.io.IOException;

class ObjectFactory {
  public static byte[] ApiInfoIDBytes = {44, 73, 4, 40, -36, -81, -60, -81, 104, 41, 21, -13, -46, 78, 38, 100, -8, -114, -57, 48,};
  public static ObjectTypeID ApiInfoID = new ObjectTypeID(ApiInfoIDBytes);
  public static byte[] ArrayInfoIDBytes = {25, -62, -40, -125, 116, 99, 29, -65, 75, 27, 41, -51, -53, -7, -40, 5, 56, -99, -79, 13,};
  public static ObjectTypeID ArrayInfoID = new ObjectTypeID(ArrayInfoIDBytes);
  public static byte[] AtomGroupIDBytes = {-106, 33, 57, -96, 95, -50, 16, 105, 91, 51, -72, 6, -70, -57, 79, 73, -78, -86, 45, -110,};
  public static ObjectTypeID AtomGroupID = new ObjectTypeID(AtomGroupIDBytes);
  public static byte[] AtomInfoIDBytes = {54, -116, -23, -55, 86, -32, 20, 40, 47, -125, -118, -69, 19, 0, -49, -18, -44, 7, 40, -34,};
  public static ObjectTypeID AtomInfoID = new ObjectTypeID(AtomInfoIDBytes);
  public static byte[] AtomRangeIDBytes =
    {-122, 33, 32, 23, 86, -44, -114, -25, -118, -94, -98, 80, 41, -118, -110, 71, 51, 50, -108, 111,};
  public static ObjectTypeID AtomRangeID = new ObjectTypeID(AtomRangeIDBytes);
  public static byte[] AtomRangeTimerIDBytes = {117, 112, 60, 118, -82, -105, -27, 37, 41, -120, 67, 73, 72, 89, -23, 6, 44, -81, -69, 99,};
  public static ObjectTypeID AtomRangeTimerID = new ObjectTypeID(AtomRangeTimerIDBytes);
  public static byte[] AtomStreamIDBytes = {47, 70, -125, -24, -66, 40, 53, 40, 127, 11, -22, 13, 26, 68, -85, 59, 41, 39, 78, -11,};
  public static ObjectTypeID AtomStreamID = new ObjectTypeID(AtomStreamIDBytes);
  public static byte[] AtomTimerIDBytes = {-6, -29, 41, -84, 42, 26, 42, 43, 116, 23, -88, -48, -8, 49, -41, -74, -54, 27, 37, -110,};
  public static ObjectTypeID AtomTimerID = new ObjectTypeID(AtomTimerIDBytes);
  public static byte[] BinaryIDBytes = {-89, 90, -127, 10, 4, -79, -70, 51, -121, -8, -29, -77, 78, -15, -79, -128, 70, 31, 16, 25,};
  public static ObjectTypeID BinaryID = new ObjectTypeID(BinaryIDBytes);
  public static byte[] CaptureIDBytes = {-28, 59, 19, 56, 112, 46, -125, -118, 35, 62, 43, -95, 85, 25, -17, 13, 102, -29, -10, 102,};
  public static ObjectTypeID CaptureID = new ObjectTypeID(CaptureIDBytes);
  public static byte[] ClassInfoIDBytes = {6, -23, -55, 67, 109, -118, 49, 1, 19, -78, 93, 122, 5, 54, 63, 55, -115, 62, -7, -126,};
  public static ObjectTypeID ClassInfoID = new ObjectTypeID(ClassInfoIDBytes);
  public static byte[] DeviceIDBytes = {74, 77, 78, 75, 5, -67, -112, -90, -25, 10, 91, -99, 66, -29, -15, -86, -2, -74, 126, -52,};
  public static ObjectTypeID DeviceID = new ObjectTypeID(DeviceIDBytes);
  public static byte[] EnumEntryIDBytes = {65, -13, 19, -56, 36, -5, -30, 124, 110, 42, -45, -115, -82, 59, 98, -20, -93, -88, -119, -116,};
  public static ObjectTypeID EnumEntryID = new ObjectTypeID(EnumEntryIDBytes);
  public static byte[] EnumInfoIDBytes = {91, 113, 56, -123, 7, -23, -51, 92, -106, 46, -19, -81, 86, -31, -37, 47, -51, -22, 23, -9,};
  public static ObjectTypeID EnumInfoID = new ObjectTypeID(EnumInfoIDBytes);
  public static byte[] FieldInfoIDBytes = {87, -105, 72, 89, -39, 7, 75, -113, 61, 48, 37, 118, 121, 46, -47, 66, 53, -15, -33, 100,};
  public static ObjectTypeID FieldInfoID = new ObjectTypeID(FieldInfoIDBytes);
  public static byte[] HierarchyIDBytes =
    {-29, 109, -109, -96, 41, -100, 58, -50, 55, -1, 81, -110, 74, 118, -72, -126, -52, 77, 58, -108,};
  public static ObjectTypeID HierarchyID = new ObjectTypeID(HierarchyIDBytes);
  public static byte[] ImageInfoIDBytes = {82, -96, -100, -1, 109, 66, -83, -53, -94, -25, 126, 79, 93, -103, -1, -98, 29, -89, 37, 37,};
  public static ObjectTypeID ImageInfoID = new ObjectTypeID(ImageInfoIDBytes);
  public static byte[] MapInfoIDBytes = {10, 34, 51, -20, -96, 94, -45, 113, 109, 81, 28, -106, -72, 76, 34, -113, 22, 7, -85, -24,};
  public static ObjectTypeID MapInfoID = new ObjectTypeID(MapInfoIDBytes);
  public static byte[] MemoryInfoIDBytes =
    {121, -97, -33, -122, 63, -30, 49, 124, 119, 31, -79, -122, -102, 50, 4, 105, -89, -118, 35, -62,};
  public static ObjectTypeID MemoryInfoID = new ObjectTypeID(MemoryInfoIDBytes);
  public static byte[] MemoryRangeIDBytes = {9, -38, -43, -122, -123, 114, -103, 52, 10, -112, 52, -21, -55, -1, 96, 44, -85, 26, 61, 36,};
  public static ObjectTypeID MemoryRangeID = new ObjectTypeID(MemoryRangeIDBytes);
  public static byte[] ParameterInfoIDBytes =
    {37, -106, -57, 112, -119, -50, -82, 60, 61, -115, 11, -36, -68, -101, 42, -83, -115, 32, -79, -125,};
  public static ObjectTypeID ParameterInfoID = new ObjectTypeID(ParameterInfoIDBytes);
  public static byte[] RenderSettingsIDBytes = {-69, 124, -97, -29, 51, -40, -31, 99, -75, 77, -66, -5, 79, 57, 13, -47, 0, 48, 32, 100,};
  public static ObjectTypeID RenderSettingsID = new ObjectTypeID(RenderSettingsIDBytes);
  public static byte[] SchemaIDBytes = {-63, 99, -115, 69, 47, -5, 48, -94, -57, -70, 102, -27, -73, -14, -5, 89, 43, 117, -69, 6,};
  public static ObjectTypeID SchemaID = new ObjectTypeID(SchemaIDBytes);
  public static byte[] SimpleInfoIDBytes = {-94, 83, -42, 115, 33, 62, -93, -78, 17, 66, 87, 53, 118, -71, -93, 57, 3, -43, 14, 65,};
  public static ObjectTypeID SimpleInfoID = new ObjectTypeID(SimpleInfoIDBytes);
  public static byte[] StructInfoIDBytes = {-99, -36, 12, 102, 25, -97, -37, -12, -38, -110, 62, 61, 125, -46, -25, -55, -127, -35, 63, 6,};
  public static ObjectTypeID StructInfoID = new ObjectTypeID(StructInfoIDBytes);
  public static byte[] TimingInfoIDBytes = {-78, 122, -72, -87, -112, -30, -89, 88, 17, 33, 70, 119, 26, 38, -22, -21, 82, 64, 82, -126,};
  public static ObjectTypeID TimingInfoID = new ObjectTypeID(TimingInfoIDBytes);
  public static byte[] callGetCapturesIDBytes =
    {73, 100, 91, 95, 112, -117, -52, 37, -29, 112, -79, 66, 96, 53, 28, -51, -73, 28, -10, 26,};
  public static ObjectTypeID callGetCapturesID = new ObjectTypeID(callGetCapturesIDBytes);
  public static byte[] callGetDevicesIDBytes =
    {-91, -99, -110, 112, 56, 122, 3, 74, -90, 111, 60, 29, -119, -29, -127, 37, -98, -122, 41, -92,};
  public static ObjectTypeID callGetDevicesID = new ObjectTypeID(callGetDevicesIDBytes);
  public static byte[] callGetFramebufferColorIDBytes =
    {34, -100, 5, 72, -55, -97, -86, 44, 59, 123, 110, 106, -87, 110, 32, -43, 108, -35, 54, -64,};
  public static ObjectTypeID callGetFramebufferColorID = new ObjectTypeID(callGetFramebufferColorIDBytes);
  public static byte[] callGetFramebufferDepthIDBytes =
    {-91, 91, 116, -64, 0, -80, 20, -40, -127, 19, 115, -113, 68, 31, 38, 14, 3, -81, 108, 26,};
  public static ObjectTypeID callGetFramebufferDepthID = new ObjectTypeID(callGetFramebufferDepthIDBytes);
  public static byte[] callGetGlErrorCodesIDBytes =
    {-6, -34, 35, -122, 12, 52, -89, 73, 18, -17, 62, 105, 23, 43, 51, -51, -81, 68, 117, 120,};
  public static ObjectTypeID callGetGlErrorCodesID = new ObjectTypeID(callGetGlErrorCodesIDBytes);
  public static byte[] callGetHierarchyIDBytes =
    {-31, 26, 48, -60, -88, -75, 87, 118, -118, -50, -23, 99, -109, -94, 20, 92, 98, -117, -106, 31,};
  public static ObjectTypeID callGetHierarchyID = new ObjectTypeID(callGetHierarchyIDBytes);
  public static byte[] callGetMemoryInfoIDBytes =
    {63, 52, -48, -113, -112, -108, 37, -4, 110, 8, 19, -72, -9, 8, -115, 21, -124, -23, -52, -86,};
  public static ObjectTypeID callGetMemoryInfoID = new ObjectTypeID(callGetMemoryInfoIDBytes);
  public static byte[] callGetStateIDBytes = {-41, 35, -108, 89, 55, 15, -2, -71, -26, -98, 47, -68, 86, 45, 100, 118, 88, -100, -52, -9,};
  public static ObjectTypeID callGetStateID = new ObjectTypeID(callGetStateIDBytes);
  public static byte[] callGetTimingInfoIDBytes =
    {59, -49, -47, 20, 57, 82, -121, 69, -73, -118, -66, 9, 112, 2, 125, -102, 98, -54, -25, -80,};
  public static ObjectTypeID callGetTimingInfoID = new ObjectTypeID(callGetTimingInfoIDBytes);
  public static byte[] callReplaceAtomIDBytes = {74, 44, 34, 58, 84, 79, -64, 75, -76, -5, -21, 84, 107, 77, -102, 109, 0, -94, 14, 43,};
  public static ObjectTypeID callReplaceAtomID = new ObjectTypeID(callReplaceAtomIDBytes);
  public static byte[] callResolveAtomStreamIDBytes =
    {-20, 19, -14, -2, -119, 51, 110, -121, -27, 18, -3, -51, 4, 96, -19, 81, 81, 28, -48, -4,};
  public static ObjectTypeID callResolveAtomStreamID = new ObjectTypeID(callResolveAtomStreamIDBytes);
  public static byte[] callResolveBinaryIDBytes =
    {-1, -46, -89, 105, -12, 78, 63, -31, 50, -77, 59, 5, -91, 126, -48, 18, -13, -83, 80, -60,};
  public static ObjectTypeID callResolveBinaryID = new ObjectTypeID(callResolveBinaryIDBytes);
  public static byte[] callResolveCaptureIDBytes =
    {70, -25, 126, 117, 49, 80, 70, -85, -94, -8, 40, 78, -37, 32, -110, 95, 3, -40, 59, 107,};
  public static ObjectTypeID callResolveCaptureID = new ObjectTypeID(callResolveCaptureIDBytes);
  public static byte[] callResolveDeviceIDBytes =
    {-113, -37, -82, 34, 113, 82, 95, 76, 39, 16, 5, 53, -116, -76, -96, -96, -45, 35, -19, 46,};
  public static ObjectTypeID callResolveDeviceID = new ObjectTypeID(callResolveDeviceIDBytes);
  public static byte[] callResolveHierarchyIDBytes =
    {-84, 41, 95, 70, -54, -125, 106, -42, -69, 45, -121, 56, -93, 42, 114, -99, -18, -98, -20, -18,};
  public static ObjectTypeID callResolveHierarchyID = new ObjectTypeID(callResolveHierarchyIDBytes);
  public static byte[] callResolveImageInfoIDBytes =
    {79, 21, 69, 58, 30, -128, 71, -44, -3, 63, -126, -18, -90, -117, 124, -98, 65, 94, -33, 73,};
  public static ObjectTypeID callResolveImageInfoID = new ObjectTypeID(callResolveImageInfoIDBytes);
  public static byte[] callResolveMemoryInfoIDBytes =
    {-20, -118, 109, -35, 51, -64, -55, 96, 117, -34, -4, -42, -35, -83, 66, -76, -11, -62, 114, -25,};
  public static ObjectTypeID callResolveMemoryInfoID = new ObjectTypeID(callResolveMemoryInfoIDBytes);
  public static byte[] callResolveSchemaIDBytes =
    {26, 80, 79, -56, -112, -127, -73, 95, -62, 90, 112, -68, 111, -36, 24, 126, -98, -122, -20, -54,};
  public static ObjectTypeID callResolveSchemaID = new ObjectTypeID(callResolveSchemaIDBytes);
  public static byte[] callResolveTimingInfoIDBytes =
    {-32, -89, -118, -20, 83, 32, 37, 70, 98, 71, -122, -57, -20, 41, -88, -125, 77, 67, -19, 30,};
  public static ObjectTypeID callResolveTimingInfoID = new ObjectTypeID(callResolveTimingInfoIDBytes);
  public static byte[] resultGetCapturesIDBytes =
    {25, -107, 35, 97, 65, -41, 86, 48, 42, 95, -76, -5, 39, -6, 127, 92, -123, -128, -92, -105,};
  public static ObjectTypeID resultGetCapturesID = new ObjectTypeID(resultGetCapturesIDBytes);
  public static byte[] resultGetDevicesIDBytes =
    {-119, 75, -126, -29, -54, 97, 51, 44, -111, 126, 3, 7, -67, -118, 13, -18, -23, -26, -124, -73,};
  public static ObjectTypeID resultGetDevicesID = new ObjectTypeID(resultGetDevicesIDBytes);
  public static byte[] resultGetFramebufferColorIDBytes =
    {-48, 121, -7, -64, -94, -64, -69, 117, -26, -53, 89, 92, 63, 95, 23, 123, 58, 59, -85, 39,};
  public static ObjectTypeID resultGetFramebufferColorID = new ObjectTypeID(resultGetFramebufferColorIDBytes);
  public static byte[] resultGetFramebufferDepthIDBytes =
    {104, -77, -68, 102, -47, -107, -49, 65, -73, 79, 51, -125, -56, 63, 5, 82, -74, 112, 33, 74,};
  public static ObjectTypeID resultGetFramebufferDepthID = new ObjectTypeID(resultGetFramebufferDepthIDBytes);
  public static byte[] resultGetGlErrorCodesIDBytes =
    {-122, 115, 113, 66, 46, -73, 6, -91, 101, -68, 79, 3, 38, 70, 84, -95, 117, -75, 93, -100,};
  public static ObjectTypeID resultGetGlErrorCodesID = new ObjectTypeID(resultGetGlErrorCodesIDBytes);
  public static byte[] resultGetHierarchyIDBytes =
    {112, -126, 2, 100, -88, -86, -73, 46, 50, 96, 20, -81, 110, -73, -17, -69, -98, -77, -11, -55,};
  public static ObjectTypeID resultGetHierarchyID = new ObjectTypeID(resultGetHierarchyIDBytes);
  public static byte[] resultGetMemoryInfoIDBytes =
    {-105, 24, 20, 55, 25, -108, 127, 47, 33, -124, -7, 35, 53, 93, 93, 125, 105, 41, -63, -108,};
  public static ObjectTypeID resultGetMemoryInfoID = new ObjectTypeID(resultGetMemoryInfoIDBytes);
  public static byte[] resultGetStateIDBytes = {-17, 105, 83, 38, -74, 39, 71, 61, 109, 67, -53, 12, 74, -18, -6, -70, -41, -80, -64, -48,};
  public static ObjectTypeID resultGetStateID = new ObjectTypeID(resultGetStateIDBytes);
  public static byte[] resultGetTimingInfoIDBytes =
    {40, -106, 125, -71, 3, 54, 9, 83, 53, 116, 52, -84, -21, 10, 78, 125, -126, -115, -11, -61,};
  public static ObjectTypeID resultGetTimingInfoID = new ObjectTypeID(resultGetTimingInfoIDBytes);
  public static byte[] resultReplaceAtomIDBytes =
    {-108, 56, 78, -127, 77, -61, -95, -16, 13, -28, 8, 24, 122, -50, -22, -55, -73, 101, 22, 8,};
  public static ObjectTypeID resultReplaceAtomID = new ObjectTypeID(resultReplaceAtomIDBytes);
  public static byte[] resultResolveAtomStreamIDBytes =
    {112, 33, -91, -106, -125, 89, 59, -80, 85, 23, -100, 16, -113, 39, -123, 96, 63, 12, 112, -126,};
  public static ObjectTypeID resultResolveAtomStreamID = new ObjectTypeID(resultResolveAtomStreamIDBytes);
  public static byte[] resultResolveBinaryIDBytes =
    {68, 37, 14, 83, -69, 114, 15, -21, 7, -102, -38, 117, -27, 72, -34, 73, 85, -32, 6, -72,};
  public static ObjectTypeID resultResolveBinaryID = new ObjectTypeID(resultResolveBinaryIDBytes);
  public static byte[] resultResolveCaptureIDBytes =
    {-95, -120, -81, 10, -97, -124, -118, -57, -2, -60, -105, 63, -50, -30, -8, -79, 13, 24, -76, 71,};
  public static ObjectTypeID resultResolveCaptureID = new ObjectTypeID(resultResolveCaptureIDBytes);
  public static byte[] resultResolveDeviceIDBytes =
    {-113, -41, 64, 95, 54, -66, -117, 59, -9, -103, 46, 31, -94, -3, 49, -72, -64, -115, 4, 118,};
  public static ObjectTypeID resultResolveDeviceID = new ObjectTypeID(resultResolveDeviceIDBytes);
  public static byte[] resultResolveHierarchyIDBytes =
    {121, -113, -68, -24, -26, -19, 20, -81, 25, -21, 111, 58, 16, -66, -32, 107, -82, -113, 68, -8,};
  public static ObjectTypeID resultResolveHierarchyID = new ObjectTypeID(resultResolveHierarchyIDBytes);
  public static byte[] resultResolveImageInfoIDBytes =
    {19, -21, -121, -121, 44, 53, 3, -95, -54, -112, 90, 40, 119, -26, 54, -63, 31, -50, -125, -67,};
  public static ObjectTypeID resultResolveImageInfoID = new ObjectTypeID(resultResolveImageInfoIDBytes);
  public static byte[] resultResolveMemoryInfoIDBytes =
    {88, 26, -112, 117, 5, 114, -83, -17, -72, -74, 44, -37, 2, -60, -8, -20, -67, -29, 70, -10,};
  public static ObjectTypeID resultResolveMemoryInfoID = new ObjectTypeID(resultResolveMemoryInfoIDBytes);
  public static byte[] resultResolveSchemaIDBytes =
    {-74, 4, 43, 5, -84, 90, 103, -69, -111, 84, 29, -89, -36, -1, -97, 112, 33, 35, 114, 73,};
  public static ObjectTypeID resultResolveSchemaID = new ObjectTypeID(resultResolveSchemaIDBytes);
  public static byte[] resultResolveTimingInfoIDBytes =
    {19, -56, 21, -53, 123, -22, 108, 61, -114, -27, 44, 102, -97, -92, 46, 79, 57, -28, -95, -59,};
  public static ObjectTypeID resultResolveTimingInfoID = new ObjectTypeID(resultResolveTimingInfoIDBytes);
  static {
    ObjectTypeID.register(ApiInfoID, Entries.ApiInfoEnum);
    ObjectTypeID.register(ArrayInfoID, Entries.ArrayInfoEnum);
    ObjectTypeID.register(AtomGroupID, Entries.AtomGroupEnum);
    ObjectTypeID.register(AtomInfoID, Entries.AtomInfoEnum);
    ObjectTypeID.register(AtomRangeID, Entries.AtomRangeEnum);
    ObjectTypeID.register(AtomRangeTimerID, Entries.AtomRangeTimerEnum);
    ObjectTypeID.register(AtomStreamID, Entries.AtomStreamEnum);
    ObjectTypeID.register(AtomTimerID, Entries.AtomTimerEnum);
    ObjectTypeID.register(BinaryID, Entries.BinaryEnum);
    ObjectTypeID.register(CaptureID, Entries.CaptureEnum);
    ObjectTypeID.register(ClassInfoID, Entries.ClassInfoEnum);
    ObjectTypeID.register(DeviceID, Entries.DeviceEnum);
    ObjectTypeID.register(EnumEntryID, Entries.EnumEntryEnum);
    ObjectTypeID.register(EnumInfoID, Entries.EnumInfoEnum);
    ObjectTypeID.register(FieldInfoID, Entries.FieldInfoEnum);
    ObjectTypeID.register(HierarchyID, Entries.HierarchyEnum);
    ObjectTypeID.register(ImageInfoID, Entries.ImageInfoEnum);
    ObjectTypeID.register(MapInfoID, Entries.MapInfoEnum);
    ObjectTypeID.register(MemoryInfoID, Entries.MemoryInfoEnum);
    ObjectTypeID.register(MemoryRangeID, Entries.MemoryRangeEnum);
    ObjectTypeID.register(ParameterInfoID, Entries.ParameterInfoEnum);
    ObjectTypeID.register(RenderSettingsID, Entries.RenderSettingsEnum);
    ObjectTypeID.register(SchemaID, Entries.SchemaEnum);
    ObjectTypeID.register(SimpleInfoID, Entries.SimpleInfoEnum);
    ObjectTypeID.register(StructInfoID, Entries.StructInfoEnum);
    ObjectTypeID.register(TimingInfoID, Entries.TimingInfoEnum);
    ObjectTypeID.register(callGetCapturesID, Entries.callGetCapturesEnum);
    ObjectTypeID.register(callGetDevicesID, Entries.callGetDevicesEnum);
    ObjectTypeID.register(callGetFramebufferColorID, Entries.callGetFramebufferColorEnum);
    ObjectTypeID.register(callGetFramebufferDepthID, Entries.callGetFramebufferDepthEnum);
    ObjectTypeID.register(callGetGlErrorCodesID, Entries.callGetGlErrorCodesEnum);
    ObjectTypeID.register(callGetHierarchyID, Entries.callGetHierarchyEnum);
    ObjectTypeID.register(callGetMemoryInfoID, Entries.callGetMemoryInfoEnum);
    ObjectTypeID.register(callGetStateID, Entries.callGetStateEnum);
    ObjectTypeID.register(callGetTimingInfoID, Entries.callGetTimingInfoEnum);
    ObjectTypeID.register(callReplaceAtomID, Entries.callReplaceAtomEnum);
    ObjectTypeID.register(callResolveAtomStreamID, Entries.callResolveAtomStreamEnum);
    ObjectTypeID.register(callResolveBinaryID, Entries.callResolveBinaryEnum);
    ObjectTypeID.register(callResolveCaptureID, Entries.callResolveCaptureEnum);
    ObjectTypeID.register(callResolveDeviceID, Entries.callResolveDeviceEnum);
    ObjectTypeID.register(callResolveHierarchyID, Entries.callResolveHierarchyEnum);
    ObjectTypeID.register(callResolveImageInfoID, Entries.callResolveImageInfoEnum);
    ObjectTypeID.register(callResolveMemoryInfoID, Entries.callResolveMemoryInfoEnum);
    ObjectTypeID.register(callResolveSchemaID, Entries.callResolveSchemaEnum);
    ObjectTypeID.register(callResolveTimingInfoID, Entries.callResolveTimingInfoEnum);
    ObjectTypeID.register(resultGetCapturesID, Entries.resultGetCapturesEnum);
    ObjectTypeID.register(resultGetDevicesID, Entries.resultGetDevicesEnum);
    ObjectTypeID.register(resultGetFramebufferColorID, Entries.resultGetFramebufferColorEnum);
    ObjectTypeID.register(resultGetFramebufferDepthID, Entries.resultGetFramebufferDepthEnum);
    ObjectTypeID.register(resultGetGlErrorCodesID, Entries.resultGetGlErrorCodesEnum);
    ObjectTypeID.register(resultGetHierarchyID, Entries.resultGetHierarchyEnum);
    ObjectTypeID.register(resultGetMemoryInfoID, Entries.resultGetMemoryInfoEnum);
    ObjectTypeID.register(resultGetStateID, Entries.resultGetStateEnum);
    ObjectTypeID.register(resultGetTimingInfoID, Entries.resultGetTimingInfoEnum);
    ObjectTypeID.register(resultReplaceAtomID, Entries.resultReplaceAtomEnum);
    ObjectTypeID.register(resultResolveAtomStreamID, Entries.resultResolveAtomStreamEnum);
    ObjectTypeID.register(resultResolveBinaryID, Entries.resultResolveBinaryEnum);
    ObjectTypeID.register(resultResolveCaptureID, Entries.resultResolveCaptureEnum);
    ObjectTypeID.register(resultResolveDeviceID, Entries.resultResolveDeviceEnum);
    ObjectTypeID.register(resultResolveHierarchyID, Entries.resultResolveHierarchyEnum);
    ObjectTypeID.register(resultResolveImageInfoID, Entries.resultResolveImageInfoEnum);
    ObjectTypeID.register(resultResolveMemoryInfoID, Entries.resultResolveMemoryInfoEnum);
    ObjectTypeID.register(resultResolveSchemaID, Entries.resultResolveSchemaEnum);
    ObjectTypeID.register(resultResolveTimingInfoID, Entries.resultResolveTimingInfoEnum);
  }

  public static void encode(Encoder e, ApiInfo o) throws IOException {
    e.string(o.Name);
    o.Schema.encode(e);
  }

  public static void decode(Decoder d, ApiInfo o) throws IOException {
    o.Name = d.string();
    o.Schema = SchemaId.decode(d);
  }

  public static void encode(Encoder e, ArrayInfo o) throws IOException {
    e.string(o.Name);
    o.Kind.encode(e);
    e.object(o.ElementType);
  }

  public static void decode(Decoder d, ArrayInfo o) throws IOException {
    o.Name = d.string();
    o.Kind = TypeKind.decode(d);
    o.ElementType = (TypeInfo)d.object();
  }

  public static void encode(Encoder e, AtomGroup o) throws IOException {
    e.string(o.Name);
    o.Range.encode(e);
    e.int32(o.SubGroups.length);
    for (int i = 0; i < o.SubGroups.length; i++) {
      o.SubGroups[i].encode(e);
    }

  }

  public static void decode(Decoder d, AtomGroup o) throws IOException {
    o.Name = d.string();
    o.Range = new AtomRange(d);
    o.SubGroups = new AtomGroup[d.int32()];
    for (int i = 0; i < o.SubGroups.length; i++) {
      o.SubGroups[i] = new AtomGroup(d);
    }

  }

  public static void encode(Encoder e, AtomInfo o) throws IOException {
    e.uint16(o.Type);
    e.string(o.Name);
    e.int32(o.Parameters.length);
    for (int i = 0; i < o.Parameters.length; i++) {
      o.Parameters[i].encode(e);
    }

    e.bool(o.IsCommand);
    e.bool(o.IsDrawCall);
    e.bool(o.IsEndOfFrame);
    e.string(o.DocumentationUrl);
  }

  public static void decode(Decoder d, AtomInfo o) throws IOException {
    o.Type = d.uint16();
    o.Name = d.string();
    o.Parameters = new ParameterInfo[d.int32()];
    for (int i = 0; i < o.Parameters.length; i++) {
      o.Parameters[i] = new ParameterInfo(d);
    }

    o.IsCommand = d.bool();
    o.IsDrawCall = d.bool();
    o.IsEndOfFrame = d.bool();
    o.DocumentationUrl = d.string();
  }

  public static void encode(Encoder e, AtomRange o) throws IOException {
    e.uint64(o.First);
    e.uint64(o.Count);
  }

  public static void decode(Decoder d, AtomRange o) throws IOException {
    o.First = d.uint64();
    o.Count = d.uint64();
  }

  public static void encode(Encoder e, AtomRangeTimer o) throws IOException {
    e.uint64(o.FromAtomId);
    e.uint64(o.ToAtomId);
    e.uint64(o.Nanoseconds);
  }

  public static void decode(Decoder d, AtomRangeTimer o) throws IOException {
    o.FromAtomId = d.uint64();
    o.ToAtomId = d.uint64();
    o.Nanoseconds = d.uint64();
  }

  public static void encode(Encoder e, AtomStream o) throws IOException {
    e.int32(o.Data.length);
    for (int i = 0; i < o.Data.length; i++) {
      e.uint8(o.Data[i]);
    }

    o.Schema.encode(e);
  }

  public static void decode(Decoder d, AtomStream o) throws IOException {
    o.Data = new short[d.int32()];
    for (int i = 0; i < o.Data.length; i++) {
      o.Data[i] = d.uint8();
    }

    o.Schema = SchemaId.decode(d);
  }

  public static void encode(Encoder e, AtomTimer o) throws IOException {
    e.uint64(o.AtomId);
    e.uint64(o.Nanoseconds);
  }

  public static void decode(Decoder d, AtomTimer o) throws IOException {
    o.AtomId = d.uint64();
    o.Nanoseconds = d.uint64();
  }

  public static void encode(Encoder e, Binary o) throws IOException {
    e.int32(o.Data.length);
    for (int i = 0; i < o.Data.length; i++) {
      e.uint8(o.Data[i]);
    }

  }

  public static void decode(Decoder d, Binary o) throws IOException {
    o.Data = new short[d.int32()];
    for (int i = 0; i < o.Data.length; i++) {
      o.Data[i] = d.uint8();
    }

  }

  public static void encode(Encoder e, Capture o) throws IOException {
    e.string(o.Name);
    e.string(o.API);
    o.Atoms.encode(e);
    e.int32(o.ContextIds.length);
    for (int i = 0; i < o.ContextIds.length; i++) {
      e.uint32(o.ContextIds[i]);
    }

  }

  public static void decode(Decoder d, Capture o) throws IOException {
    o.Name = d.string();
    o.API = d.string();
    o.Atoms = AtomStreamId.decode(d);
    o.ContextIds = new long[d.int32()];
    for (int i = 0; i < o.ContextIds.length; i++) {
      o.ContextIds[i] = d.uint32();
    }

  }

  public static void encode(Encoder e, ClassInfo o) throws IOException {
    e.string(o.Name);
    o.Kind.encode(e);
    e.int32(o.Fields.length);
    for (int i = 0; i < o.Fields.length; i++) {
      e.object(o.Fields[i]);
    }

    e.int32(o.Extends.length);
    for (int i = 0; i < o.Extends.length; i++) {
      e.object(o.Extends[i]);
    }

  }

  public static void decode(Decoder d, ClassInfo o) throws IOException {
    o.Name = d.string();
    o.Kind = TypeKind.decode(d);
    o.Fields = new FieldInfo[d.int32()];
    for (int i = 0; i < o.Fields.length; i++) {
      o.Fields[i] = (FieldInfo)d.object();
    }

    o.Extends = new ClassInfo[d.int32()];
    for (int i = 0; i < o.Extends.length; i++) {
      o.Extends[i] = (ClassInfo)d.object();
    }

  }

  public static void encode(Encoder e, Device o) throws IOException {
    e.string(o.Name);
    e.string(o.Model);
    e.string(o.OS);
    e.uint8(o.PointerSize);
    e.uint8(o.PointerAlignment);
    e.uint64(o.MaxMemorySize);
    e.bool(o.RequiresShaderPatching);
  }

  public static void decode(Decoder d, Device o) throws IOException {
    o.Name = d.string();
    o.Model = d.string();
    o.OS = d.string();
    o.PointerSize = d.uint8();
    o.PointerAlignment = d.uint8();
    o.MaxMemorySize = d.uint64();
    o.RequiresShaderPatching = d.bool();
  }

  public static void encode(Encoder e, EnumEntry o) throws IOException {
    e.string(o.Name);
    e.uint32(o.Value);
  }

  public static void decode(Decoder d, EnumEntry o) throws IOException {
    o.Name = d.string();
    o.Value = d.uint32();
  }

  public static void encode(Encoder e, EnumInfo o) throws IOException {
    e.string(o.Name);
    o.Kind.encode(e);
    e.int32(o.Entries.length);
    for (int i = 0; i < o.Entries.length; i++) {
      o.Entries[i].encode(e);
    }

    e.int32(o.Extends.length);
    for (int i = 0; i < o.Extends.length; i++) {
      e.object(o.Extends[i]);
    }

  }

  public static void decode(Decoder d, EnumInfo o) throws IOException {
    o.Name = d.string();
    o.Kind = TypeKind.decode(d);
    o.Entries = new EnumEntry[d.int32()];
    for (int i = 0; i < o.Entries.length; i++) {
      o.Entries[i] = new EnumEntry(d);
    }

    o.Extends = new EnumInfo[d.int32()];
    for (int i = 0; i < o.Extends.length; i++) {
      o.Extends[i] = (EnumInfo)d.object();
    }

  }

  public static void encode(Encoder e, FieldInfo o) throws IOException {
    e.string(o.Name);
    e.object(o.Type);
  }

  public static void decode(Decoder d, FieldInfo o) throws IOException {
    o.Name = d.string();
    o.Type = (TypeInfo)d.object();
  }

  public static void encode(Encoder e, Hierarchy o) throws IOException {
    o.Root.encode(e);
  }

  public static void decode(Decoder d, Hierarchy o) throws IOException {
    o.Root = new AtomGroup(d);
  }

  public static void encode(Encoder e, ImageInfo o) throws IOException {
    o.Format.encode(e);
    e.uint32(o.Width);
    e.uint32(o.Height);
    o.Data.encode(e);
  }

  public static void decode(Decoder d, ImageInfo o) throws IOException {
    o.Format = ImageFormat.decode(d);
    o.Width = d.uint32();
    o.Height = d.uint32();
    o.Data = BinaryId.decode(d);
  }

  public static void encode(Encoder e, MapInfo o) throws IOException {
    e.string(o.Name);
    o.Kind.encode(e);
    e.object(o.KeyType);
    e.object(o.ValueType);
  }

  public static void decode(Decoder d, MapInfo o) throws IOException {
    o.Name = d.string();
    o.Kind = TypeKind.decode(d);
    o.KeyType = (TypeInfo)d.object();
    o.ValueType = (TypeInfo)d.object();
  }

  public static void encode(Encoder e, MemoryInfo o) throws IOException {
    e.int32(o.Data.length);
    for (int i = 0; i < o.Data.length; i++) {
      e.uint8(o.Data[i]);
    }

    e.int32(o.Stale.length);
    for (int i = 0; i < o.Stale.length; i++) {
      o.Stale[i].encode(e);
    }

    e.int32(o.Current.length);
    for (int i = 0; i < o.Current.length; i++) {
      o.Current[i].encode(e);
    }

    e.int32(o.Unknown.length);
    for (int i = 0; i < o.Unknown.length; i++) {
      o.Unknown[i].encode(e);
    }

  }

  public static void decode(Decoder d, MemoryInfo o) throws IOException {
    o.Data = new short[d.int32()];
    for (int i = 0; i < o.Data.length; i++) {
      o.Data[i] = d.uint8();
    }

    o.Stale = new MemoryRange[d.int32()];
    for (int i = 0; i < o.Stale.length; i++) {
      o.Stale[i] = new MemoryRange(d);
    }

    o.Current = new MemoryRange[d.int32()];
    for (int i = 0; i < o.Current.length; i++) {
      o.Current[i] = new MemoryRange(d);
    }

    o.Unknown = new MemoryRange[d.int32()];
    for (int i = 0; i < o.Unknown.length; i++) {
      o.Unknown[i] = new MemoryRange(d);
    }

  }

  public static void encode(Encoder e, MemoryRange o) throws IOException {
    e.uint64(o.Base);
    e.uint64(o.Size);
  }

  public static void decode(Decoder d, MemoryRange o) throws IOException {
    o.Base = d.uint64();
    o.Size = d.uint64();
  }

  public static void encode(Encoder e, ParameterInfo o) throws IOException {
    e.string(o.Name);
    e.object(o.Type);
    e.bool(o.Out);
  }

  public static void decode(Decoder d, ParameterInfo o) throws IOException {
    o.Name = d.string();
    o.Type = (TypeInfo)d.object();
    o.Out = d.bool();
  }

  public static void encode(Encoder e, RenderSettings o) throws IOException {
    e.uint32(o.MaxWidth);
    e.uint32(o.MaxHeight);
    e.bool(o.Wireframe);
  }

  public static void decode(Decoder d, RenderSettings o) throws IOException {
    o.MaxWidth = d.uint32();
    o.MaxHeight = d.uint32();
    o.Wireframe = d.bool();
  }

  public static void encode(Encoder e, Schema o) throws IOException {
    e.int32(o.Arrays.length);
    for (int i = 0; i < o.Arrays.length; i++) {
      e.object(o.Arrays[i]);
    }

    e.int32(o.Maps.length);
    for (int i = 0; i < o.Maps.length; i++) {
      e.object(o.Maps[i]);
    }

    e.int32(o.Enums.length);
    for (int i = 0; i < o.Enums.length; i++) {
      e.object(o.Enums[i]);
    }

    e.int32(o.Structs.length);
    for (int i = 0; i < o.Structs.length; i++) {
      e.object(o.Structs[i]);
    }

    e.int32(o.Classes.length);
    for (int i = 0; i < o.Classes.length; i++) {
      e.object(o.Classes[i]);
    }

    e.int32(o.Atoms.length);
    for (int i = 0; i < o.Atoms.length; i++) {
      o.Atoms[i].encode(e);
    }

    e.object(o.State);
  }

  public static void decode(Decoder d, Schema o) throws IOException {
    o.Arrays = new ArrayInfo[d.int32()];
    for (int i = 0; i < o.Arrays.length; i++) {
      o.Arrays[i] = (ArrayInfo)d.object();
    }

    o.Maps = new MapInfo[d.int32()];
    for (int i = 0; i < o.Maps.length; i++) {
      o.Maps[i] = (MapInfo)d.object();
    }

    o.Enums = new EnumInfo[d.int32()];
    for (int i = 0; i < o.Enums.length; i++) {
      o.Enums[i] = (EnumInfo)d.object();
    }

    o.Structs = new StructInfo[d.int32()];
    for (int i = 0; i < o.Structs.length; i++) {
      o.Structs[i] = (StructInfo)d.object();
    }

    o.Classes = new ClassInfo[d.int32()];
    for (int i = 0; i < o.Classes.length; i++) {
      o.Classes[i] = (ClassInfo)d.object();
    }

    o.Atoms = new AtomInfo[d.int32()];
    for (int i = 0; i < o.Atoms.length; i++) {
      o.Atoms[i] = new AtomInfo(d);
    }

    o.State = (StructInfo)d.object();
  }

  public static void encode(Encoder e, SimpleInfo o) throws IOException {
    e.string(o.Name);
    o.Kind.encode(e);
  }

  public static void decode(Decoder d, SimpleInfo o) throws IOException {
    o.Name = d.string();
    o.Kind = TypeKind.decode(d);
  }

  public static void encode(Encoder e, StructInfo o) throws IOException {
    e.string(o.Name);
    o.Kind.encode(e);
    e.int32(o.Fields.length);
    for (int i = 0; i < o.Fields.length; i++) {
      e.object(o.Fields[i]);
    }

  }

  public static void decode(Decoder d, StructInfo o) throws IOException {
    o.Name = d.string();
    o.Kind = TypeKind.decode(d);
    o.Fields = new FieldInfo[d.int32()];
    for (int i = 0; i < o.Fields.length; i++) {
      o.Fields[i] = (FieldInfo)d.object();
    }

  }

  public static void encode(Encoder e, TimingInfo o) throws IOException {
    e.int32(o.PerCommand.length);
    for (int i = 0; i < o.PerCommand.length; i++) {
      o.PerCommand[i].encode(e);
    }

    e.int32(o.PerDrawCall.length);
    for (int i = 0; i < o.PerDrawCall.length; i++) {
      o.PerDrawCall[i].encode(e);
    }

    e.int32(o.PerFrame.length);
    for (int i = 0; i < o.PerFrame.length; i++) {
      o.PerFrame[i].encode(e);
    }

  }

  public static void decode(Decoder d, TimingInfo o) throws IOException {
    o.PerCommand = new AtomTimer[d.int32()];
    for (int i = 0; i < o.PerCommand.length; i++) {
      o.PerCommand[i] = new AtomTimer(d);
    }

    o.PerDrawCall = new AtomRangeTimer[d.int32()];
    for (int i = 0; i < o.PerDrawCall.length; i++) {
      o.PerDrawCall[i] = new AtomRangeTimer(d);
    }

    o.PerFrame = new AtomRangeTimer[d.int32()];
    for (int i = 0; i < o.PerFrame.length; i++) {
      o.PerFrame[i] = new AtomRangeTimer(d);
    }

  }

  public static void encode(Encoder e, Commands.GetCaptures.Call o) throws IOException {
  }

  public static void decode(Decoder d, Commands.GetCaptures.Call o) throws IOException {
  }

  public static void encode(Encoder e, Commands.GetDevices.Call o) throws IOException {
  }

  public static void decode(Decoder d, Commands.GetDevices.Call o) throws IOException {
  }

  public static void encode(Encoder e, Commands.GetFramebufferColor.Call o) throws IOException {
    o.device.encode(e);
    o.capture.encode(e);
    e.uint32(o.contextId);
    e.uint64(o.after);
    o.settings.encode(e);
  }

  public static void decode(Decoder d, Commands.GetFramebufferColor.Call o) throws IOException {
    o.device = DeviceId.decode(d);
    o.capture = CaptureId.decode(d);
    o.contextId = d.uint32();
    o.after = d.uint64();
    o.settings = new RenderSettings(d);
  }

  public static void encode(Encoder e, Commands.GetFramebufferDepth.Call o) throws IOException {
    o.device.encode(e);
    o.capture.encode(e);
    e.uint32(o.contextId);
    e.uint64(o.after);
  }

  public static void decode(Decoder d, Commands.GetFramebufferDepth.Call o) throws IOException {
    o.device = DeviceId.decode(d);
    o.capture = CaptureId.decode(d);
    o.contextId = d.uint32();
    o.after = d.uint64();
  }

  public static void encode(Encoder e, Commands.GetGlErrorCodes.Call o) throws IOException {
    o.device.encode(e);
    o.capture.encode(e);
    e.uint32(o.contextId);
  }

  public static void decode(Decoder d, Commands.GetGlErrorCodes.Call o) throws IOException {
    o.device = DeviceId.decode(d);
    o.capture = CaptureId.decode(d);
    o.contextId = d.uint32();
  }

  public static void encode(Encoder e, Commands.GetHierarchy.Call o) throws IOException {
    o.capture.encode(e);
    e.uint32(o.contextId);
  }

  public static void decode(Decoder d, Commands.GetHierarchy.Call o) throws IOException {
    o.capture = CaptureId.decode(d);
    o.contextId = d.uint32();
  }

  public static void encode(Encoder e, Commands.GetMemoryInfo.Call o) throws IOException {
    o.capture.encode(e);
    e.uint32(o.contextId);
    e.uint64(o.after);
    o.rng.encode(e);
  }

  public static void decode(Decoder d, Commands.GetMemoryInfo.Call o) throws IOException {
    o.capture = CaptureId.decode(d);
    o.contextId = d.uint32();
    o.after = d.uint64();
    o.rng = new MemoryRange(d);
  }

  public static void encode(Encoder e, Commands.GetState.Call o) throws IOException {
    o.capture.encode(e);
    e.uint32(o.contextId);
    e.uint64(o.after);
  }

  public static void decode(Decoder d, Commands.GetState.Call o) throws IOException {
    o.capture = CaptureId.decode(d);
    o.contextId = d.uint32();
    o.after = d.uint64();
  }

  public static void encode(Encoder e, Commands.GetTimingInfo.Call o) throws IOException {
    o.device.encode(e);
    o.capture.encode(e);
    e.uint32(o.contextId);
    o.mask.encode(e);
  }

  public static void decode(Decoder d, Commands.GetTimingInfo.Call o) throws IOException {
    o.device = DeviceId.decode(d);
    o.capture = CaptureId.decode(d);
    o.contextId = d.uint32();
    o.mask = TimingMask.decode(d);
  }

  public static void encode(Encoder e, Commands.ReplaceAtom.Call o) throws IOException {
    o.capture.encode(e);
    e.uint64(o.atomId);
    e.uint16(o.atomType);
    o.data.encode(e);
  }

  public static void decode(Decoder d, Commands.ReplaceAtom.Call o) throws IOException {
    o.capture = CaptureId.decode(d);
    o.atomId = d.uint64();
    o.atomType = d.uint16();
    o.data = new Binary(d);
  }

  public static void encode(Encoder e, Commands.ResolveAtomStream.Call o) throws IOException {
    o.id.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveAtomStream.Call o) throws IOException {
    o.id = AtomStreamId.decode(d);
  }

  public static void encode(Encoder e, Commands.ResolveBinary.Call o) throws IOException {
    o.id.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveBinary.Call o) throws IOException {
    o.id = BinaryId.decode(d);
  }

  public static void encode(Encoder e, Commands.ResolveCapture.Call o) throws IOException {
    o.id.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveCapture.Call o) throws IOException {
    o.id = CaptureId.decode(d);
  }

  public static void encode(Encoder e, Commands.ResolveDevice.Call o) throws IOException {
    o.id.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveDevice.Call o) throws IOException {
    o.id = DeviceId.decode(d);
  }

  public static void encode(Encoder e, Commands.ResolveHierarchy.Call o) throws IOException {
    o.id.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveHierarchy.Call o) throws IOException {
    o.id = HierarchyId.decode(d);
  }

  public static void encode(Encoder e, Commands.ResolveImageInfo.Call o) throws IOException {
    o.id.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveImageInfo.Call o) throws IOException {
    o.id = ImageInfoId.decode(d);
  }

  public static void encode(Encoder e, Commands.ResolveMemoryInfo.Call o) throws IOException {
    o.id.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveMemoryInfo.Call o) throws IOException {
    o.id = MemoryInfoId.decode(d);
  }

  public static void encode(Encoder e, Commands.ResolveSchema.Call o) throws IOException {
    o.id.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveSchema.Call o) throws IOException {
    o.id = SchemaId.decode(d);
  }

  public static void encode(Encoder e, Commands.ResolveTimingInfo.Call o) throws IOException {
    o.id.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveTimingInfo.Call o) throws IOException {
    o.id = TimingInfoId.decode(d);
  }

  public static void encode(Encoder e, Commands.GetCaptures.Result o) throws IOException {
    e.int32(o.value.length);
    for (int i = 0; i < o.value.length; i++) {
      o.value[i].encode(e);
    }

  }

  public static void decode(Decoder d, Commands.GetCaptures.Result o) throws IOException {
    o.value = new CaptureId[d.int32()];
    for (int i = 0; i < o.value.length; i++) {
      o.value[i] = CaptureId.decode(d);
    }

  }

  public static void encode(Encoder e, Commands.GetDevices.Result o) throws IOException {
    e.int32(o.value.length);
    for (int i = 0; i < o.value.length; i++) {
      o.value[i].encode(e);
    }

  }

  public static void decode(Decoder d, Commands.GetDevices.Result o) throws IOException {
    o.value = new DeviceId[d.int32()];
    for (int i = 0; i < o.value.length; i++) {
      o.value[i] = DeviceId.decode(d);
    }

  }

  public static void encode(Encoder e, Commands.GetFramebufferColor.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.GetFramebufferColor.Result o) throws IOException {
    o.value = ImageInfoId.decode(d);
  }

  public static void encode(Encoder e, Commands.GetFramebufferDepth.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.GetFramebufferDepth.Result o) throws IOException {
    o.value = ImageInfoId.decode(d);
  }

  public static void encode(Encoder e, Commands.GetGlErrorCodes.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.GetGlErrorCodes.Result o) throws IOException {
    o.value = BinaryId.decode(d);
  }

  public static void encode(Encoder e, Commands.GetHierarchy.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.GetHierarchy.Result o) throws IOException {
    o.value = HierarchyId.decode(d);
  }

  public static void encode(Encoder e, Commands.GetMemoryInfo.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.GetMemoryInfo.Result o) throws IOException {
    o.value = MemoryInfoId.decode(d);
  }

  public static void encode(Encoder e, Commands.GetState.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.GetState.Result o) throws IOException {
    o.value = BinaryId.decode(d);
  }

  public static void encode(Encoder e, Commands.GetTimingInfo.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.GetTimingInfo.Result o) throws IOException {
    o.value = TimingInfoId.decode(d);
  }

  public static void encode(Encoder e, Commands.ReplaceAtom.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.ReplaceAtom.Result o) throws IOException {
    o.value = CaptureId.decode(d);
  }

  public static void encode(Encoder e, Commands.ResolveAtomStream.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveAtomStream.Result o) throws IOException {
    o.value = new AtomStream(d);
  }

  public static void encode(Encoder e, Commands.ResolveBinary.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveBinary.Result o) throws IOException {
    o.value = new Binary(d);
  }

  public static void encode(Encoder e, Commands.ResolveCapture.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveCapture.Result o) throws IOException {
    o.value = new Capture(d);
  }

  public static void encode(Encoder e, Commands.ResolveDevice.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveDevice.Result o) throws IOException {
    o.value = new Device(d);
  }

  public static void encode(Encoder e, Commands.ResolveHierarchy.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveHierarchy.Result o) throws IOException {
    o.value = new Hierarchy(d);
  }

  public static void encode(Encoder e, Commands.ResolveImageInfo.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveImageInfo.Result o) throws IOException {
    o.value = new ImageInfo(d);
  }

  public static void encode(Encoder e, Commands.ResolveMemoryInfo.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveMemoryInfo.Result o) throws IOException {
    o.value = new MemoryInfo(d);
  }

  public static void encode(Encoder e, Commands.ResolveSchema.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveSchema.Result o) throws IOException {
    o.value = new Schema(d);
  }

  public static void encode(Encoder e, Commands.ResolveTimingInfo.Result o) throws IOException {
    o.value.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveTimingInfo.Result o) throws IOException {
    o.value = new TimingInfo(d);
  }

  public enum Entries implements BinaryObjectCreator {
    ApiInfoEnum {
      @Override
      public BinaryObject create() {
        return new ApiInfo();
      }
    },
    ArrayInfoEnum {
      @Override
      public BinaryObject create() {
        return new ArrayInfo();
      }
    },
    AtomGroupEnum {
      @Override
      public BinaryObject create() {
        return new AtomGroup();
      }
    },
    AtomInfoEnum {
      @Override
      public BinaryObject create() {
        return new AtomInfo();
      }
    },
    AtomRangeEnum {
      @Override
      public BinaryObject create() {
        return new AtomRange();
      }
    },
    AtomRangeTimerEnum {
      @Override
      public BinaryObject create() {
        return new AtomRangeTimer();
      }
    },
    AtomStreamEnum {
      @Override
      public BinaryObject create() {
        return new AtomStream();
      }
    },
    AtomTimerEnum {
      @Override
      public BinaryObject create() {
        return new AtomTimer();
      }
    },
    BinaryEnum {
      @Override
      public BinaryObject create() {
        return new Binary();
      }
    },
    CaptureEnum {
      @Override
      public BinaryObject create() {
        return new Capture();
      }
    },
    ClassInfoEnum {
      @Override
      public BinaryObject create() {
        return new ClassInfo();
      }
    },
    DeviceEnum {
      @Override
      public BinaryObject create() {
        return new Device();
      }
    },
    EnumEntryEnum {
      @Override
      public BinaryObject create() {
        return new EnumEntry();
      }
    },
    EnumInfoEnum {
      @Override
      public BinaryObject create() {
        return new EnumInfo();
      }
    },
    FieldInfoEnum {
      @Override
      public BinaryObject create() {
        return new FieldInfo();
      }
    },
    HierarchyEnum {
      @Override
      public BinaryObject create() {
        return new Hierarchy();
      }
    },
    ImageInfoEnum {
      @Override
      public BinaryObject create() {
        return new ImageInfo();
      }
    },
    MapInfoEnum {
      @Override
      public BinaryObject create() {
        return new MapInfo();
      }
    },
    MemoryInfoEnum {
      @Override
      public BinaryObject create() {
        return new MemoryInfo();
      }
    },
    MemoryRangeEnum {
      @Override
      public BinaryObject create() {
        return new MemoryRange();
      }
    },
    ParameterInfoEnum {
      @Override
      public BinaryObject create() {
        return new ParameterInfo();
      }
    },
    RenderSettingsEnum {
      @Override
      public BinaryObject create() {
        return new RenderSettings();
      }
    },
    SchemaEnum {
      @Override
      public BinaryObject create() {
        return new Schema();
      }
    },
    SimpleInfoEnum {
      @Override
      public BinaryObject create() {
        return new SimpleInfo();
      }
    },
    StructInfoEnum {
      @Override
      public BinaryObject create() {
        return new StructInfo();
      }
    },
    TimingInfoEnum {
      @Override
      public BinaryObject create() {
        return new TimingInfo();
      }
    },
    callGetCapturesEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetCaptures.Call();
      }
    },
    callGetDevicesEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetDevices.Call();
      }
    },
    callGetFramebufferColorEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetFramebufferColor.Call();
      }
    },
    callGetFramebufferDepthEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetFramebufferDepth.Call();
      }
    },
    callGetGlErrorCodesEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetGlErrorCodes.Call();
      }
    },
    callGetHierarchyEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetHierarchy.Call();
      }
    },
    callGetMemoryInfoEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetMemoryInfo.Call();
      }
    },
    callGetStateEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetState.Call();
      }
    },
    callGetTimingInfoEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetTimingInfo.Call();
      }
    },
    callReplaceAtomEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ReplaceAtom.Call();
      }
    },
    callResolveAtomStreamEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveAtomStream.Call();
      }
    },
    callResolveBinaryEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveBinary.Call();
      }
    },
    callResolveCaptureEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveCapture.Call();
      }
    },
    callResolveDeviceEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveDevice.Call();
      }
    },
    callResolveHierarchyEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveHierarchy.Call();
      }
    },
    callResolveImageInfoEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveImageInfo.Call();
      }
    },
    callResolveMemoryInfoEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveMemoryInfo.Call();
      }
    },
    callResolveSchemaEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveSchema.Call();
      }
    },
    callResolveTimingInfoEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveTimingInfo.Call();
      }
    },
    resultGetCapturesEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetCaptures.Result();
      }
    },
    resultGetDevicesEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetDevices.Result();
      }
    },
    resultGetFramebufferColorEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetFramebufferColor.Result();
      }
    },
    resultGetFramebufferDepthEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetFramebufferDepth.Result();
      }
    },
    resultGetGlErrorCodesEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetGlErrorCodes.Result();
      }
    },
    resultGetHierarchyEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetHierarchy.Result();
      }
    },
    resultGetMemoryInfoEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetMemoryInfo.Result();
      }
    },
    resultGetStateEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetState.Result();
      }
    },
    resultGetTimingInfoEnum {
      @Override
      public BinaryObject create() {
        return new Commands.GetTimingInfo.Result();
      }
    },
    resultReplaceAtomEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ReplaceAtom.Result();
      }
    },
    resultResolveAtomStreamEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveAtomStream.Result();
      }
    },
    resultResolveBinaryEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveBinary.Result();
      }
    },
    resultResolveCaptureEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveCapture.Result();
      }
    },
    resultResolveDeviceEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveDevice.Result();
      }
    },
    resultResolveHierarchyEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveHierarchy.Result();
      }
    },
    resultResolveImageInfoEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveImageInfo.Result();
      }
    },
    resultResolveMemoryInfoEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveMemoryInfo.Result();
      }
    },
    resultResolveSchemaEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveSchema.Result();
      }
    },
    resultResolveTimingInfoEnum {
      @Override
      public BinaryObject create() {
        return new Commands.ResolveTimingInfo.Result();
      }
    },
  }

}

