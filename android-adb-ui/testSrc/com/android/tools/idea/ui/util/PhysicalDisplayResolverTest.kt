/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui.util

import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbSession
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.UsefulTestCase.assertThrows
import com.jetbrains.rd.generator.nova.fail
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Test for functions defined in PhysicalDisplayIdResolver.kt. */
internal class PhysicalDisplayIdResolverTest {
  @Test
  fun testPhysicalDisplayIdLookup() = runTest {
    val adbSession = FakeAdbSession()
    val device = DeviceSelector.fromSerialNumber("123")
    adbSession.deviceServices.configureShellCommand(device, "dumpsys display", dumpsysOutput)
    assertThat(adbSession.getPhysicalDisplayId(device, 0)).isEqualTo(4619827259835644672)
    assertThat(adbSession.getPhysicalDisplayId(device, 2)).isEqualTo(4619827551948147201)
    assertThat(adbSession.getPhysicalDisplayId(device, 3)).isEqualTo(4619827124781842690)
    try {
      adbSession.getPhysicalDisplayId(device, 1)
      fail("Expected an exception")
    }
    catch (_: Exception) {
    }
    adbSession.closeAndJoin()
  }
}

private val dumpsysOutput = """
DISPLAY MANAGER (dumpsys display)
  mOnlyCode=false
  mSafeMode=false
  mPendingTraversal=false
  mViewports=[DisplayViewport{type=INTERNAL, valid=true, isActive=true, displayId=0, uniqueId='local:4619827259835644672', physicalPort=0, orientation=0, logicalFrame=Rect(0, 0 - 1080, 600), physicalFrame=Rect(0, 0 - 1080, 600), deviceWidth=1080, deviceHeight=600}, DisplayViewport{type=EXTERNAL, valid=true, isActive=true, displayId=2, uniqueId='local:4619827551948147201', physicalPort=1, orientation=0, logicalFrame=Rect(0, 0 - 400, 600), physicalFrame=Rect(0, 0 - 400, 600), deviceWidth=400, deviceHeight=600}, DisplayViewport{type=EXTERNAL, valid=true, isActive=true, displayId=3, uniqueId='local:4619827124781842690', physicalPort=2, orientation=0, logicalFrame=Rect(0, 0 - 3000, 600), physicalFrame=Rect(0, 0 - 3000, 600), deviceWidth=3000, deviceHeight=600}, DisplayViewport{type=VIRTUAL, valid=true, isActive=true, displayId=5, uniqueId='virtual:com.android.systemui:DistantDisplay-NavigationViewSurface', physicalPort=null, orientation=0, logicalFrame=Rect(0, 0 - 1500, 588), physicalFrame=Rect(0, 0 - 1500, 588), deviceWidth=1500, deviceHeight=588}, DisplayViewport{type=VIRTUAL, valid=true, isActive=true, displayId=6, uniqueId='virtual:com.android.systemui:DistantDisplay-RootViewSurface', physicalPort=null, orientation=0, logicalFrame=Rect(0, 0 - 1500, 588), physicalFrame=Rect(0, 0 - 1500, 588), deviceWidth=1500, deviceHeight=588}]
  mDefaultDisplayDefaultColorMode=0
  mWifiDisplayScanRequestCount=0
  mStableDisplaySize=Point(1080, 600)
  mMinimumBrightnessCurve=[(0.0, 0.0), (2000.0, 50.0), (4000.0, 90.0)]


Display States: size=6
  Display Id=0
  Display State=ON
  Display Brightness=0.3976378
  Display SdrBrightness=0.3976378
  Display Id=2
  Display State=ON
  Display Brightness=0.4999999
  Display SdrBrightness=0.4999999
  Display Id=3
  Display State=ON
  Display Brightness=0.4999999
  Display SdrBrightness=0.4999999
  Display Id=4
  Display State=UNKNOWN
  Display Brightness=0.0
  Display SdrBrightness=0.0
  Display Id=5
  Display State=UNKNOWN
  Display Brightness=0.0
  Display SdrBrightness=0.0
  Display Id=6
  Display State=UNKNOWN
  Display Brightness=0.0
  Display SdrBrightness=0.0

Display Adapters: size=3
  LocalDisplayAdapter
  VirtualDisplayAdapter
  OverlayDisplayAdapter
    mCurrentOverlaySetting=
    mOverlays: size=0

Display Devices: size=6
  DisplayDeviceInfo{"Built-in Screen": uniqueId="local:4619827259835644672", 1080 x 600, modeId 1, defaultModeId 1, supportedModes [{id=1, width=1080, height=600, fps=60.000004, alternativeRefreshRates=[]}], colorMode 0, supportedColorModes [0], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, allmSupported false, gameContentTypeSupported false, density 120, 120.0 x 120.0 dpi, appVsyncOff 1000000, presDeadline 16666666, touch INTERNAL, rotation 0, type INTERNAL, address {port=0, model=0x401cec6a7a2b7b}, deviceProductInfo DeviceProductInfo{name=EMU_display_0, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, state ON, committedState ON, frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.39763778, FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_ROTATES_WITH_CONTENT, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, installOrientation 0}
    mAdapter=LocalDisplayAdapter
    mUniqueId=local:4619827259835644672
    mDisplayToken=android.os.BinderProxy@974b667
    mCurrentLayerStack=0
    mCurrentFlags=1
    mCurrentOrientation=0
    mCurrentLayerStackRect=Rect(0, 0 - 1080, 600)
    mCurrentDisplayRect=Rect(0, 0 - 1080, 600)
    mCurrentSurface=null
    mPhysicalDisplayId=4619827259835644672
    mDisplayModeSpecs={baseModeId=1 allowGroupSwitching=false primaryRefreshRateRange=[0 60] appRequestRefreshRateRange=[0 Infinity]}
    mDisplayModeSpecsInvalid=false
    mActiveModeId=1
    mActiveColorMode=0
    mDefaultModeId=1
    mUserPreferredModeId=-1
    mState=ON
    mCommittedState=ON
    mBrightnessState=0.3976378
    mBacklightAdapter=BacklightAdapter [useSurfaceControl=false (force_anyway? false), backlight=null]
    mAllmSupported=false
    mAllmRequested=false
    mGameContentTypeSupported=false
    mGameContentTypeRequested=false
    mStaticDisplayInfo=StaticDisplayInfo{isInternal=true, density=0.75, secure=true, deviceProductInfo=DeviceProductInfo{name=EMU_display_0, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, installOrientation=0}
    mSfDisplayModes=
      DisplayMode{id=0, width=1080, height=600, xDpi=120.0, yDpi=120.0, refreshRate=60.000004, appVsyncOffsetNanos=1000000, presentationDeadlineNanos=16666666, group=0}
    mActiveSfDisplayMode=DisplayMode{id=0, width=1080, height=600, xDpi=120.0, yDpi=120.0, refreshRate=60.000004, appVsyncOffsetNanos=1000000, presentationDeadlineNanos=16666666, group=0}
    mSupportedModes=
      DisplayModeRecord{mMode={id=1, width=1080, height=600, fps=60.000004, alternativeRefreshRates=[]}}
    mSupportedColorModes=[0]
    mDisplayDeviceConfig=DisplayDeviceConfig{mLoadedFrom=<config.xml>, mBacklight=null, mNits=null, mRawBacklight=null, mRawNits=null, mInterpolationType=0, mBrightness=null, mBrightnessToBacklightSpline=MonotoneCubicSpline{[(0.0, 0.0: 1.0), (1.0, 1.0: 1.0)]}, mBacklightToBrightnessSpline=MonotoneCubicSpline{[(0.0, 0.0: 1.0), (1.0, 1.0: 1.0)]}, mNitsToBacklightSpline=null, mBacklightMinimum=0.035433073, mBacklightMaximum=1.0, mBrightnessDefault=0.39763778, mQuirks=null, isHbmEnabled=false, mHbmData=null, mSdrToHdrRatioSpline=null, mBrightnessThrottlingData=null, mOriginalBrightnessThrottlingData=null
    , mBrightnessRampFastDecrease=0.70472443, mBrightnessRampFastIncrease=0.70472443, mBrightnessRampSlowDecrease=0.23228346, mBrightnessRampSlowIncrease=0.23228346, mBrightnessRampDecreaseMaxMillis=0, mBrightnessRampIncreaseMaxMillis=0
    , mAmbientHorizonLong=10000, mAmbientHorizonShort=2000
    , mScreenDarkeningMinThreshold=0.0, mScreenDarkeningMinThresholdIdle=0.0, mScreenBrighteningMinThreshold=0.0, mScreenBrighteningMinThresholdIdle=0.0, mAmbientLuxDarkeningMinThreshold=0.0, mAmbientLuxDarkeningMinThresholdIdle=0.0, mAmbientLuxBrighteningMinThreshold=0.0, mAmbientLuxBrighteningMinThresholdIdle=0.0
    , mScreenBrighteningLevels=[0.0], mScreenBrighteningPercentages=[10.0], mScreenDarkeningLevels=[0.0], mScreenDarkeningPercentages=[20.0], mAmbientBrighteningLevels=[0.0], mAmbientBrighteningPercentages=[10.0], mAmbientDarkeningLevels=[0.0], mAmbientDarkeningPercentages=[20.0]
    , mAmbientBrighteningLevelsIdle=[0.0], mAmbientBrighteningPercentagesIdle=[10.0], mAmbientDarkeningLevelsIdle=[0.0], mAmbientDarkeningPercentagesIdle=[20.0], mScreenBrighteningLevelsIdle=[0.0], mScreenBrighteningPercentagesIdle=[10.0], mScreenDarkeningLevelsIdle=[0.0], mScreenDarkeningPercentagesIdle=[20.0]
    , mAmbientLightSensor=Sensor{type: , name: , refreshRateRange: [0.0, Infinity]} , mScreenOffBrightnessSensor=Sensor{type: null, name: null, refreshRateRange: [0.0, Infinity]} , mProximitySensor=Sensor{type: null, name: null, refreshRateRange: [0.0, Infinity]} , mRefreshRateLimitations= [], mDensityMapping= null, mAutoBrightnessBrighteningLightDebounce= -1, mAutoBrightnessDarkeningLightDebounce= -1, mBrightnessLevelsLux= [0.0], mBrightnessLevelsNits= [], mDdcAutoBrightnessAvailable= true, mAutoBrightnessAvailable= true
    , mDefaultLowBlockingZoneRefreshRate= 0, mDefaultHighBlockingZoneRefreshRate= 0, mDefaultPeakRefreshRate= 0, mDefaultRefreshRate= 60, mDefaultRefreshRateInHbmHdr= 0, mDefaultRefreshRateInHbmSunlight= 0, mLowDisplayBrightnessThresholds= [], mLowAmbientBrightnessThresholds= [], mHighDisplayBrightnessThresholds= [], mHighAmbientBrightnessThresholds= []
    , mScreenOffBrightnessSensorValueToLux=null}
  DisplayDeviceInfo{"HDMI Screen": uniqueId="local:4619827551948147201", 400 x 600, modeId 2, defaultModeId 2, supportedModes [{id=2, width=400, height=600, fps=160.0, alternativeRefreshRates=[]}], colorMode 0, supportedColorModes [0], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, allmSupported false, gameContentTypeSupported false, density 120, 120.0 x 120.0 dpi, appVsyncOff 2000000, presDeadline 6250000, touch EXTERNAL, rotation 0, type EXTERNAL, address {port=1, model=0x401cecae7d6e8a}, deviceProductInfo DeviceProductInfo{name=EMU_display_1, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, state ON, committedState ON, frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.5, FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_PRIVATE, FLAG_PRESENTATION, FLAG_OWN_CONTENT_ONLY, installOrientation 0}
    mAdapter=LocalDisplayAdapter
    mUniqueId=local:4619827551948147201
    mDisplayToken=android.os.BinderProxy@ef3c14
    mCurrentLayerStack=2
    mCurrentFlags=1
    mCurrentOrientation=0
    mCurrentLayerStackRect=Rect(0, 0 - 400, 600)
    mCurrentDisplayRect=Rect(0, 0 - 400, 600)
    mCurrentSurface=null
    mPhysicalDisplayId=4619827551948147201
    mDisplayModeSpecs={baseModeId=2 allowGroupSwitching=false primaryRefreshRateRange=[0 Infinity] appRequestRefreshRateRange=[0 Infinity]}
    mDisplayModeSpecsInvalid=false
    mActiveModeId=2
    mActiveColorMode=0
    mDefaultModeId=2
    mUserPreferredModeId=-1
    mState=ON
    mCommittedState=ON
    mBrightnessState=0.4999999
    mBacklightAdapter=BacklightAdapter [useSurfaceControl=false (force_anyway? false), backlight=null]
    mAllmSupported=false
    mAllmRequested=false
    mGameContentTypeSupported=false
    mGameContentTypeRequested=false
    mStaticDisplayInfo=StaticDisplayInfo{isInternal=false, density=0.75, secure=true, deviceProductInfo=DeviceProductInfo{name=EMU_display_1, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, installOrientation=0}
    mSfDisplayModes=
      DisplayMode{id=0, width=400, height=600, xDpi=120.0, yDpi=120.0, refreshRate=160.0, appVsyncOffsetNanos=2000000, presentationDeadlineNanos=6250000, group=0}
    mActiveSfDisplayMode=DisplayMode{id=0, width=400, height=600, xDpi=120.0, yDpi=120.0, refreshRate=160.0, appVsyncOffsetNanos=2000000, presentationDeadlineNanos=6250000, group=0}
    mSupportedModes=
      DisplayModeRecord{mMode={id=2, width=400, height=600, fps=160.0, alternativeRefreshRates=[]}}
    mSupportedColorModes=[0]
    mDisplayDeviceConfig=DisplayDeviceConfig{mLoadedFrom=Static values, mBacklight=null, mNits=null, mRawBacklight=null, mRawNits=null, mInterpolationType=0, mBrightness=null, mBrightnessToBacklightSpline=MonotoneCubicSpline{[(0.0, 0.0: 1.0), (1.0, 1.0: 1.0)]}, mBacklightToBrightnessSpline=MonotoneCubicSpline{[(0.0, 0.0: 1.0), (1.0, 1.0: 1.0)]}, mNitsToBacklightSpline=null, mBacklightMinimum=0.0, mBacklightMaximum=1.0, mBrightnessDefault=0.5, mQuirks=null, isHbmEnabled=false, mHbmData=null, mSdrToHdrRatioSpline=null, mBrightnessThrottlingData=null, mOriginalBrightnessThrottlingData=null
    , mBrightnessRampFastDecrease=1.0, mBrightnessRampFastIncrease=1.0, mBrightnessRampSlowDecrease=1.0, mBrightnessRampSlowIncrease=1.0, mBrightnessRampDecreaseMaxMillis=0, mBrightnessRampIncreaseMaxMillis=0
    , mAmbientHorizonLong=10000, mAmbientHorizonShort=2000
    , mScreenDarkeningMinThreshold=0.0, mScreenDarkeningMinThresholdIdle=0.0, mScreenBrighteningMinThreshold=0.0, mScreenBrighteningMinThresholdIdle=0.0, mAmbientLuxDarkeningMinThreshold=0.0, mAmbientLuxDarkeningMinThresholdIdle=0.0, mAmbientLuxBrighteningMinThreshold=0.0, mAmbientLuxBrighteningMinThresholdIdle=0.0
    , mScreenBrighteningLevels=[0.0], mScreenBrighteningPercentages=[100.0], mScreenDarkeningLevels=[0.0], mScreenDarkeningPercentages=[200.0], mAmbientBrighteningLevels=[0.0], mAmbientBrighteningPercentages=[100.0], mAmbientDarkeningLevels=[0.0], mAmbientDarkeningPercentages=[200.0]
    , mAmbientBrighteningLevelsIdle=[0.0], mAmbientBrighteningPercentagesIdle=[100.0], mAmbientDarkeningLevelsIdle=[0.0], mAmbientDarkeningPercentagesIdle=[200.0], mScreenBrighteningLevelsIdle=[0.0], mScreenBrighteningPercentagesIdle=[100.0], mScreenDarkeningLevelsIdle=[0.0], mScreenDarkeningPercentagesIdle=[200.0]
    , mAmbientLightSensor=Sensor{type: , name: , refreshRateRange: [0.0, Infinity]} , mScreenOffBrightnessSensor=Sensor{type: null, name: null, refreshRateRange: [0.0, Infinity]} , mProximitySensor=Sensor{type: null, name: null, refreshRateRange: [0.0, Infinity]} , mRefreshRateLimitations= [], mDensityMapping= null, mAutoBrightnessBrighteningLightDebounce= -1, mAutoBrightnessDarkeningLightDebounce= -1, mBrightnessLevelsLux= null, mBrightnessLevelsNits= null, mDdcAutoBrightnessAvailable= true, mAutoBrightnessAvailable= true
    , mDefaultLowBlockingZoneRefreshRate= 60, mDefaultHighBlockingZoneRefreshRate= 0, mDefaultPeakRefreshRate= 0, mDefaultRefreshRate= 60, mDefaultRefreshRateInHbmHdr= 0, mDefaultRefreshRateInHbmSunlight= 0, mLowDisplayBrightnessThresholds= [], mLowAmbientBrightnessThresholds= [], mHighDisplayBrightnessThresholds= [], mHighAmbientBrightnessThresholds= []
    , mScreenOffBrightnessSensorValueToLux=null}
  DisplayDeviceInfo{"HDMI Screen": uniqueId="local:4619827124781842690", 3000 x 600, modeId 3, defaultModeId 3, supportedModes [{id=3, width=3000, height=600, fps=160.0, alternativeRefreshRates=[]}], colorMode 0, supportedColorModes [0], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, allmSupported false, gameContentTypeSupported false, density 120, 120.0 x 120.0 dpi, appVsyncOff 2000000, presDeadline 6250000, touch EXTERNAL, rotation 0, type EXTERNAL, address {port=2, model=0x401cec4b085601}, deviceProductInfo DeviceProductInfo{name=EMU_display_2, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, state ON, committedState ON, frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.5, FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_PRIVATE, FLAG_PRESENTATION, FLAG_OWN_CONTENT_ONLY, installOrientation 0}
    mAdapter=LocalDisplayAdapter
    mUniqueId=local:4619827124781842690
    mDisplayToken=android.os.BinderProxy@5a2dcbd
    mCurrentLayerStack=3
    mCurrentFlags=1
    mCurrentOrientation=0
    mCurrentLayerStackRect=Rect(0, 0 - 3000, 600)
    mCurrentDisplayRect=Rect(0, 0 - 3000, 600)
    mCurrentSurface=null
    mPhysicalDisplayId=4619827124781842690
    mDisplayModeSpecs={baseModeId=3 allowGroupSwitching=false primaryRefreshRateRange=[0 Infinity] appRequestRefreshRateRange=[0 Infinity]}
    mDisplayModeSpecsInvalid=false
    mActiveModeId=3
    mActiveColorMode=0
    mDefaultModeId=3
    mUserPreferredModeId=-1
    mState=ON
    mCommittedState=ON
    mBrightnessState=0.4999999
    mBacklightAdapter=BacklightAdapter [useSurfaceControl=false (force_anyway? false), backlight=null]
    mAllmSupported=false
    mAllmRequested=false
    mGameContentTypeSupported=false
    mGameContentTypeRequested=false
    mStaticDisplayInfo=StaticDisplayInfo{isInternal=false, density=0.75, secure=true, deviceProductInfo=DeviceProductInfo{name=EMU_display_2, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, installOrientation=0}
    mSfDisplayModes=
      DisplayMode{id=0, width=3000, height=600, xDpi=120.0, yDpi=120.0, refreshRate=160.0, appVsyncOffsetNanos=2000000, presentationDeadlineNanos=6250000, group=0}
    mActiveSfDisplayMode=DisplayMode{id=0, width=3000, height=600, xDpi=120.0, yDpi=120.0, refreshRate=160.0, appVsyncOffsetNanos=2000000, presentationDeadlineNanos=6250000, group=0}
    mSupportedModes=
      DisplayModeRecord{mMode={id=3, width=3000, height=600, fps=160.0, alternativeRefreshRates=[]}}
    mSupportedColorModes=[0]
    mDisplayDeviceConfig=DisplayDeviceConfig{mLoadedFrom=Static values, mBacklight=null, mNits=null, mRawBacklight=null, mRawNits=null, mInterpolationType=0, mBrightness=null, mBrightnessToBacklightSpline=MonotoneCubicSpline{[(0.0, 0.0: 1.0), (1.0, 1.0: 1.0)]}, mBacklightToBrightnessSpline=MonotoneCubicSpline{[(0.0, 0.0: 1.0), (1.0, 1.0: 1.0)]}, mNitsToBacklightSpline=null, mBacklightMinimum=0.0, mBacklightMaximum=1.0, mBrightnessDefault=0.5, mQuirks=null, isHbmEnabled=false, mHbmData=null, mSdrToHdrRatioSpline=null, mBrightnessThrottlingData=null, mOriginalBrightnessThrottlingData=null
    , mBrightnessRampFastDecrease=1.0, mBrightnessRampFastIncrease=1.0, mBrightnessRampSlowDecrease=1.0, mBrightnessRampSlowIncrease=1.0, mBrightnessRampDecreaseMaxMillis=0, mBrightnessRampIncreaseMaxMillis=0
    , mAmbientHorizonLong=10000, mAmbientHorizonShort=2000
    , mScreenDarkeningMinThreshold=0.0, mScreenDarkeningMinThresholdIdle=0.0, mScreenBrighteningMinThreshold=0.0, mScreenBrighteningMinThresholdIdle=0.0, mAmbientLuxDarkeningMinThreshold=0.0, mAmbientLuxDarkeningMinThresholdIdle=0.0, mAmbientLuxBrighteningMinThreshold=0.0, mAmbientLuxBrighteningMinThresholdIdle=0.0
    , mScreenBrighteningLevels=[0.0], mScreenBrighteningPercentages=[100.0], mScreenDarkeningLevels=[0.0], mScreenDarkeningPercentages=[200.0], mAmbientBrighteningLevels=[0.0], mAmbientBrighteningPercentages=[100.0], mAmbientDarkeningLevels=[0.0], mAmbientDarkeningPercentages=[200.0]
    , mAmbientBrighteningLevelsIdle=[0.0], mAmbientBrighteningPercentagesIdle=[100.0], mAmbientDarkeningLevelsIdle=[0.0], mAmbientDarkeningPercentagesIdle=[200.0], mScreenBrighteningLevelsIdle=[0.0], mScreenBrighteningPercentagesIdle=[100.0], mScreenDarkeningLevelsIdle=[0.0], mScreenDarkeningPercentagesIdle=[200.0]
    , mAmbientLightSensor=Sensor{type: , name: , refreshRateRange: [0.0, Infinity]} , mScreenOffBrightnessSensor=Sensor{type: null, name: null, refreshRateRange: [0.0, Infinity]} , mProximitySensor=Sensor{type: null, name: null, refreshRateRange: [0.0, Infinity]} , mRefreshRateLimitations= [], mDensityMapping= null, mAutoBrightnessBrighteningLightDebounce= -1, mAutoBrightnessDarkeningLightDebounce= -1, mBrightnessLevelsLux= null, mBrightnessLevelsNits= null, mDdcAutoBrightnessAvailable= true, mAutoBrightnessAvailable= true
    , mDefaultLowBlockingZoneRefreshRate= 60, mDefaultHighBlockingZoneRefreshRate= 0, mDefaultPeakRefreshRate= 0, mDefaultRefreshRate= 60, mDefaultRefreshRateInHbmHdr= 0, mDefaultRefreshRateInHbmSunlight= 0, mLowDisplayBrightnessThresholds= [], mLowAmbientBrightnessThresholds= [], mHighDisplayBrightnessThresholds= [], mHighAmbientBrightnessThresholds= []
    , mScreenOffBrightnessSensorValueToLux=null}
  DisplayDeviceInfo{"ClusterOsDouble-VD": uniqueId="virtual:com.android.car.cluster.osdouble:ClusterDisplay", 400 x 525, modeId 4, defaultModeId 4, supportedModes [{id=4, width=400, height=525, fps=60.0, alternativeRefreshRates=[]}], colorMode 0, supportedColorModes [0], hdrCapabilities null, allmSupported false, gameContentTypeSupported false, density 160, 160.0 x 160.0 dpi, appVsyncOff 0, presDeadline 16666666, touch NONE, rotation 0, type VIRTUAL, deviceProductInfo null, state ON, committedState UNKNOWN, owner com.android.car.cluster.osdouble (uid 1000), frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 0.0, brightnessDefault 0.0, FLAG_PRIVATE, FLAG_NEVER_BLANK, FLAG_OWN_CONTENT_ONLY, installOrientation 0}
    mAdapter=VirtualDisplayAdapter
    mUniqueId=virtual:com.android.car.cluster.osdouble:ClusterDisplay
    mDisplayToken=android.os.BinderProxy@cc7c2b2
    mCurrentLayerStack=4
    mCurrentFlags=0
    mCurrentOrientation=0
    mCurrentLayerStackRect=Rect(0, 0 - 400, 525)
    mCurrentDisplayRect=Rect(0, 0 - 400, 525)
    mCurrentSurface=Surface(name=null)/@0x60c5203
    mFlags=8
    mDisplayState=UNKNOWN
    mStopped=false
    mDisplayIdToMirror=0
    mWindowManagerMirroring=false
  DisplayDeviceInfo{"DistantDisplay-NavigationViewSurface-VD": uniqueId="virtual:com.android.systemui:DistantDisplay-NavigationViewSurface", 1500 x 588, modeId 5, defaultModeId 5, supportedModes [{id=5, width=1500, height=588, fps=60.0, alternativeRefreshRates=[]}], colorMode 0, supportedColorModes [0], hdrCapabilities null, allmSupported false, gameContentTypeSupported false, density 160, 160.0 x 160.0 dpi, appVsyncOff 0, presDeadline 16666666, touch VIRTUAL, rotation 0, type VIRTUAL, deviceProductInfo null, state ON, committedState UNKNOWN, owner com.android.systemui (uid 1010188), frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 0.0, brightnessDefault 0.0, FLAG_SECURE, FLAG_PRIVATE, FLAG_NEVER_BLANK, FLAG_OWN_CONTENT_ONLY, installOrientation 0}
    mAdapter=VirtualDisplayAdapter
    mUniqueId=virtual:com.android.systemui:DistantDisplay-NavigationViewSurface
    mDisplayToken=android.os.BinderProxy@58e0580
    mCurrentLayerStack=5
    mCurrentFlags=1
    mCurrentOrientation=0
    mCurrentLayerStackRect=Rect(0, 0 - 1500, 588)
    mCurrentDisplayRect=Rect(0, 0 - 1500, 588)
    mCurrentSurface=Surface(name=null)/@0x42587b9
    mFlags=1100
    mDisplayState=UNKNOWN
    mStopped=false
    mDisplayIdToMirror=0
    mWindowManagerMirroring=false
  DisplayDeviceInfo{"DistantDisplay-RootViewSurface-VD": uniqueId="virtual:com.android.systemui:DistantDisplay-RootViewSurface", 1500 x 588, modeId 6, defaultModeId 6, supportedModes [{id=6, width=1500, height=588, fps=60.0, alternativeRefreshRates=[]}], colorMode 0, supportedColorModes [0], hdrCapabilities null, allmSupported false, gameContentTypeSupported false, density 160, 160.0 x 160.0 dpi, appVsyncOff 0, presDeadline 16666666, touch VIRTUAL, rotation 0, type VIRTUAL, deviceProductInfo null, state ON, committedState UNKNOWN, owner com.android.systemui (uid 1010188), frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 0.0, brightnessDefault 0.0, FLAG_SECURE, FLAG_PRIVATE, FLAG_NEVER_BLANK, FLAG_OWN_CONTENT_ONLY, installOrientation 0}
    mAdapter=VirtualDisplayAdapter
    mUniqueId=virtual:com.android.systemui:DistantDisplay-RootViewSurface
    mDisplayToken=android.os.BinderProxy@56b57fe
    mCurrentLayerStack=6
    mCurrentFlags=1
    mCurrentOrientation=0
    mCurrentLayerStackRect=Rect(0, 0 - 1500, 588)
    mCurrentDisplayRect=Rect(0, 0 - 1500, 588)
    mCurrentSurface=Surface(name=null)/@0xff80b5f
    mFlags=1100
    mDisplayState=UNKNOWN
    mStopped=false
    mDisplayIdToMirror=0
    mWindowManagerMirroring=false

LogicalDisplayMapper:
  mSingleDisplayDemoMode=false
  mCurrentLayout=[{addr: {port=0, model=0x401cec6a7a2b7b}, dispId: 0(ON)}]
  mDeviceStatesOnWhichToWakeUp={}
  mDeviceStatesOnWhichToSleep={}
  mInteractive=true
  mBootCompleted=true
  
  mDeviceState=0
  mPendingDeviceState=-1
  mDeviceStateToBeAppliedAfterBoot=-1
  
  Logical Displays: size=6
  Display 0:
    mDisplayId=0
    mIsEnabled=true
    mIsInTransition=false
    mLayerStack=0
    mHasContent=true
    mDesiredDisplayModeSpecs={baseModeId=1 allowGroupSwitching=false primaryRefreshRateRange=[0 60] appRequestRefreshRateRange=[0 Infinity]}
    mRequestedColorMode=0
    mDisplayOffset=(0, 0)
    mDisplayScalingDisabled=false
    mPrimaryDisplayDevice=Built-in Screen
    mBaseDisplayInfo=DisplayInfo{"Built-in Screen", displayId 0", displayGroupId 0, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_TRUSTED, real 1080 x 600, largest app 1080 x 600, smallest app 1080 x 600, appVsyncOff 1000000, presDeadline 16666666, mode 1, defaultMode 1, modes [{id=1, width=1080, height=600, fps=60.000004, alternativeRefreshRates=[]}], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state ON, committedState ON, type INTERNAL, uniqueId "local:4619827259835644672", app 1080 x 600, density 120 (120.0 x 120.0) dpi, layerStack 0, colorMode 0, supportedColorModes [0], address {port=0, model=0x401cec6a7a2b7b}, deviceProductInfo DeviceProductInfo{name=EMU_display_0, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, removeMode 0, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.39763778, installOrientation ROTATION_0}
    mOverrideDisplayInfo=DisplayInfo{"Built-in Screen", displayId 0", displayGroupId 0, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_TRUSTED, real 1080 x 600, largest app 1080 x 951, smallest app 600 x 471, appVsyncOff 1000000, presDeadline 16666666, mode 1, defaultMode 1, modes [{id=1, width=1080, height=600, fps=60.000004, alternativeRefreshRates=[]}], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state ON, committedState ON, type INTERNAL, uniqueId "local:4619827259835644672", app 1080 x 528, density 120 (120.0 x 120.0) dpi, layerStack 0, colorMode 0, supportedColorModes [0], address {port=0, model=0x401cec6a7a2b7b}, deviceProductInfo DeviceProductInfo{name=EMU_display_0, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, removeMode 0, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.39763778, installOrientation ROTATION_0}
    mRequestedMinimalPostProcessing=false
    mFrameRateOverrides=[]
    mPendingFrameRateOverrideUids={}
  
  Display 2:
    mDisplayId=2
    mIsEnabled=true
    mIsInTransition=false
    mLayerStack=2
    mHasContent=true
    mDesiredDisplayModeSpecs={baseModeId=2 allowGroupSwitching=false primaryRefreshRateRange=[0 Infinity] appRequestRefreshRateRange=[0 Infinity]}
    mRequestedColorMode=0
    mDisplayOffset=(0, 0)
    mDisplayScalingDisabled=false
    mPrimaryDisplayDevice=HDMI Screen
    mBaseDisplayInfo=DisplayInfo{"HDMI Screen", displayId 2", displayGroupId 0, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_PRIVATE, FLAG_PRESENTATION, FLAG_TRUSTED, real 400 x 600, largest app 400 x 600, smallest app 400 x 600, appVsyncOff 2000000, presDeadline 6250000, mode 2, defaultMode 2, modes [{id=2, width=400, height=600, fps=160.0, alternativeRefreshRates=[]}], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state ON, committedState ON, type EXTERNAL, uniqueId "local:4619827551948147201", app 400 x 600, density 120 (120.0 x 120.0) dpi, layerStack 2, colorMode 0, supportedColorModes [0], address {port=1, model=0x401cecae7d6e8a}, deviceProductInfo DeviceProductInfo{name=EMU_display_1, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, removeMode 1, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.5, installOrientation ROTATION_0}
    mOverrideDisplayInfo=DisplayInfo{"HDMI Screen", displayId 2", displayGroupId 0, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_PRIVATE, FLAG_PRESENTATION, FLAG_TRUSTED, real 400 x 600, largest app 600 x 600, smallest app 400 x 400, appVsyncOff 2000000, presDeadline 6250000, mode 2, defaultMode 2, modes [{id=2, width=400, height=600, fps=160.0, alternativeRefreshRates=[]}], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state UNKNOWN, committedState UNKNOWN, type EXTERNAL, uniqueId "local:4619827551948147201", app 400 x 600, density 120 (120.0 x 120.0) dpi, layerStack 2, colorMode 0, supportedColorModes [0], address {port=1, model=0x401cecae7d6e8a}, deviceProductInfo DeviceProductInfo{name=EMU_display_1, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, removeMode 1, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.5, installOrientation ROTATION_0}
    mRequestedMinimalPostProcessing=false
    mFrameRateOverrides=[]
    mPendingFrameRateOverrideUids={}
  
  Display 3:
    mDisplayId=3
    mIsEnabled=true
    mIsInTransition=false
    mLayerStack=3
    mHasContent=true
    mDesiredDisplayModeSpecs={baseModeId=3 allowGroupSwitching=false primaryRefreshRateRange=[0 Infinity] appRequestRefreshRateRange=[0 Infinity]}
    mRequestedColorMode=0
    mDisplayOffset=(0, 0)
    mDisplayScalingDisabled=false
    mPrimaryDisplayDevice=HDMI Screen
    mBaseDisplayInfo=DisplayInfo{"HDMI Screen", displayId 3", displayGroupId 0, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_PRIVATE, FLAG_PRESENTATION, FLAG_TRUSTED, real 3000 x 600, largest app 3000 x 600, smallest app 3000 x 600, appVsyncOff 2000000, presDeadline 6250000, mode 3, defaultMode 3, modes [{id=3, width=3000, height=600, fps=160.0, alternativeRefreshRates=[]}], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state ON, committedState ON, type EXTERNAL, uniqueId "local:4619827124781842690", app 3000 x 600, density 120 (120.0 x 120.0) dpi, layerStack 3, colorMode 0, supportedColorModes [0], address {port=2, model=0x401cec4b085601}, deviceProductInfo DeviceProductInfo{name=EMU_display_2, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, removeMode 1, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.5, installOrientation ROTATION_0}
    mOverrideDisplayInfo=DisplayInfo{"HDMI Screen", displayId 3", displayGroupId 0, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_PRIVATE, FLAG_PRESENTATION, FLAG_TRUSTED, real 3000 x 600, largest app 3000 x 3000, smallest app 600 x 600, appVsyncOff 2000000, presDeadline 6250000, mode 3, defaultMode 3, modes [{id=3, width=3000, height=600, fps=160.0, alternativeRefreshRates=[]}], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state UNKNOWN, committedState UNKNOWN, type EXTERNAL, uniqueId "local:4619827124781842690", app 3000 x 600, density 120 (120.0 x 120.0) dpi, layerStack 3, colorMode 0, supportedColorModes [0], address {port=2, model=0x401cec4b085601}, deviceProductInfo DeviceProductInfo{name=EMU_display_2, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, removeMode 1, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.5, installOrientation ROTATION_0}
    mRequestedMinimalPostProcessing=false
    mFrameRateOverrides=[]
    mPendingFrameRateOverrideUids={}
  
  Display 4:
    mDisplayId=4
    mIsEnabled=true
    mIsInTransition=false
    mLayerStack=4
    mHasContent=true
    mDesiredDisplayModeSpecs={baseModeId=4 allowGroupSwitching=false primaryRefreshRateRange=[0 60] appRequestRefreshRateRange=[0 Infinity]}
    mRequestedColorMode=0
    mDisplayOffset=(0, 0)
    mDisplayScalingDisabled=false
    mPrimaryDisplayDevice=ClusterOsDouble-VD
    mBaseDisplayInfo=DisplayInfo{"ClusterOsDouble-VD", displayId 4", displayGroupId 0, FLAG_PRIVATE, real 400 x 525, largest app 400 x 525, smallest app 400 x 525, appVsyncOff 0, presDeadline 16666666, mode 4, defaultMode 4, modes [{id=4, width=400, height=525, fps=60.0, alternativeRefreshRates=[]}], hdrCapabilities null, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state ON, committedState UNKNOWN, type VIRTUAL, uniqueId "virtual:com.android.car.cluster.osdouble:ClusterDisplay", app 400 x 525, density 160 (160.0 x 160.0) dpi, layerStack 4, colorMode 0, supportedColorModes [0], deviceProductInfo null, owner com.android.car.cluster.osdouble (uid 1000), removeMode 1, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 0.0, brightnessDefault 0.0, installOrientation ROTATION_0}
    mOverrideDisplayInfo=DisplayInfo{"ClusterOsDouble-VD", displayId 4", displayGroupId 0, FLAG_PRIVATE, real 400 x 525, largest app 525 x 525, smallest app 400 x 400, appVsyncOff 0, presDeadline 16666666, mode 4, defaultMode 4, modes [{id=4, width=400, height=525, fps=60.0, alternativeRefreshRates=[]}], hdrCapabilities null, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state ON, committedState UNKNOWN, type VIRTUAL, uniqueId "virtual:com.android.car.cluster.osdouble:ClusterDisplay", app 400 x 525, density 160 (160.0 x 160.0) dpi, layerStack 4, colorMode 0, supportedColorModes [0], deviceProductInfo null, owner com.android.car.cluster.osdouble (uid 1000), removeMode 1, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 0.0, brightnessDefault 0.0, installOrientation ROTATION_0}
    mRequestedMinimalPostProcessing=false
    mFrameRateOverrides=[]
    mPendingFrameRateOverrideUids={}
  
  Display 5:
    mDisplayId=5
    mIsEnabled=true
    mIsInTransition=false
    mLayerStack=5
    mHasContent=true
    mDesiredDisplayModeSpecs={baseModeId=5 allowGroupSwitching=false primaryRefreshRateRange=[0 60] appRequestRefreshRateRange=[0 Infinity]}
    mRequestedColorMode=0
    mDisplayOffset=(0, 0)
    mDisplayScalingDisabled=false
    mPrimaryDisplayDevice=DistantDisplay-NavigationViewSurface-VD
    mBaseDisplayInfo=DisplayInfo{"DistantDisplay-NavigationViewSurface-VD", displayId 5", displayGroupId 0, FLAG_SECURE, FLAG_PRIVATE, FLAG_TRUSTED, real 1500 x 588, largest app 1500 x 588, smallest app 1500 x 588, appVsyncOff 0, presDeadline 16666666, mode 5, defaultMode 5, modes [{id=5, width=1500, height=588, fps=60.0, alternativeRefreshRates=[]}], hdrCapabilities null, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state ON, committedState UNKNOWN, type VIRTUAL, uniqueId "virtual:com.android.systemui:DistantDisplay-NavigationViewSurface", app 1500 x 588, density 160 (160.0 x 160.0) dpi, layerStack 5, colorMode 0, supportedColorModes [0], deviceProductInfo null, owner com.android.systemui (uid 1010188), removeMode 1, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 0.0, brightnessDefault 0.0, installOrientation ROTATION_0}
    mOverrideDisplayInfo=DisplayInfo{"DistantDisplay-NavigationViewSurface-VD", displayId 5", displayGroupId 0, FLAG_SECURE, FLAG_PRIVATE, FLAG_TRUSTED, real 1500 x 588, largest app 1500 x 1500, smallest app 588 x 588, appVsyncOff 0, presDeadline 16666666, mode 5, defaultMode 5, modes [{id=5, width=1500, height=588, fps=60.0, alternativeRefreshRates=[]}], hdrCapabilities null, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state ON, committedState UNKNOWN, type VIRTUAL, uniqueId "virtual:com.android.systemui:DistantDisplay-NavigationViewSurface", app 1500 x 588, density 160 (160.0 x 160.0) dpi, layerStack 5, colorMode 0, supportedColorModes [0], deviceProductInfo null, owner com.android.systemui (uid 1010188), removeMode 1, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 0.0, brightnessDefault 0.0, installOrientation ROTATION_0}
    mRequestedMinimalPostProcessing=false
    mFrameRateOverrides=[]
    mPendingFrameRateOverrideUids={}
  
  Display 6:
    mDisplayId=6
    mIsEnabled=true
    mIsInTransition=false
    mLayerStack=6
    mHasContent=true
    mDesiredDisplayModeSpecs={baseModeId=6 allowGroupSwitching=false primaryRefreshRateRange=[0 60] appRequestRefreshRateRange=[0 Infinity]}
    mRequestedColorMode=0
    mDisplayOffset=(0, 0)
    mDisplayScalingDisabled=false
    mPrimaryDisplayDevice=DistantDisplay-RootViewSurface-VD
    mBaseDisplayInfo=DisplayInfo{"DistantDisplay-RootViewSurface-VD", displayId 6", displayGroupId 0, FLAG_SECURE, FLAG_PRIVATE, FLAG_TRUSTED, real 1500 x 588, largest app 1500 x 588, smallest app 1500 x 588, appVsyncOff 0, presDeadline 16666666, mode 6, defaultMode 6, modes [{id=6, width=1500, height=588, fps=60.0, alternativeRefreshRates=[]}], hdrCapabilities null, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state ON, committedState UNKNOWN, type VIRTUAL, uniqueId "virtual:com.android.systemui:DistantDisplay-RootViewSurface", app 1500 x 588, density 160 (160.0 x 160.0) dpi, layerStack 6, colorMode 0, supportedColorModes [0], deviceProductInfo null, owner com.android.systemui (uid 1010188), removeMode 1, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 0.0, brightnessDefault 0.0, installOrientation ROTATION_0}
    mOverrideDisplayInfo=DisplayInfo{"DistantDisplay-RootViewSurface-VD", displayId 6", displayGroupId 0, FLAG_SECURE, FLAG_PRIVATE, FLAG_TRUSTED, real 1500 x 588, largest app 1500 x 1500, smallest app 588 x 588, appVsyncOff 0, presDeadline 16666666, mode 6, defaultMode 6, modes [{id=6, width=1500, height=588, fps=60.0, alternativeRefreshRates=[]}], hdrCapabilities null, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state ON, committedState UNKNOWN, type VIRTUAL, uniqueId "virtual:com.android.systemui:DistantDisplay-RootViewSurface", app 1500 x 588, density 160 (160.0 x 160.0) dpi, layerStack 6, colorMode 0, supportedColorModes [0], deviceProductInfo null, owner com.android.systemui (uid 1010188), removeMode 1, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 0.0, brightnessDefault 0.0, installOrientation ROTATION_0}
    mRequestedMinimalPostProcessing=false
    mFrameRateOverrides=[]
    mPendingFrameRateOverrideUids={}
  
  DeviceStateToLayoutMap:
    Registered Layouts:
    state(-1): [{addr: {port=0, model=0x401cec6a7a2b7b}, dispId: 0(ON)}]
"""