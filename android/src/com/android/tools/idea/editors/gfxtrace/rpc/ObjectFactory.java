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
import com.android.tools.rpclib.binary.BinaryObjectCreator;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.ObjectTypeID;
import java.io.IOException;

class ObjectFactory {
  public enum Entries implements BinaryObjectCreator {
    ArrayInfoEnum {
      @Override public BinaryObject create() {
        return new ArrayInfo();
      }
    },
    AtomGroupEnum {
      @Override public BinaryObject create() {
        return new AtomGroup();
      }
    },
    AtomInfoEnum {
      @Override public BinaryObject create() {
        return new AtomInfo();
      }
    },
    AtomRangeEnum {
      @Override public BinaryObject create() {
        return new AtomRange();
      }
    },
    AtomRangeTimerEnum {
      @Override public BinaryObject create() {
        return new AtomRangeTimer();
      }
    },
    AtomStreamEnum {
      @Override public BinaryObject create() {
        return new AtomStream();
      }
    },
    AtomTimerEnum {
      @Override public BinaryObject create() {
        return new AtomTimer();
      }
    },
    BinaryEnum {
      @Override public BinaryObject create() {
        return new Binary();
      }
    },
    CaptureEnum {
      @Override public BinaryObject create() {
        return new Capture();
      }
    },
    ClassInfoEnum {
      @Override public BinaryObject create() {
        return new ClassInfo();
      }
    },
    DeviceEnum {
      @Override public BinaryObject create() {
        return new Device();
      }
    },
    EnumEntryEnum {
      @Override public BinaryObject create() {
        return new EnumEntry();
      }
    },
    EnumInfoEnum {
      @Override public BinaryObject create() {
        return new EnumInfo();
      }
    },
    FieldInfoEnum {
      @Override public BinaryObject create() {
        return new FieldInfo();
      }
    },
    HierarchyEnum {
      @Override public BinaryObject create() {
        return new Hierarchy();
      }
    },
    ImageInfoEnum {
      @Override public BinaryObject create() {
        return new ImageInfo();
      }
    },
    MapInfoEnum {
      @Override public BinaryObject create() {
        return new MapInfo();
      }
    },
    MemoryInfoEnum {
      @Override public BinaryObject create() {
        return new MemoryInfo();
      }
    },
    MemoryRangeEnum {
      @Override public BinaryObject create() {
        return new MemoryRange();
      }
    },
    ParameterInfoEnum {
      @Override public BinaryObject create() {
        return new ParameterInfo();
      }
    },
    RenderSettingsEnum {
      @Override public BinaryObject create() {
        return new RenderSettings();
      }
    },
    SchemaEnum {
      @Override public BinaryObject create() {
        return new Schema();
      }
    },
    SimpleInfoEnum {
      @Override public BinaryObject create() {
        return new SimpleInfo();
      }
    },
    StaticArrayInfoEnum {
      @Override public BinaryObject create() {
        return new StaticArrayInfo();
      }
    },
    StructInfoEnum {
      @Override public BinaryObject create() {
        return new StructInfo();
      }
    },
    TimingInfoEnum {
      @Override public BinaryObject create() {
        return new TimingInfo();
      }
    },
    callGetCapturesEnum {
      @Override public BinaryObject create() {
        return new Commands.GetCaptures.Call();
      }
    },
    callGetDevicesEnum {
      @Override public BinaryObject create() {
        return new Commands.GetDevices.Call();
      }
    },
    callGetFramebufferColorEnum {
      @Override public BinaryObject create() {
        return new Commands.GetFramebufferColor.Call();
      }
    },
    callGetFramebufferDepthEnum {
      @Override public BinaryObject create() {
        return new Commands.GetFramebufferDepth.Call();
      }
    },
    callGetHierarchyEnum {
      @Override public BinaryObject create() {
        return new Commands.GetHierarchy.Call();
      }
    },
    callGetMemoryInfoEnum {
      @Override public BinaryObject create() {
        return new Commands.GetMemoryInfo.Call();
      }
    },
    callGetStateEnum {
      @Override public BinaryObject create() {
        return new Commands.GetState.Call();
      }
    },
    callGetTimingInfoEnum {
      @Override public BinaryObject create() {
        return new Commands.GetTimingInfo.Call();
      }
    },
    callPrerenderFramebuffersEnum {
      @Override public BinaryObject create() {
        return new Commands.PrerenderFramebuffers.Call();
      }
    },
    callReplaceAtomEnum {
      @Override public BinaryObject create() {
        return new Commands.ReplaceAtom.Call();
      }
    },
    callResolveAtomStreamEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveAtomStream.Call();
      }
    },
    callResolveBinaryEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveBinary.Call();
      }
    },
    callResolveCaptureEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveCapture.Call();
      }
    },
    callResolveDeviceEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveDevice.Call();
      }
    },
    callResolveHierarchyEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveHierarchy.Call();
      }
    },
    callResolveImageInfoEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveImageInfo.Call();
      }
    },
    callResolveMemoryInfoEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveMemoryInfo.Call();
      }
    },
    callResolveSchemaEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveSchema.Call();
      }
    },
    callResolveTimingInfoEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveTimingInfo.Call();
      }
    },
    resultGetCapturesEnum {
      @Override public BinaryObject create() {
        return new Commands.GetCaptures.Result();
      }
    },
    resultGetDevicesEnum {
      @Override public BinaryObject create() {
        return new Commands.GetDevices.Result();
      }
    },
    resultGetFramebufferColorEnum {
      @Override public BinaryObject create() {
        return new Commands.GetFramebufferColor.Result();
      }
    },
    resultGetFramebufferDepthEnum {
      @Override public BinaryObject create() {
        return new Commands.GetFramebufferDepth.Result();
      }
    },
    resultGetHierarchyEnum {
      @Override public BinaryObject create() {
        return new Commands.GetHierarchy.Result();
      }
    },
    resultGetMemoryInfoEnum {
      @Override public BinaryObject create() {
        return new Commands.GetMemoryInfo.Result();
      }
    },
    resultGetStateEnum {
      @Override public BinaryObject create() {
        return new Commands.GetState.Result();
      }
    },
    resultGetTimingInfoEnum {
      @Override public BinaryObject create() {
        return new Commands.GetTimingInfo.Result();
      }
    },
    resultPrerenderFramebuffersEnum {
      @Override public BinaryObject create() {
        return new Commands.PrerenderFramebuffers.Result();
      }
    },
    resultReplaceAtomEnum {
      @Override public BinaryObject create() {
        return new Commands.ReplaceAtom.Result();
      }
    },
    resultResolveAtomStreamEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveAtomStream.Result();
      }
    },
    resultResolveBinaryEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveBinary.Result();
      }
    },
    resultResolveCaptureEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveCapture.Result();
      }
    },
    resultResolveDeviceEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveDevice.Result();
      }
    },
    resultResolveHierarchyEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveHierarchy.Result();
      }
    },
    resultResolveImageInfoEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveImageInfo.Result();
      }
    },
    resultResolveMemoryInfoEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveMemoryInfo.Result();
      }
    },
    resultResolveSchemaEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveSchema.Result();
      }
    },
    resultResolveTimingInfoEnum {
      @Override public BinaryObject create() {
        return new Commands.ResolveTimingInfo.Result();
      }
    },
  }

  public static byte[] ArrayInfoIDBytes = { -15, 11, -79, -65, 16, -7, 83, 74, -10, 90, 111, -57, -53, -87, 71, -3, -79, 35, 91, 29, };
  public static byte[] AtomGroupIDBytes = { 49, 75, -61, -102, 14, -78, 0, -108, 104, -57, -51, -66, 11, 26, 35, -16, -13, 117, 86, 28, };
  public static byte[] AtomInfoIDBytes = { 126, 78, -23, -88, 31, 102, 93, 121, 10, 90, -52, -47, -41, 30, 57, -119, -11, 35, -21, 105, };
  public static byte[] AtomRangeIDBytes = { -6, -71, 29, 64, 22, -60, 38, 77, 64, 3, -79, 114, -55, 47, 47, 119, -34, -89, -106, 74, };
  public static byte[] AtomRangeTimerIDBytes = { -27, -35, -11, -103, -14, 35, -21, 72, 6, 38, -31, 3, -97, 90, 110, 45, -81, 118, -118, -14, };
  public static byte[] AtomStreamIDBytes = { -50, -31, -77, -62, -120, 110, 78, 117, 17, -25, -118, -24, 42, -57, 21, -6, -96, 88, 40, 32, };
  public static byte[] AtomTimerIDBytes = { 123, 100, 12, 0, 37, -18, -104, -93, 81, 123, 27, 13, -104, 115, 32, -109, 18, -102, 125, -60, };
  public static byte[] BinaryIDBytes = { -100, 96, -6, 124, -31, 19, -121, 61, -107, -63, 118, -72, 96, 86, -9, 53, 95, -104, 39, -117, };
  public static byte[] CaptureIDBytes = { 85, -128, 61, -51, -72, -37, 25, -58, -102, 18, -29, 89, -8, 3, 78, 7, 120, -54, -85, 92, };
  public static byte[] ClassInfoIDBytes = { -81, 13, 76, -126, 3, 98, 111, 67, 120, 15, 117, -66, -67, 110, -112, 11, 121, 80, -81, 79, };
  public static byte[] DeviceIDBytes = { 127, -91, 112, -39, -109, -4, 112, 39, 50, -39, -72, 110, 42, -101, -13, -124, 85, 79, 38, 80, };
  public static byte[] EnumEntryIDBytes = { -22, 127, -93, -17, -74, 76, 90, -123, -55, 95, -75, -95, 40, -2, -77, -89, 83, -82, -73, -48, };
  public static byte[] EnumInfoIDBytes = { 112, 4, 113, 11, 5, 43, -7, -39, -107, 38, -29, 104, 8, -105, 50, 109, -75, -44, -6, -98, };
  public static byte[] FieldInfoIDBytes = { 22, -37, 76, 87, 10, 31, -7, 51, 124, 31, 3, 13, 42, -50, -106, 126, -70, -88, 34, -91, };
  public static byte[] HierarchyIDBytes = { 74, 41, 107, 111, 55, -54, 118, 37, -68, -119, 26, -22, -128, 86, -87, 102, 14, 26, 26, -105, };
  public static byte[] ImageInfoIDBytes = { -125, 85, 119, -99, -25, 107, -19, -43, -59, 60, -122, 66, -2, -42, 26, 109, 43, -48, -5, -120, };
  public static byte[] MapInfoIDBytes = { -13, 64, 96, 2, 71, -119, 4, 94, 12, -113, -45, -99, -87, -41, 25, -117, -48, -120, -40, -33, };
  public static byte[] MemoryInfoIDBytes = { -6, 110, -98, -55, -28, 68, 98, 83, -96, 105, -25, 100, 59, 48, -63, -86, -3, 115, -79, -109, };
  public static byte[] MemoryRangeIDBytes = { -6, -59, -124, 36, 108, 27, 71, 14, -27, -37, 103, 41, -40, -79, -127, 59, -2, 83, 58, -99, };
  public static byte[] ParameterInfoIDBytes = { 70, 10, -39, 115, 23, -67, 125, 2, 20, -19, 6, -88, 9, 21, -92, -61, -48, -61, -32, -1, };
  public static byte[] RenderSettingsIDBytes = { 24, 35, 53, -17, -48, 58, -28, 37, 23, -60, 122, 43, -85, 50, 16, -100, 34, -122, 35, 0, };
  public static byte[] SchemaIDBytes = { 55, 76, -40, -84, 31, -56, 77, 16, -76, -25, -88, 60, 114, 73, 122, 98, 97, 120, -32, -51, };
  public static byte[] SimpleInfoIDBytes = { -51, 115, -60, -25, 72, 63, 11, -40, -100, 109, -88, 78, 81, 107, 76, -52, -91, -108, 58, 37, };
  public static byte[] StaticArrayInfoIDBytes = { 91, -96, 39, 64, 102, 65, 53, 111, 74, -35, 59, -96, -28, 38, 74, -110, 114, 104, -61, 59, };
  public static byte[] StructInfoIDBytes = { -15, -61, 88, 95, 55, -3, -78, -124, -17, 26, -11, -126, 61, -59, 90, 52, 73, 18, -44, -88, };
  public static byte[] TimingInfoIDBytes = { 25, -40, -25, -37, -25, -78, -36, 46, 115, 8, -62, -105, 46, 104, -71, -110, 82, 74, 10, -41, };
  public static byte[] callGetCapturesIDBytes = { -80, 47, 61, -91, -123, -107, -12, 33, 32, 118, -88, -90, 90, 83, -97, -4, -40, 16, -37, 21, };
  public static byte[] callGetDevicesIDBytes = { 25, 20, 100, 5, -10, -83, -115, 72, -61, -114, 123, -56, 24, 91, 47, 124, -73, -97, 92, 115, };
  public static byte[] callGetFramebufferColorIDBytes = { 41, 50, 93, -112, 35, -72, 46, 77, 26, 5, 11, -15, 1, -10, -95, 49, 61, -86, -103, -94, };
  public static byte[] callGetFramebufferDepthIDBytes = { 94, -86, -72, -97, -108, -87, -30, 125, 105, -35, 38, -61, -37, 80, 116, -84, -117, 65, 104, -9, };
  public static byte[] callGetHierarchyIDBytes = { -11, 74, -109, 5, 43, 35, 18, -84, -18, 68, 5, 74, 46, -106, -8, -69, -11, 76, -85, -107, };
  public static byte[] callGetMemoryInfoIDBytes = { 87, 94, 3, -39, -76, 16, 30, -108, 23, -44, 95, -5, -100, -40, 10, 23, 37, -100, 111, -88, };
  public static byte[] callGetStateIDBytes = { -11, -73, -75, 116, -87, 34, 119, 56, -51, 116, -95, -116, -112, -104, -68, 56, -124, 23, 117, 68, };
  public static byte[] callGetTimingInfoIDBytes = { -60, -46, 2, 118, 95, 61, -104, -117, 6, 1, 104, 65, 60, -55, 103, -128, -120, 59, -107, -116, };
  public static byte[] callPrerenderFramebuffersIDBytes = { 21, 52, -111, -115, -18, -107, 62, 82, -118, -80, 66, -21, -118, -49, -38, -15, -64, 37, -78, -21, };
  public static byte[] callReplaceAtomIDBytes = { 59, 106, -102, 48, 58, 50, -64, 58, 80, 92, -19, -87, -44, 106, 5, 84, -77, -30, -107, 61, };
  public static byte[] callResolveAtomStreamIDBytes = { 107, -17, 124, 43, 126, 60, 35, -29, -32, 56, -72, -36, -69, -46, -119, -109, -70, -103, 121, -112, };
  public static byte[] callResolveBinaryIDBytes = { 8, 104, 27, 119, -105, 11, -75, 52, -101, -19, 20, 88, 94, 69, 9, -126, 12, 71, 87, -23, };
  public static byte[] callResolveCaptureIDBytes = { 78, 110, 91, 73, 97, -17, -4, 60, 74, -8, -10, -82, -64, -54, -95, 103, 23, 115, -1, -8, };
  public static byte[] callResolveDeviceIDBytes = { -122, 84, -120, -11, -113, 62, -105, 87, -30, -104, 25, 126, 103, 37, -5, -94, 117, -21, 48, 10, };
  public static byte[] callResolveHierarchyIDBytes = { 57, 42, -97, 68, -2, 44, 15, -40, -59, 68, 88, 81, -108, 106, -107, -92, 34, -112, 52, 54, };
  public static byte[] callResolveImageInfoIDBytes = { 78, 81, 65, 19, 104, 111, 88, 90, -19, -32, 10, 5, 13, 1, -99, 40, -100, 33, -116, -69, };
  public static byte[] callResolveMemoryInfoIDBytes = { -33, 38, 108, 59, 6, 5, 8, -59, -63, -47, -122, 100, 33, -15, 88, 69, 15, 62, 47, 14, };
  public static byte[] callResolveSchemaIDBytes = { 69, -54, 22, -27, 93, 36, 105, 120, -95, 35, 50, 119, -105, 19, -30, -81, 48, 59, 112, 33, };
  public static byte[] callResolveTimingInfoIDBytes = { -7, -103, -21, 67, 39, -100, 56, 22, -90, -75, 106, 14, -96, -33, 121, -95, 38, 46, -29, -28, };
  public static byte[] resultGetCapturesIDBytes = { -80, 62, 4, 77, 50, -3, -113, -109, 90, -6, 48, 9, -98, -74, -101, 93, 2, -109, -28, 123, };
  public static byte[] resultGetDevicesIDBytes = { 126, -63, -128, -29, 121, 29, -22, 13, 98, 2, 88, 106, -21, -91, 36, -104, 48, -114, 70, -24, };
  public static byte[] resultGetFramebufferColorIDBytes = { -47, -14, -100, 72, -21, -80, 78, 78, -126, -49, -84, -23, -58, 113, 39, 4, 87, 126, -28, 37, };
  public static byte[] resultGetFramebufferDepthIDBytes = { 8, -40, 78, -46, -28, 47, -61, -74, 16, 71, -21, -82, -94, 86, -103, -111, 49, -125, -73, 39, };
  public static byte[] resultGetHierarchyIDBytes = { -1, 40, -32, 88, -105, -87, 94, 27, 98, -31, -96, -17, 5, -22, 62, 98, 93, 23, -86, 71, };
  public static byte[] resultGetMemoryInfoIDBytes = { 84, -36, 16, -49, 109, -106, 112, -39, 0, 125, -41, 31, 8, 74, -93, -81, 87, -38, 9, -18, };
  public static byte[] resultGetStateIDBytes = { -96, 30, 64, 32, -47, 77, -58, 25, -12, -53, 16, -50, -127, 9, -119, -33, -83, 99, -10, -18, };
  public static byte[] resultGetTimingInfoIDBytes = { 29, 23, 50, 84, -77, -67, -66, 87, -38, -17, -78, -80, 75, 95, -62, -123, -123, -53, -113, -13, };
  public static byte[] resultPrerenderFramebuffersIDBytes = { -62, -98, 17, 97, 34, -53, -40, -4, 108, 92, -71, 15, 7, -127, 103, -55, 46, 31, 47, -8, };
  public static byte[] resultReplaceAtomIDBytes = { 82, 98, -26, 39, 84, 78, -114, -113, 30, -97, 94, -11, -115, 100, 56, -103, -76, 87, -17, -1, };
  public static byte[] resultResolveAtomStreamIDBytes = { 5, -71, -104, 61, 111, 95, -64, 35, -78, -31, 46, 85, -12, 55, 113, -26, -45, -104, -65, 82, };
  public static byte[] resultResolveBinaryIDBytes = { -65, -67, 119, -10, -63, 19, -116, -2, 56, -43, 48, 60, 88, -56, 103, -65, 23, -50, 113, -23, };
  public static byte[] resultResolveCaptureIDBytes = { -77, 71, -48, 63, 40, 99, -2, -92, 50, -58, -16, 44, -20, 109, 51, -118, -78, 56, -9, 55, };
  public static byte[] resultResolveDeviceIDBytes = { 127, -46, -97, 103, -29, -66, 14, 70, 7, -33, -113, 110, 110, -123, 109, 38, -49, -47, -90, -93, };
  public static byte[] resultResolveHierarchyIDBytes = { 96, -13, 33, -104, 27, -122, -104, 56, -116, 30, 51, 60, 6, -99, 103, 42, 5, 80, -17, -4, };
  public static byte[] resultResolveImageInfoIDBytes = { -33, 69, 38, -61, -20, 54, 44, 90, 8, -29, 15, -114, 88, 82, -67, -78, -13, 38, 105, -97, };
  public static byte[] resultResolveMemoryInfoIDBytes = { 31, -49, -102, -123, -103, -126, 95, -88, 70, -109, 96, -91, 23, -38, -98, 17, 52, -24, 61, 34, };
  public static byte[] resultResolveSchemaIDBytes = { -31, 39, 82, 118, -126, -52, 62, 108, -72, 106, 16, -53, 81, -64, -53, -8, -93, 50, -57, -86, };
  public static byte[] resultResolveTimingInfoIDBytes = { -55, 55, -5, -44, 45, 69, -53, 21, 86, 29, 8, -94, -52, -23, -8, 67, 104, -125, 61, -89, };

  public static ObjectTypeID ArrayInfoID = new ObjectTypeID(ArrayInfoIDBytes);
  public static ObjectTypeID AtomGroupID = new ObjectTypeID(AtomGroupIDBytes);
  public static ObjectTypeID AtomInfoID = new ObjectTypeID(AtomInfoIDBytes);
  public static ObjectTypeID AtomRangeID = new ObjectTypeID(AtomRangeIDBytes);
  public static ObjectTypeID AtomRangeTimerID = new ObjectTypeID(AtomRangeTimerIDBytes);
  public static ObjectTypeID AtomStreamID = new ObjectTypeID(AtomStreamIDBytes);
  public static ObjectTypeID AtomTimerID = new ObjectTypeID(AtomTimerIDBytes);
  public static ObjectTypeID BinaryID = new ObjectTypeID(BinaryIDBytes);
  public static ObjectTypeID CaptureID = new ObjectTypeID(CaptureIDBytes);
  public static ObjectTypeID ClassInfoID = new ObjectTypeID(ClassInfoIDBytes);
  public static ObjectTypeID DeviceID = new ObjectTypeID(DeviceIDBytes);
  public static ObjectTypeID EnumEntryID = new ObjectTypeID(EnumEntryIDBytes);
  public static ObjectTypeID EnumInfoID = new ObjectTypeID(EnumInfoIDBytes);
  public static ObjectTypeID FieldInfoID = new ObjectTypeID(FieldInfoIDBytes);
  public static ObjectTypeID HierarchyID = new ObjectTypeID(HierarchyIDBytes);
  public static ObjectTypeID ImageInfoID = new ObjectTypeID(ImageInfoIDBytes);
  public static ObjectTypeID MapInfoID = new ObjectTypeID(MapInfoIDBytes);
  public static ObjectTypeID MemoryInfoID = new ObjectTypeID(MemoryInfoIDBytes);
  public static ObjectTypeID MemoryRangeID = new ObjectTypeID(MemoryRangeIDBytes);
  public static ObjectTypeID ParameterInfoID = new ObjectTypeID(ParameterInfoIDBytes);
  public static ObjectTypeID RenderSettingsID = new ObjectTypeID(RenderSettingsIDBytes);
  public static ObjectTypeID SchemaID = new ObjectTypeID(SchemaIDBytes);
  public static ObjectTypeID SimpleInfoID = new ObjectTypeID(SimpleInfoIDBytes);
  public static ObjectTypeID StaticArrayInfoID = new ObjectTypeID(StaticArrayInfoIDBytes);
  public static ObjectTypeID StructInfoID = new ObjectTypeID(StructInfoIDBytes);
  public static ObjectTypeID TimingInfoID = new ObjectTypeID(TimingInfoIDBytes);
  public static ObjectTypeID callGetCapturesID = new ObjectTypeID(callGetCapturesIDBytes);
  public static ObjectTypeID callGetDevicesID = new ObjectTypeID(callGetDevicesIDBytes);
  public static ObjectTypeID callGetFramebufferColorID = new ObjectTypeID(callGetFramebufferColorIDBytes);
  public static ObjectTypeID callGetFramebufferDepthID = new ObjectTypeID(callGetFramebufferDepthIDBytes);
  public static ObjectTypeID callGetHierarchyID = new ObjectTypeID(callGetHierarchyIDBytes);
  public static ObjectTypeID callGetMemoryInfoID = new ObjectTypeID(callGetMemoryInfoIDBytes);
  public static ObjectTypeID callGetStateID = new ObjectTypeID(callGetStateIDBytes);
  public static ObjectTypeID callGetTimingInfoID = new ObjectTypeID(callGetTimingInfoIDBytes);
  public static ObjectTypeID callPrerenderFramebuffersID = new ObjectTypeID(callPrerenderFramebuffersIDBytes);
  public static ObjectTypeID callReplaceAtomID = new ObjectTypeID(callReplaceAtomIDBytes);
  public static ObjectTypeID callResolveAtomStreamID = new ObjectTypeID(callResolveAtomStreamIDBytes);
  public static ObjectTypeID callResolveBinaryID = new ObjectTypeID(callResolveBinaryIDBytes);
  public static ObjectTypeID callResolveCaptureID = new ObjectTypeID(callResolveCaptureIDBytes);
  public static ObjectTypeID callResolveDeviceID = new ObjectTypeID(callResolveDeviceIDBytes);
  public static ObjectTypeID callResolveHierarchyID = new ObjectTypeID(callResolveHierarchyIDBytes);
  public static ObjectTypeID callResolveImageInfoID = new ObjectTypeID(callResolveImageInfoIDBytes);
  public static ObjectTypeID callResolveMemoryInfoID = new ObjectTypeID(callResolveMemoryInfoIDBytes);
  public static ObjectTypeID callResolveSchemaID = new ObjectTypeID(callResolveSchemaIDBytes);
  public static ObjectTypeID callResolveTimingInfoID = new ObjectTypeID(callResolveTimingInfoIDBytes);
  public static ObjectTypeID resultGetCapturesID = new ObjectTypeID(resultGetCapturesIDBytes);
  public static ObjectTypeID resultGetDevicesID = new ObjectTypeID(resultGetDevicesIDBytes);
  public static ObjectTypeID resultGetFramebufferColorID = new ObjectTypeID(resultGetFramebufferColorIDBytes);
  public static ObjectTypeID resultGetFramebufferDepthID = new ObjectTypeID(resultGetFramebufferDepthIDBytes);
  public static ObjectTypeID resultGetHierarchyID = new ObjectTypeID(resultGetHierarchyIDBytes);
  public static ObjectTypeID resultGetMemoryInfoID = new ObjectTypeID(resultGetMemoryInfoIDBytes);
  public static ObjectTypeID resultGetStateID = new ObjectTypeID(resultGetStateIDBytes);
  public static ObjectTypeID resultGetTimingInfoID = new ObjectTypeID(resultGetTimingInfoIDBytes);
  public static ObjectTypeID resultPrerenderFramebuffersID = new ObjectTypeID(resultPrerenderFramebuffersIDBytes);
  public static ObjectTypeID resultReplaceAtomID = new ObjectTypeID(resultReplaceAtomIDBytes);
  public static ObjectTypeID resultResolveAtomStreamID = new ObjectTypeID(resultResolveAtomStreamIDBytes);
  public static ObjectTypeID resultResolveBinaryID = new ObjectTypeID(resultResolveBinaryIDBytes);
  public static ObjectTypeID resultResolveCaptureID = new ObjectTypeID(resultResolveCaptureIDBytes);
  public static ObjectTypeID resultResolveDeviceID = new ObjectTypeID(resultResolveDeviceIDBytes);
  public static ObjectTypeID resultResolveHierarchyID = new ObjectTypeID(resultResolveHierarchyIDBytes);
  public static ObjectTypeID resultResolveImageInfoID = new ObjectTypeID(resultResolveImageInfoIDBytes);
  public static ObjectTypeID resultResolveMemoryInfoID = new ObjectTypeID(resultResolveMemoryInfoIDBytes);
  public static ObjectTypeID resultResolveSchemaID = new ObjectTypeID(resultResolveSchemaIDBytes);
  public static ObjectTypeID resultResolveTimingInfoID = new ObjectTypeID(resultResolveTimingInfoIDBytes);

  static {
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
    ObjectTypeID.register(StaticArrayInfoID, Entries.StaticArrayInfoEnum);
    ObjectTypeID.register(StructInfoID, Entries.StructInfoEnum);
    ObjectTypeID.register(TimingInfoID, Entries.TimingInfoEnum);
    ObjectTypeID.register(callGetCapturesID, Entries.callGetCapturesEnum);
    ObjectTypeID.register(callGetDevicesID, Entries.callGetDevicesEnum);
    ObjectTypeID.register(callGetFramebufferColorID, Entries.callGetFramebufferColorEnum);
    ObjectTypeID.register(callGetFramebufferDepthID, Entries.callGetFramebufferDepthEnum);
    ObjectTypeID.register(callGetHierarchyID, Entries.callGetHierarchyEnum);
    ObjectTypeID.register(callGetMemoryInfoID, Entries.callGetMemoryInfoEnum);
    ObjectTypeID.register(callGetStateID, Entries.callGetStateEnum);
    ObjectTypeID.register(callGetTimingInfoID, Entries.callGetTimingInfoEnum);
    ObjectTypeID.register(callPrerenderFramebuffersID, Entries.callPrerenderFramebuffersEnum);
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
    ObjectTypeID.register(resultGetHierarchyID, Entries.resultGetHierarchyEnum);
    ObjectTypeID.register(resultGetMemoryInfoID, Entries.resultGetMemoryInfoEnum);
    ObjectTypeID.register(resultGetStateID, Entries.resultGetStateEnum);
    ObjectTypeID.register(resultGetTimingInfoID, Entries.resultGetTimingInfoEnum);
    ObjectTypeID.register(resultPrerenderFramebuffersID, Entries.resultPrerenderFramebuffersEnum);
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

  public static void encode(Encoder e, ArrayInfo o) throws IOException {
    e.string(o.myName);
    o.myKind.encode(e);
    e.object(o.myElementType);
  }

  public static void decode(Decoder d, ArrayInfo o) throws IOException {
    o.myName = d.string();
    o.myKind = TypeKind.decode(d);
    o.myElementType = (TypeInfo)d.object();
  }

  public static void encode(Encoder e, AtomGroup o) throws IOException {
    e.string(o.myName);
    o.myRange.encode(e);
    e.int32(o.mySubGroups.length);
    for (int i = 0; i < o.mySubGroups.length; i++) {
      o.mySubGroups[i].encode(e);
    }
  }

  public static void decode(Decoder d, AtomGroup o) throws IOException {
    o.myName = d.string();
    o.myRange = new AtomRange(d);
    o.mySubGroups = new AtomGroup[d.int32()];
    for (int i = 0; i < o.mySubGroups.length; i++) {
      o.mySubGroups[i] = new AtomGroup(d);
    }
  }

  public static void encode(Encoder e, AtomInfo o) throws IOException {
    e.uint16(o.myType);
    e.string(o.myName);
    e.int32(o.myParameters.length);
    for (int i = 0; i < o.myParameters.length; i++) {
      o.myParameters[i].encode(e);
    }
    e.bool(o.myIsCommand);
    e.bool(o.myIsDrawCall);
    e.bool(o.myIsEndOfFrame);
    e.string(o.myDocumentationUrl);
  }

  public static void decode(Decoder d, AtomInfo o) throws IOException {
    o.myType = d.uint16();
    o.myName = d.string();
    o.myParameters = new ParameterInfo[d.int32()];
    for (int i = 0; i < o.myParameters.length; i++) {
      o.myParameters[i] = new ParameterInfo(d);
    }
    o.myIsCommand = d.bool();
    o.myIsDrawCall = d.bool();
    o.myIsEndOfFrame = d.bool();
    o.myDocumentationUrl = d.string();
  }

  public static void encode(Encoder e, AtomRange o) throws IOException {
    e.uint64(o.myFirst);
    e.uint64(o.myCount);
  }

  public static void decode(Decoder d, AtomRange o) throws IOException {
    o.myFirst = d.uint64();
    o.myCount = d.uint64();
  }

  public static void encode(Encoder e, AtomRangeTimer o) throws IOException {
    e.uint64(o.myFromAtomId);
    e.uint64(o.myToAtomId);
    e.uint64(o.myNanoseconds);
  }

  public static void decode(Decoder d, AtomRangeTimer o) throws IOException {
    o.myFromAtomId = d.uint64();
    o.myToAtomId = d.uint64();
    o.myNanoseconds = d.uint64();
  }

  public static void encode(Encoder e, AtomStream o) throws IOException {
    e.int32(o.myData.length);
    for (int i = 0; i < o.myData.length; i++) {
      e.uint8(o.myData[i]);
    }
    o.mySchema.encode(e);
  }

  public static void decode(Decoder d, AtomStream o) throws IOException {
    o.myData = new byte[d.int32()];
    for (int i = 0; i < o.myData.length; i++) {
      o.myData[i] = d.uint8();
    }
    o.mySchema = new SchemaId(d);
  }

  public static void encode(Encoder e, AtomTimer o) throws IOException {
    e.uint64(o.myAtomId);
    e.uint64(o.myNanoseconds);
  }

  public static void decode(Decoder d, AtomTimer o) throws IOException {
    o.myAtomId = d.uint64();
    o.myNanoseconds = d.uint64();
  }

  public static void encode(Encoder e, Binary o) throws IOException {
    e.int32(o.myData.length);
    for (int i = 0; i < o.myData.length; i++) {
      e.uint8(o.myData[i]);
    }
  }

  public static void decode(Decoder d, Binary o) throws IOException {
    o.myData = new byte[d.int32()];
    for (int i = 0; i < o.myData.length; i++) {
      o.myData[i] = d.uint8();
    }
  }

  public static void encode(Encoder e, Capture o) throws IOException {
    e.string(o.myName);
    e.string(o.myAPI);
    o.myAtoms.encode(e);
    e.int32(o.myContextIds.length);
    for (int i = 0; i < o.myContextIds.length; i++) {
      e.uint32(o.myContextIds[i]);
    }
  }

  public static void decode(Decoder d, Capture o) throws IOException {
    o.myName = d.string();
    o.myAPI = d.string();
    o.myAtoms = new AtomStreamId(d);
    o.myContextIds = new int[d.int32()];
    for (int i = 0; i < o.myContextIds.length; i++) {
      o.myContextIds[i] = d.uint32();
    }
  }

  public static void encode(Encoder e, ClassInfo o) throws IOException {
    e.string(o.myName);
    o.myKind.encode(e);
    e.int32(o.myFields.length);
    for (int i = 0; i < o.myFields.length; i++) {
      e.object(o.myFields[i]);
    }
    e.int32(o.myExtends.length);
    for (int i = 0; i < o.myExtends.length; i++) {
      e.object(o.myExtends[i]);
    }
  }

  public static void decode(Decoder d, ClassInfo o) throws IOException {
    o.myName = d.string();
    o.myKind = TypeKind.decode(d);
    o.myFields = new FieldInfo[d.int32()];
    for (int i = 0; i < o.myFields.length; i++) {
      o.myFields[i] = (FieldInfo)d.object();
    }
    o.myExtends = new ClassInfo[d.int32()];
    for (int i = 0; i < o.myExtends.length; i++) {
      o.myExtends[i] = (ClassInfo)d.object();
    }
  }

  public static void encode(Encoder e, Device o) throws IOException {
    e.string(o.myName);
    e.string(o.myModel);
    e.string(o.myOS);
    e.uint8(o.myPointerSize);
    e.uint8(o.myPointerAlignment);
    e.uint64(o.myMaxMemorySize);
    e.bool(o.myRequiresShaderPatching);
  }

  public static void decode(Decoder d, Device o) throws IOException {
    o.myName = d.string();
    o.myModel = d.string();
    o.myOS = d.string();
    o.myPointerSize = d.uint8();
    o.myPointerAlignment = d.uint8();
    o.myMaxMemorySize = d.uint64();
    o.myRequiresShaderPatching = d.bool();
  }

  public static void encode(Encoder e, EnumEntry o) throws IOException {
    e.string(o.myName);
    e.uint32(o.myValue);
  }

  public static void decode(Decoder d, EnumEntry o) throws IOException {
    o.myName = d.string();
    o.myValue = d.uint32();
  }

  public static void encode(Encoder e, EnumInfo o) throws IOException {
    e.string(o.myName);
    o.myKind.encode(e);
    e.int32(o.myEntries.length);
    for (int i = 0; i < o.myEntries.length; i++) {
      o.myEntries[i].encode(e);
    }
    e.int32(o.myExtends.length);
    for (int i = 0; i < o.myExtends.length; i++) {
      e.object(o.myExtends[i]);
    }
  }

  public static void decode(Decoder d, EnumInfo o) throws IOException {
    o.myName = d.string();
    o.myKind = TypeKind.decode(d);
    o.myEntries = new EnumEntry[d.int32()];
    for (int i = 0; i < o.myEntries.length; i++) {
      o.myEntries[i] = new EnumEntry(d);
    }
    o.myExtends = new EnumInfo[d.int32()];
    for (int i = 0; i < o.myExtends.length; i++) {
      o.myExtends[i] = (EnumInfo)d.object();
    }
  }

  public static void encode(Encoder e, FieldInfo o) throws IOException {
    e.string(o.myName);
    e.object(o.myType);
  }

  public static void decode(Decoder d, FieldInfo o) throws IOException {
    o.myName = d.string();
    o.myType = (TypeInfo)d.object();
  }

  public static void encode(Encoder e, Hierarchy o) throws IOException {
    o.myRoot.encode(e);
  }

  public static void decode(Decoder d, Hierarchy o) throws IOException {
    o.myRoot = new AtomGroup(d);
  }

  public static void encode(Encoder e, ImageInfo o) throws IOException {
    o.myFormat.encode(e);
    e.uint32(o.myWidth);
    e.uint32(o.myHeight);
    o.myData.encode(e);
  }

  public static void decode(Decoder d, ImageInfo o) throws IOException {
    o.myFormat = ImageFormat.decode(d);
    o.myWidth = d.uint32();
    o.myHeight = d.uint32();
    o.myData = new BinaryId(d);
  }

  public static void encode(Encoder e, MapInfo o) throws IOException {
    e.string(o.myName);
    o.myKind.encode(e);
    e.object(o.myKeyType);
    e.object(o.myValueType);
  }

  public static void decode(Decoder d, MapInfo o) throws IOException {
    o.myName = d.string();
    o.myKind = TypeKind.decode(d);
    o.myKeyType = (TypeInfo)d.object();
    o.myValueType = (TypeInfo)d.object();
  }

  public static void encode(Encoder e, MemoryInfo o) throws IOException {
    e.int32(o.myData.length);
    for (int i = 0; i < o.myData.length; i++) {
      e.uint8(o.myData[i]);
    }
    e.int32(o.myStale.length);
    for (int i = 0; i < o.myStale.length; i++) {
      o.myStale[i].encode(e);
    }
    e.int32(o.myCurrent.length);
    for (int i = 0; i < o.myCurrent.length; i++) {
      o.myCurrent[i].encode(e);
    }
    e.int32(o.myUnknown.length);
    for (int i = 0; i < o.myUnknown.length; i++) {
      o.myUnknown[i].encode(e);
    }
  }

  public static void decode(Decoder d, MemoryInfo o) throws IOException {
    o.myData = new byte[d.int32()];
    for (int i = 0; i < o.myData.length; i++) {
      o.myData[i] = d.uint8();
    }
    o.myStale = new MemoryRange[d.int32()];
    for (int i = 0; i < o.myStale.length; i++) {
      o.myStale[i] = new MemoryRange(d);
    }
    o.myCurrent = new MemoryRange[d.int32()];
    for (int i = 0; i < o.myCurrent.length; i++) {
      o.myCurrent[i] = new MemoryRange(d);
    }
    o.myUnknown = new MemoryRange[d.int32()];
    for (int i = 0; i < o.myUnknown.length; i++) {
      o.myUnknown[i] = new MemoryRange(d);
    }
  }

  public static void encode(Encoder e, MemoryRange o) throws IOException {
    e.uint64(o.myBase);
    e.uint64(o.mySize);
  }

  public static void decode(Decoder d, MemoryRange o) throws IOException {
    o.myBase = d.uint64();
    o.mySize = d.uint64();
  }

  public static void encode(Encoder e, ParameterInfo o) throws IOException {
    e.string(o.myName);
    e.object(o.myType);
    e.bool(o.myOut);
  }

  public static void decode(Decoder d, ParameterInfo o) throws IOException {
    o.myName = d.string();
    o.myType = (TypeInfo)d.object();
    o.myOut = d.bool();
  }

  public static void encode(Encoder e, RenderSettings o) throws IOException {
    e.uint32(o.myMaxWidth);
    e.uint32(o.myMaxHeight);
    e.bool(o.myWireframe);
  }

  public static void decode(Decoder d, RenderSettings o) throws IOException {
    o.myMaxWidth = d.uint32();
    o.myMaxHeight = d.uint32();
    o.myWireframe = d.bool();
  }

  public static void encode(Encoder e, Schema o) throws IOException {
    e.int32(o.myArrays.length);
    for (int i = 0; i < o.myArrays.length; i++) {
      e.object(o.myArrays[i]);
    }
    e.int32(o.myStaticArrays.length);
    for (int i = 0; i < o.myStaticArrays.length; i++) {
      e.object(o.myStaticArrays[i]);
    }
    e.int32(o.myMaps.length);
    for (int i = 0; i < o.myMaps.length; i++) {
      e.object(o.myMaps[i]);
    }
    e.int32(o.myEnums.length);
    for (int i = 0; i < o.myEnums.length; i++) {
      e.object(o.myEnums[i]);
    }
    e.int32(o.myStructs.length);
    for (int i = 0; i < o.myStructs.length; i++) {
      e.object(o.myStructs[i]);
    }
    e.int32(o.myClasses.length);
    for (int i = 0; i < o.myClasses.length; i++) {
      e.object(o.myClasses[i]);
    }
    e.int32(o.myAtoms.length);
    for (int i = 0; i < o.myAtoms.length; i++) {
      o.myAtoms[i].encode(e);
    }
    e.object(o.myState);
  }

  public static void decode(Decoder d, Schema o) throws IOException {
    o.myArrays = new ArrayInfo[d.int32()];
    for (int i = 0; i < o.myArrays.length; i++) {
      o.myArrays[i] = (ArrayInfo)d.object();
    }
    o.myStaticArrays = new StaticArrayInfo[d.int32()];
    for (int i = 0; i < o.myStaticArrays.length; i++) {
      o.myStaticArrays[i] = (StaticArrayInfo)d.object();
    }
    o.myMaps = new MapInfo[d.int32()];
    for (int i = 0; i < o.myMaps.length; i++) {
      o.myMaps[i] = (MapInfo)d.object();
    }
    o.myEnums = new EnumInfo[d.int32()];
    for (int i = 0; i < o.myEnums.length; i++) {
      o.myEnums[i] = (EnumInfo)d.object();
    }
    o.myStructs = new StructInfo[d.int32()];
    for (int i = 0; i < o.myStructs.length; i++) {
      o.myStructs[i] = (StructInfo)d.object();
    }
    o.myClasses = new ClassInfo[d.int32()];
    for (int i = 0; i < o.myClasses.length; i++) {
      o.myClasses[i] = (ClassInfo)d.object();
    }
    o.myAtoms = new AtomInfo[d.int32()];
    for (int i = 0; i < o.myAtoms.length; i++) {
      o.myAtoms[i] = new AtomInfo(d);
    }
    o.myState = (StructInfo)d.object();
  }

  public static void encode(Encoder e, SimpleInfo o) throws IOException {
    e.string(o.myName);
    o.myKind.encode(e);
  }

  public static void decode(Decoder d, SimpleInfo o) throws IOException {
    o.myName = d.string();
    o.myKind = TypeKind.decode(d);
  }

  public static void encode(Encoder e, StaticArrayInfo o) throws IOException {
    e.string(o.myName);
    o.myKind.encode(e);
    e.object(o.myElementType);
    e.int32(o.myDimensions.length);
    for (int i = 0; i < o.myDimensions.length; i++) {
      e.uint32(o.myDimensions[i]);
    }
  }

  public static void decode(Decoder d, StaticArrayInfo o) throws IOException {
    o.myName = d.string();
    o.myKind = TypeKind.decode(d);
    o.myElementType = (TypeInfo)d.object();
    o.myDimensions = new int[d.int32()];
    for (int i = 0; i < o.myDimensions.length; i++) {
      o.myDimensions[i] = d.uint32();
    }
  }

  public static void encode(Encoder e, StructInfo o) throws IOException {
    e.string(o.myName);
    o.myKind.encode(e);
    e.int32(o.myFields.length);
    for (int i = 0; i < o.myFields.length; i++) {
      e.object(o.myFields[i]);
    }
  }

  public static void decode(Decoder d, StructInfo o) throws IOException {
    o.myName = d.string();
    o.myKind = TypeKind.decode(d);
    o.myFields = new FieldInfo[d.int32()];
    for (int i = 0; i < o.myFields.length; i++) {
      o.myFields[i] = (FieldInfo)d.object();
    }
  }

  public static void encode(Encoder e, TimingInfo o) throws IOException {
    e.int32(o.myPerCommand.length);
    for (int i = 0; i < o.myPerCommand.length; i++) {
      o.myPerCommand[i].encode(e);
    }
    e.int32(o.myPerDrawCall.length);
    for (int i = 0; i < o.myPerDrawCall.length; i++) {
      o.myPerDrawCall[i].encode(e);
    }
    e.int32(o.myPerFrame.length);
    for (int i = 0; i < o.myPerFrame.length; i++) {
      o.myPerFrame[i].encode(e);
    }
  }

  public static void decode(Decoder d, TimingInfo o) throws IOException {
    o.myPerCommand = new AtomTimer[d.int32()];
    for (int i = 0; i < o.myPerCommand.length; i++) {
      o.myPerCommand[i] = new AtomTimer(d);
    }
    o.myPerDrawCall = new AtomRangeTimer[d.int32()];
    for (int i = 0; i < o.myPerDrawCall.length; i++) {
      o.myPerDrawCall[i] = new AtomRangeTimer(d);
    }
    o.myPerFrame = new AtomRangeTimer[d.int32()];
    for (int i = 0; i < o.myPerFrame.length; i++) {
      o.myPerFrame[i] = new AtomRangeTimer(d);
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
    o.myDevice.encode(e);
    o.myCapture.encode(e);
    e.uint32(o.myContextId);
    e.uint64(o.myAfter);
    o.mySettings.encode(e);
  }

  public static void decode(Decoder d, Commands.GetFramebufferColor.Call o) throws IOException {
    o.myDevice = new DeviceId(d);
    o.myCapture = new CaptureId(d);
    o.myContextId = d.uint32();
    o.myAfter = d.uint64();
    o.mySettings = new RenderSettings(d);
  }

  public static void encode(Encoder e, Commands.GetFramebufferDepth.Call o) throws IOException {
    o.myDevice.encode(e);
    o.myCapture.encode(e);
    e.uint32(o.myContextId);
    e.uint64(o.myAfter);
  }

  public static void decode(Decoder d, Commands.GetFramebufferDepth.Call o) throws IOException {
    o.myDevice = new DeviceId(d);
    o.myCapture = new CaptureId(d);
    o.myContextId = d.uint32();
    o.myAfter = d.uint64();
  }

  public static void encode(Encoder e, Commands.GetHierarchy.Call o) throws IOException {
    o.myCapture.encode(e);
    e.uint32(o.myContextId);
  }

  public static void decode(Decoder d, Commands.GetHierarchy.Call o) throws IOException {
    o.myCapture = new CaptureId(d);
    o.myContextId = d.uint32();
  }

  public static void encode(Encoder e, Commands.GetMemoryInfo.Call o) throws IOException {
    o.myCapture.encode(e);
    e.uint32(o.myContextId);
    e.uint64(o.myAfter);
    o.myRng.encode(e);
  }

  public static void decode(Decoder d, Commands.GetMemoryInfo.Call o) throws IOException {
    o.myCapture = new CaptureId(d);
    o.myContextId = d.uint32();
    o.myAfter = d.uint64();
    o.myRng = new MemoryRange(d);
  }

  public static void encode(Encoder e, Commands.GetState.Call o) throws IOException {
    o.myCapture.encode(e);
    e.uint32(o.myContextId);
    e.uint64(o.myAfter);
  }

  public static void decode(Decoder d, Commands.GetState.Call o) throws IOException {
    o.myCapture = new CaptureId(d);
    o.myContextId = d.uint32();
    o.myAfter = d.uint64();
  }

  public static void encode(Encoder e, Commands.GetTimingInfo.Call o) throws IOException {
    o.myDevice.encode(e);
    o.myCapture.encode(e);
    e.uint32(o.myContextId);
    o.myMask.encode(e);
  }

  public static void decode(Decoder d, Commands.GetTimingInfo.Call o) throws IOException {
    o.myDevice = new DeviceId(d);
    o.myCapture = new CaptureId(d);
    o.myContextId = d.uint32();
    o.myMask = TimingMask.decode(d);
  }

  public static void encode(Encoder e, Commands.PrerenderFramebuffers.Call o) throws IOException {
    o.myDevice.encode(e);
    o.myCapture.encode(e);
    e.uint32(o.myWidth);
    e.uint32(o.myHeight);
    e.int32(o.myAtomIds.length);
    for (int i = 0; i < o.myAtomIds.length; i++) {
      e.uint64(o.myAtomIds[i]);
    }
  }

  public static void decode(Decoder d, Commands.PrerenderFramebuffers.Call o) throws IOException {
    o.myDevice = new DeviceId(d);
    o.myCapture = new CaptureId(d);
    o.myWidth = d.uint32();
    o.myHeight = d.uint32();
    o.myAtomIds = new long[d.int32()];
    for (int i = 0; i < o.myAtomIds.length; i++) {
      o.myAtomIds[i] = d.uint64();
    }
  }

  public static void encode(Encoder e, Commands.ReplaceAtom.Call o) throws IOException {
    o.myCapture.encode(e);
    e.uint64(o.myAtomId);
    e.uint16(o.myAtomType);
    o.myData.encode(e);
  }

  public static void decode(Decoder d, Commands.ReplaceAtom.Call o) throws IOException {
    o.myCapture = new CaptureId(d);
    o.myAtomId = d.uint64();
    o.myAtomType = d.uint16();
    o.myData = new Binary(d);
  }

  public static void encode(Encoder e, Commands.ResolveAtomStream.Call o) throws IOException {
    o.myId.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveAtomStream.Call o) throws IOException {
    o.myId = new AtomStreamId(d);
  }

  public static void encode(Encoder e, Commands.ResolveBinary.Call o) throws IOException {
    o.myId.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveBinary.Call o) throws IOException {
    o.myId = new BinaryId(d);
  }

  public static void encode(Encoder e, Commands.ResolveCapture.Call o) throws IOException {
    o.myId.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveCapture.Call o) throws IOException {
    o.myId = new CaptureId(d);
  }

  public static void encode(Encoder e, Commands.ResolveDevice.Call o) throws IOException {
    o.myId.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveDevice.Call o) throws IOException {
    o.myId = new DeviceId(d);
  }

  public static void encode(Encoder e, Commands.ResolveHierarchy.Call o) throws IOException {
    o.myId.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveHierarchy.Call o) throws IOException {
    o.myId = new HierarchyId(d);
  }

  public static void encode(Encoder e, Commands.ResolveImageInfo.Call o) throws IOException {
    o.myId.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveImageInfo.Call o) throws IOException {
    o.myId = new ImageInfoId(d);
  }

  public static void encode(Encoder e, Commands.ResolveMemoryInfo.Call o) throws IOException {
    o.myId.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveMemoryInfo.Call o) throws IOException {
    o.myId = new MemoryInfoId(d);
  }

  public static void encode(Encoder e, Commands.ResolveSchema.Call o) throws IOException {
    o.myId.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveSchema.Call o) throws IOException {
    o.myId = new SchemaId(d);
  }

  public static void encode(Encoder e, Commands.ResolveTimingInfo.Call o) throws IOException {
    o.myId.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveTimingInfo.Call o) throws IOException {
    o.myId = new TimingInfoId(d);
  }

  public static void encode(Encoder e, Commands.GetCaptures.Result o) throws IOException {
    e.int32(o.myValue.length);
    for (int i = 0; i < o.myValue.length; i++) {
      o.myValue[i].encode(e);
    }
  }

  public static void decode(Decoder d, Commands.GetCaptures.Result o) throws IOException {
    o.myValue = new CaptureId[d.int32()];
    for (int i = 0; i < o.myValue.length; i++) {
      o.myValue[i] = new CaptureId(d);
    }
  }

  public static void encode(Encoder e, Commands.GetDevices.Result o) throws IOException {
    e.int32(o.myValue.length);
    for (int i = 0; i < o.myValue.length; i++) {
      o.myValue[i].encode(e);
    }
  }

  public static void decode(Decoder d, Commands.GetDevices.Result o) throws IOException {
    o.myValue = new DeviceId[d.int32()];
    for (int i = 0; i < o.myValue.length; i++) {
      o.myValue[i] = new DeviceId(d);
    }
  }

  public static void encode(Encoder e, Commands.GetFramebufferColor.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.GetFramebufferColor.Result o) throws IOException {
    o.myValue = new ImageInfoId(d);
  }

  public static void encode(Encoder e, Commands.GetFramebufferDepth.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.GetFramebufferDepth.Result o) throws IOException {
    o.myValue = new ImageInfoId(d);
  }

  public static void encode(Encoder e, Commands.GetHierarchy.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.GetHierarchy.Result o) throws IOException {
    o.myValue = new HierarchyId(d);
  }

  public static void encode(Encoder e, Commands.GetMemoryInfo.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.GetMemoryInfo.Result o) throws IOException {
    o.myValue = new MemoryInfoId(d);
  }

  public static void encode(Encoder e, Commands.GetState.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.GetState.Result o) throws IOException {
    o.myValue = new BinaryId(d);
  }

  public static void encode(Encoder e, Commands.GetTimingInfo.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.GetTimingInfo.Result o) throws IOException {
    o.myValue = new TimingInfoId(d);
  }

  public static void encode(Encoder e, Commands.PrerenderFramebuffers.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.PrerenderFramebuffers.Result o) throws IOException {
    o.myValue = new BinaryId(d);
  }

  public static void encode(Encoder e, Commands.ReplaceAtom.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.ReplaceAtom.Result o) throws IOException {
    o.myValue = new CaptureId(d);
  }

  public static void encode(Encoder e, Commands.ResolveAtomStream.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveAtomStream.Result o) throws IOException {
    o.myValue = new AtomStream(d);
  }

  public static void encode(Encoder e, Commands.ResolveBinary.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveBinary.Result o) throws IOException {
    o.myValue = new Binary(d);
  }

  public static void encode(Encoder e, Commands.ResolveCapture.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveCapture.Result o) throws IOException {
    o.myValue = new Capture(d);
  }

  public static void encode(Encoder e, Commands.ResolveDevice.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveDevice.Result o) throws IOException {
    o.myValue = new Device(d);
  }

  public static void encode(Encoder e, Commands.ResolveHierarchy.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveHierarchy.Result o) throws IOException {
    o.myValue = new Hierarchy(d);
  }

  public static void encode(Encoder e, Commands.ResolveImageInfo.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveImageInfo.Result o) throws IOException {
    o.myValue = new ImageInfo(d);
  }

  public static void encode(Encoder e, Commands.ResolveMemoryInfo.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveMemoryInfo.Result o) throws IOException {
    o.myValue = new MemoryInfo(d);
  }

  public static void encode(Encoder e, Commands.ResolveSchema.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveSchema.Result o) throws IOException {
    o.myValue = new Schema(d);
  }

  public static void encode(Encoder e, Commands.ResolveTimingInfo.Result o) throws IOException {
    o.myValue.encode(e);
  }

  public static void decode(Decoder d, Commands.ResolveTimingInfo.Result o) throws IOException {
    o.myValue = new TimingInfo(d);
  }
}