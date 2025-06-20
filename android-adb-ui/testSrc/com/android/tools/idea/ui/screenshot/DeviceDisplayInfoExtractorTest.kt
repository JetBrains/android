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
package com.android.tools.idea.ui.screenshot

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceDisplayInfoExtractorTest {

  // Regression test for b/424085995
  @Test
  fun `extracts FLAG_ROUND from dump sys`() {
    val extractor = DeviceDisplayInfoExtractor(displayId = 0)

    val deviceDisplayInfo = extractor.extractFromDumpSys(flagRoundDumpSysOutput)

    assertThat(deviceDisplayInfo).isNotEmpty()
    assertThat(deviceDisplayInfo).contains("FLAG_ROUND")
  }

  // Regression test for b/424085995
  @Test
  fun `handles multiple displays`() {
    val uniqueId1 = "local:4619827259835644672"
    val uniqueId2 = "local:4619827551948147201"
    run {
      val extractor = DeviceDisplayInfoExtractor(displayId = 0)

      val deviceDisplayInfo = extractor.extractFromDumpSys(multiDisplayDumpSysOutput)

      assertThat(deviceDisplayInfo).contains("uniqueId=\"$uniqueId1\"")
      assertThat(deviceDisplayInfo).doesNotContain("uniqueId=\"$uniqueId2\"")
    }

    run {
      val extractor = DeviceDisplayInfoExtractor(displayId = 2)

      val deviceDisplayInfo = extractor.extractFromDumpSys(multiDisplayDumpSysOutput)

      assertThat(deviceDisplayInfo).contains("uniqueId=\"$uniqueId2\"")
      assertThat(deviceDisplayInfo).doesNotContain("uniqueId=\"$uniqueId1\"")
    }
  }
}

private const val multiDisplayDumpSysOutput = """
  DisplayDeviceInfo{"Built-in Screen": uniqueId="local:4619827259835644672", 1080 x 600, modeId 1, defaultModeId 1, supportedModes [{id=1, width=1080, height=600, fps=60.000004, alternativeRefreshRates=[]}], colorMode 0, supportedColorModes [0], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, allmSupported false, gameContentTypeSupported false, density 120, 120.0 x 120.0 dpi, appVsyncOff 1000000, presDeadline 16666666, touch INTERNAL, rotation 0, type INTERNAL, address {port=0, model=0x401cec6a7a2b7b}, deviceProductInfo DeviceProductInfo{name=EMU_display_0, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, state ON, committedState ON, frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.39763778, FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_ROTATES_WITH_CONTENT, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, installOrientation 0}
    mCurrentLayerStack=0
    mPhysicalDisplayId=4619827259835644672

  DisplayDeviceInfo{"HDMI Screen": uniqueId="local:4619827551948147201", 400 x 600, modeId 2, defaultModeId 2, supportedModes [{id=2, width=400, height=600, fps=160.0, alternativeRefreshRates=[]}], colorMode 0, supportedColorModes [0], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, allmSupported false, gameContentTypeSupported false, density 120, 120.0 x 120.0 dpi, appVsyncOff 2000000, presDeadline 6250000, touch EXTERNAL, rotation 0, type EXTERNAL, address {port=1, model=0x401cecae7d6e8a}, deviceProductInfo DeviceProductInfo{name=EMU_display_1, manufacturerPnpId=GGL, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, state ON, committedState ON, frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.5, FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_PRIVATE, FLAG_PRESENTATION, FLAG_OWN_CONTENT_ONLY, installOrientation 0}
    mCurrentLayerStack=2
    mPhysicalDisplayId=4619827551948147201
"""

/** Example of a dumpsys output from a real round device. */
private const val flagRoundDumpSysOutput =
  """
DISPLAY MANAGER (dumpsys display)
  mOnlyCode=false
  mSafeMode=false
  mPendingTraversal=false
  mViewports=[DisplayViewport{type=INTERNAL, valid=true, isActive=true, displayId=0, uniqueId='local:4630946526965601921', physicalPort=129, orientation=0, logicalFrame=Rect(0, 0 - 384, 384), physicalFrame=Rect(0, 0 - 384, 384), deviceWidth=384, deviceHeight=384}]
  mDefaultDisplayDefaultColorMode=0
  mWifiDisplayScanRequestCount=0
  mStableDisplaySize=Point(384, 384)
  mMinimumBrightnessCurve=[(0.0, 0.0), (2000.0, 50.0), (4000.0, 90.0)]


Display States: size=1
  Display Id=0
  Display State=ON
  Display Brightness=0.027559057
  Display SdrBrightness=0.027559057

Display Adapters: size=3
  LocalDisplayAdapter
  VirtualDisplayAdapter
  OverlayDisplayAdapter
    mCurrentOverlaySetting=
    mOverlays: size=0

Display Devices: size=1
  DisplayDeviceInfo{"Built-in Screen": uniqueId="local:4630946526965601921", 384 x 384, modeId 1, defaultModeId 1, supportedModes [{id=1, width=384, height=384, fps=60.000004, alternativeRefreshRates=[]}], colorMode 0, supportedColorModes [0], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, allmSupported false, gameContentTypeSupported false, density 320, 325.12 x 325.12 dpi, appVsyncOff 1000000, presDeadline 16666666, touch INTERNAL, rotation 0, type INTERNAL, address {port=129, model=0x40446d54999d16}, deviceProductInfo DeviceProductInfo{name=, manufacturerPnpId=QCM, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, state ON, frameRateOverride , brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.7992126, roundedCorners RoundedCorners{[RoundedCorner{position=TopLeft, radius=192, center=Point(192, 192)}, RoundedCorner{position=TopRight, radius=192, center=Point(192, 192)}, RoundedCorner{position=BottomRight, radius=192, center=Point(192, 192)}, RoundedCorner{position=BottomLeft, radius=192, center=Point(192, 192)}]}, FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_ROTATES_WITH_CONTENT, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_ROUND, installOrientation 0}
    mAdapter=LocalDisplayAdapter
    mUniqueId=local:4630946526965601921
    mDisplayToken=android.os.BinderProxy@e4450b9
    mCurrentLayerStack=0
    mCurrentFlags=1
    mCurrentOrientation=0
    mCurrentLayerStackRect=Rect(0, 0 - 384, 384)
    mCurrentDisplayRect=Rect(0, 0 - 384, 384)
    mCurrentSurface=null
    mPhysicalDisplayId=4630946526965601921
    mDisplayModeSpecs={baseModeId=1 allowGroupSwitching=false primaryRefreshRateRange=[0 60] appRequestRefreshRateRange=[0 Infinity]}
    mDisplayModeSpecsInvalid=false
    mActiveModeId=1
    mActiveColorMode=0
    mDefaultModeId=1
    mUserPreferredModeId=-1
    mState=ON
    mBrightnessState=0.027559057
    mBacklightAdapter=BacklightAdapter [useSurfaceControl=true (force_anyway? false), backlight=null]
    mAllmSupported=false
    mAllmRequested=false
    mGameContentTypeSupported=false
    mGameContentTypeRequested=false
    mStaticDisplayInfo=StaticDisplayInfo{isInternal=true, density=2.0, secure=true, deviceProductInfo=DeviceProductInfo{name=, manufacturerPnpId=QCM, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, installOrientation=0}
    mSfDisplayModes=
      DisplayMode{id=0, width=384, height=384, xDpi=325.12, yDpi=325.12, refreshRate=60.000004, appVsyncOffsetNanos=1000000, presentationDeadlineNanos=16666666, group=0}
    mActiveSfDisplayMode=DisplayMode{id=0, width=384, height=384, xDpi=325.12, yDpi=325.12, refreshRate=60.000004, appVsyncOffsetNanos=1000000, presentationDeadlineNanos=16666666, group=0}
    mSupportedModes=
      DisplayModeRecord{mMode={id=1, width=384, height=384, fps=60.000004, alternativeRefreshRates=[]}}
    mSupportedColorModes=[0]
    mDisplayDeviceConfig=DisplayDeviceConfig{mLoadedFrom=<config.xml>, mBacklight=null, mNits=null, mRawBacklight=null, mRawNits=null, mInterpolationType=0, mBrightness=null, mBrightnessToBacklightSpline=MonotoneCubicSpline{[(0.0, 0.0: 1.0), (1.0, 1.0: 1.0)]}, mBacklightToBrightnessSpline=MonotoneCubicSpline{[(0.0, 0.0: 1.0), (1.0, 1.0: 1.0)]}, mNitsToBacklightSpline=null, mBacklightMinimum=0.003937008, mBacklightMaximum=1.0, mBrightnessDefault=0.7992126, mQuirks=null, isHbmEnabled=false, mHbmData=null, mSdrToHdrRatioSpline=null, mBrightnessThrottlingData=null, mBrightnessRampFastDecrease=1.1764706, mBrightnessRampFastIncrease=1.1764706, mBrightnessRampSlowDecrease=0.7058824, mBrightnessRampSlowIncrease=0.7058824, mBrightnessRampDecreaseMaxMillis=0, mBrightnessRampIncreaseMaxMillis=0, mAmbientHorizonLong=10000, mAmbientHorizonShort=2000, mScreenDarkeningMinThreshold=0.0, mScreenBrighteningMinThreshold=0.0, mAmbientLuxDarkeningMinThreshold=0.0, mAmbientLuxBrighteningMinThreshold=0.0, mAmbientLightSensor=Sensor{type: , name: , refreshRateRange: [0.0, Infinity]} , mProximitySensor=Sensor{type: , name: , refreshRateRange: [0.0, Infinity]} , mRefreshRateLimitations= [], mDensityMapping= null}

LogicalDisplayMapper:
  mSingleDisplayDemoMode=false
  mCurrentLayout=[{addr: {port=129, model=0x40446d54999d16}, dispId: 0(ON)}]
  mDeviceStatesOnWhichToWakeUp={}
  mDeviceStatesOnWhichToSleep={}
  mInteractive=true

  Logical Displays: size=1
  Display 0:
    mDisplayId=0
    mPhase=1
    mLayerStack=0
    mHasContent=true
    mDesiredDisplayModeSpecs={baseModeId=1 allowGroupSwitching=false primaryRefreshRateRange=[0 60] appRequestRefreshRateRange=[0 Infinity]}
    mRequestedColorMode=0
    mDisplayOffset=(0, 0)
    mDisplayScalingDisabled=false
    mPrimaryDisplayDevice=Built-in Screen
    mBaseDisplayInfo=DisplayInfo{"Built-in Screen", displayId 0", displayGroupId 0, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_ROUND, FLAG_TRUSTED, real 384 x 384, largest app 384 x 384, smallest app 384 x 384, appVsyncOff 1000000, presDeadline 16666666, mode 1, defaultMode 1, modes [{id=1, width=384, height=384, fps=60.000004, alternativeRefreshRates=[]}], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state ON, type INTERNAL, uniqueId "local:4630946526965601921", app 384 x 384, density 320 (325.12 x 325.12) dpi, layerStack 0, colorMode 0, supportedColorModes [0], address {port=129, model=0x40446d54999d16}, deviceProductInfo DeviceProductInfo{name=, manufacturerPnpId=QCM, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, removeMode 0, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.7992126, installOrientation ROTATION_0}
    mOverrideDisplayInfo=DisplayInfo{"Built-in Screen", displayId 0", displayGroupId 0, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_ROUND, FLAG_TRUSTED, real 384 x 384, largest app 384 x 384, smallest app 384 x 384, appVsyncOff 1000000, presDeadline 16666666, mode 1, defaultMode 1, modes [{id=1, width=384, height=384, fps=60.000004, alternativeRefreshRates=[]}], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0, mMaxAverageLuminance=500.0, mMinLuminance=0.0}, userDisabledHdrTypes [], minimalPostProcessingSupported false, rotation 0, state ON, type INTERNAL, uniqueId "local:4630946526965601921", app 384 x 384, density 320 (325.12 x 325.12) dpi, layerStack 0, colorMode 0, supportedColorModes [0], address {port=129, model=0x40446d54999d16}, deviceProductInfo DeviceProductInfo{name=, manufacturerPnpId=QCM, productId=1, modelYear=null, manufactureDate=ManufactureDate{week=27, year=2006}, connectionToSinkType=0}, removeMode 0, refreshRateOverride 0.0, brightnessMinimum 0.0, brightnessMaximum 1.0, brightnessDefault 0.7992126, installOrientation ROTATION_0}
    mRequestedMinimalPostProcessing=false
    mFrameRateOverrides=[]
    mPendingFrameRateOverrideUids={}

  DeviceStateToLayoutMap:
    Registered Layouts:
    state(-1): [{addr: {port=129, model=0x40446d54999d16}, dispId: 0(ON)}]

Callbacks: size=9
  0: mPid=1511, mWifiDisplayScanRequested=false
  1: mPid=1765, mWifiDisplayScanRequested=false
  2: mPid=1848, mWifiDisplayScanRequested=false
  3: mPid=1922, mWifiDisplayScanRequested=false
  4: mPid=2150, mWifiDisplayScanRequested=false
  5: mPid=2181, mWifiDisplayScanRequested=false
  6: mPid=2539, mWifiDisplayScanRequested=false
  7: mPid=3124, mWifiDisplayScanRequested=false
  8: mPid=10830, mWifiDisplayScanRequested=false

Display Power Controllers: size=1

Display Power Controller:
  mDisplayId=0
  mLightSensor={Sensor name="TCS3701 light sensor", vendor="AMS", version=1, type=5, maxRange=1000000.0, resolution=0.01, power=0.001, minDelay=0}

Display Power Controller Locked State:
  mDisplayReadyLocked=true
  mPendingRequestLocked=policy=DIM, useProximitySensor=false, screenBrightnessOverride=NaN, useAutoBrightness=true, screenAutoBrightnessAdjustmentOverride=NaN, screenLowPowerBrightnessFactor=1.0, blockScreenOn=false, lowPowerMode=false, boostScreenBrightness=false, dozeScreenBrightness=NaN, dozeScreenState=UNKNOWN
  mPendingRequestChangedLocked=false
  mPendingWaitForNegativeProximityLocked=false
  mPendingUpdatePowerStateLocked=false

Display Power Controller Configuration:
  mScreenBrightnessRangeDefault=0.7992126
  mScreenBrightnessDozeConfig=0.235
  mScreenBrightnessDimConfig=0.235
  mScreenBrightnessForVrRangeMinimum=0.307087
  mScreenBrightnessForVrRangeMaximum=1.0
  mScreenBrightnessForVrDefault=0.33464
  mAdaptiveBrightnessMode=2
  mUseSoftwareAutoBrightnessConfig=true
  mAllowAutoBrightnessWhileDozingConfig=true
  mSkipScreenOnBrightnessRamp=true
  mColorFadeFadesConfig=false
  mColorFadeEnabled=false
  mCachedBrightnessInfo.brightness=0.15782775
  mCachedBrightnessInfo.adjustedBrightness=0.027559055
  mCachedBrightnessInfo.brightnessMin=0.0
  mCachedBrightnessInfo.brightnessMax=1.0
  mCachedBrightnessInfo.hbmMode=0
  mCachedBrightnessInfo.hbmTransitionPoint=Infinity
  mCachedBrightnessInfo.brightnessMaxReason =0
  mRampEnabledWhenColorFadeOff=false
  mDisplayBlanksAfterDozeConfig=false
  mBrightnessBucketsInDozeConfig=false

Display Power Controller Thread State:
  mPowerRequest=policy=DIM, useProximitySensor=false, screenBrightnessOverride=NaN, useAutoBrightness=true, screenAutoBrightnessAdjustmentOverride=NaN, screenLowPowerBrightnessFactor=1.0, blockScreenOn=false, lowPowerMode=false, boostScreenBrightness=false, dozeScreenBrightness=NaN, dozeScreenState=UNKNOWN
  mUnfinishedBusiness=false
  mWaitingForNegativeProximity=false
  mProximitySensor=null
  mProximitySensorEnabled=false
  mProximityThreshold=0.0
  mProximity=Unknown
  mPendingProximity=Unknown
  mPendingProximityDebounceTime=-1 (6274046 ms ago)
  mScreenOffBecauseOfProximity=false
  mLastUserSetScreenBrightness=NaN
  mPendingScreenBrightnessSetting=NaN
  mTemporaryScreenBrightness=NaN
  mAutoBrightnessAdjustment=0.0
  mBrightnessReason=automatic [ dim ]
  mTemporaryAutoBrightnessAdjustment=NaN
  mPendingAutoBrightnessAdjustment=NaN
  mScreenBrightnessForVrFloat=0.33464
  mAppliedAutoBrightness=true
  mAppliedDimming=true
  mAppliedLowPower=false
  mAppliedThrottling=false
  mAppliedScreenBrightnessOverride=false
  mAppliedTemporaryBrightness=false
  mAppliedTemporaryAutoBrightnessAdjustment=false
  mAppliedBrightnessBoost=false
  mDozing=false
  mSkipRampState=RAMP_STATE_SKIP_NONE
  mScreenOnBlockStartRealTime=4012916
  mScreenOffBlockStartRealTime=4011004
  mPendingScreenOnUnblocker=null
  mPendingScreenOffUnblocker=null
  mPendingScreenOff=false
  mReportedToPolicy=REPORTED_TO_POLICY_SCREEN_ON
  mIsRbcActive=false
  mOnStateChangePending=false
  mOnProximityPositiveMessages=0
  mOnProximityNegativeMessages=0
  mScreenBrightnessRampAnimator.isAnimating()=false

Display Power State:
  mStopped=false
  mScreenState=ON
  mScreenBrightness=0.027559057
  mSdrScreenBrightness=0.027559057
  mScreenReady=true
  mScreenUpdatePending=false
  mColorFadePrepared=false
  mColorFadeLevel=1.0
  mColorFadeReady=true
  mColorFadeDrawPending=false

Photonic Modulator State:
  mPendingState=ON
  mPendingBacklight=0.027559057
  mPendingSdrBacklight=0.027559057
  mActualState=ON
  mActualBacklight=0.027559057
  mActualSdrBacklight=0.027559057
  mStateChangeInProgress=false
  mBacklightChangeInProgress=false

Automatic Brightness Controller Configuration:
  mState=AUTO_BRIGHTNESS_ENABLED
  mScreenBrightnessRangeMinimum=0.0
  mScreenBrightnessRangeMaximum=1.0
  mDozeScaleFactor=1.0
  mInitialLightSensorRate=200
  mNormalLightSensorRate=200
  mLightSensorWarmUpTimeConfig=0
  mBrighteningLightDebounceConfig=0
  mDarkeningLightDebounceConfig=30000
  mResetAmbientLuxAfterWarmUpConfig=true
  mAmbientLightHorizonLong=10000
  mAmbientLightHorizonShort=2000
  mWeightingIntercept=10000

Automatic Brightness Controller State:
  mLightSensor={Sensor name="TCS3701 light sensor", vendor="AMS", version=1, type=5, maxRange=1000000.0, resolution=0.01, power=0.001, minDelay=0}
  mLightSensorEnabled=true
  mLightSensorEnableTime=4012937 (2261109 ms ago)
  mCurrentLightSensorRate=200
  mAmbientLux=74.5775
  mAmbientLuxValid=true
  mPreThesholdLux=NaN
  mPreThesholdBrightness=NaN
  mScreenBrighteningThreshold=0.0
  mScreenDarkeningThreshold=0.0
  mBrighteningLuxThreshold=108.28653
  mDarkeningLuxThreshold=48.77369
  mLastObservedLux=37.032497
  mLastObservedLuxTime=6272235 (1811 ms ago)
  mRecentLightSamples=3540
  mAmbientLightRingBuffer=[16.27375 / 238ms, 33.0625 / 588ms, 62.56125 / 191ms, 22.88625 / 383ms, 53.69875 / 968ms, 28.039999 / 1171ms, 58.715 / 381ms, 35.05125 / 200ms, 53.8225 / 386ms, 25.012499 / 194ms, 50.66375 / 590ms, 7.4112496 / 178ms, 33.07 / 388ms, 14.023749 / 782ms, 48.806248 / 203ms, 34.927498 / 378ms, 9.131249 / 387ms, 41.925 / 377ms, 18.262499 / 207ms, 37.032497 / 1811ms]
  mScreenAutoBrightness=0.15782775
  mEyeAdjustedScreenAutoBrightness=NaN
  mDozeScreenAutoBrightness=0.027559055
  mDisplayPolicy=DIM
  mShortTermModelTimeout(active)=10000
  mShortTermModelAnchor=-1.0
  mShortTermModelValid=true
  mBrightnessAdjustmentSamplePending=false
  mBrightnessAdjustmentSampleOldLux=0.0
  mBrightnessAdjustmentSampleOldBrightness=0.0
  mForegroundAppPackageName=com.google.android.wearable.sysui
  mPendingForegroundAppPackageName=null
  mForegroundAppCategory=-1
  mPendingForegroundAppCategory=-1
  Idle mode active=false
  mAdaptiveBrightnessOverTimeEnabled=false

  mInteractiveMapper=
SimpleMappingStrategy
  mSpline=MonotoneCubicSpline{[(0.0, 0.003937008: 0.0), (5.0, 0.003937008: 0.0), (10.0, 0.011811024: 0.0047244094), (15.0, 0.051181104: 0.0044575166), (30.0, 0.07480315: 0.0015654372), (100.0, 0.19685039: 0.0011276716), (200.0, 0.2480315: 5.1181106E-4), (300.0, 0.2992126: 4.9212604E-4), (400.0, 0.3464567: 4.8884517E-4), (1000.0, 0.6496063: 1.5119625E-4), (2000.0, 0.7007874: 2.6744623E-5), (3000.0, 0.7480315: 4.9212576E-5), (4000.0, 0.7992126: 5.118111E-5), (5000.0, 0.8503937: 5.118111E-5), (6000.0, 0.9015748: 7.4803145E-5), (7000.0, 1.0: 9.842521E-5)]}
  mMaxGamma=3.0
  mAutoBrightnessAdjustment=0.0
  mUserLux=-1.0
  mUserBrightness=-1.0
 mDozeBrightnessMapper=
DirectMappingStrategy
  mSpline=MonotoneCubicSpline{[(0.0, 0.007874016: 0.0), (5.0, 0.007874016: 0.0), (10.0, 0.007874016: 0.0), (15.0, 0.023622047: 0.002887139), (30.0, 0.062992126: 0.0026340457), (100.0, 0.2480315: 0.0014657726), (200.0, 0.2992126: 4.5723747E-4), (300.0, 0.3464567: 4.9212587E-4), (400.0, 0.39763778: 5.4790016E-4), (1000.0, 0.7480315: 1.5158737E-4), (2000.0, 0.7992126: 2.4429373E-5), (3000.0, 0.8503937: 5.118111E-5), (4000.0, 0.9015748: 4.9212606E-5), (5000.0, 0.9488189: 3.346458E-5), (6000.0, 0.96850395: 2.559054E-5), (7000.0, 1.0: 3.149605E-5)]}

HysteresisLevels
  mBrighteningThresholds=[1.059, 0.604, 0.452, 0.376, 0.226, 0.15, 0.137, 0.119, 0.112, 0.073, 0.028, 0.015]
  mDarkeningThresholds=[0.9, 0.464, 0.346, 0.289, 0.21, 0.14, 0.126, 0.109, 0.103, 0.072, 0.028, 0.015]
  mThresholdLevels=[25.0, 50.0, 100.0, 200.0, 400.0, 500.0, 800.0, 1000.0, 1600.0, 5000.0, 10000.0]
Automatic Brightness Adjustments Last 26 Events:
  2023-06-07 07:41:44 - BrightnessEvent: disp=0, brt=0.7992126, rcmdBrt=NaN, preBrt=NaN, lux=0.0, preLux=0.0, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=override
  2023-06-07 07:41:58 - BrightnessEvent: disp=0, brt=0.003937008, rcmdBrt=NaN, preBrt=0.0, lux=NaN, preLux=0.0, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=invalid_lux doze_scale , reason=manual
  2023-06-07 07:41:58 - BrightnessEvent: disp=0, brt=0.12217121, rcmdBrt=0.12217121, preBrt=NaN, lux=56.065, preLux=0.0, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic
  2023-06-07 07:42:13 - BrightnessEvent: disp=0, brt=0.027559055, rcmdBrt=0.12217121, preBrt=NaN, lux=56.065, preLux=0.0, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic [ dim ]
  2023-06-07 07:46:49 - BrightnessEvent: disp=0, brt=0.12217121, rcmdBrt=0.12217121, preBrt=NaN, lux=56.065, preLux=0.0, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic
  2023-06-07 07:47:05 - BrightnessEvent: disp=0, brt=0.007874016, rcmdBrt=0.12217121, preBrt=NaN, lux=56.065, preLux=0.0, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic [ dim ]
  2023-06-07 07:47:06 - BrightnessEvent: disp=0, brt=0.027559055, rcmdBrt=0.12217121, preBrt=NaN, lux=56.065, preLux=0.0, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic [ dim ]
  2025-06-20 13:05:26 - BrightnessEvent: disp=0, brt=0.12217121, rcmdBrt=0.12217121, preBrt=NaN, lux=56.065, preLux=0.0, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic
  2025-06-20 13:05:27 - BrightnessEvent: disp=0, brt=0.2192164, rcmdBrt=0.2192164, preBrt=0.12217121, lux=126.47749, preLux=56.065, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic
  2025-06-20 13:06:15 - BrightnessEvent: disp=0, brt=0.0, rcmdBrt=NaN, preBrt=NaN, lux=0.0, preLux=0.0, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=screen_off
  2025-06-20 13:06:16 - BrightnessEvent: disp=0, brt=0.2192164, rcmdBrt=NaN, preBrt=NaN, lux=NaN, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=invalid_lux doze_scale , reason=manual
  2025-06-20 13:06:16 - BrightnessEvent: disp=0, brt=0.20128389, rcmdBrt=0.20128389, preBrt=NaN, lux=104.112495, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic
  2025-06-20 13:07:14 - BrightnessEvent: disp=0, brt=0.035433073, rcmdBrt=0.20128389, preBrt=NaN, lux=104.112495, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic [ dim ]
  2025-06-20 13:14:23 - BrightnessEvent: disp=0, brt=0.20128389, rcmdBrt=0.20128389, preBrt=NaN, lux=104.112495, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic
  2025-06-20 13:14:35 - BrightnessEvent: disp=0, brt=0.0, rcmdBrt=NaN, preBrt=NaN, lux=0.0, preLux=0.0, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=screen_off
  2025-06-20 13:14:37 - BrightnessEvent: disp=0, brt=0.20128389, rcmdBrt=NaN, preBrt=NaN, lux=NaN, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=invalid_lux doze_scale , reason=manual
  2025-06-20 13:14:37 - BrightnessEvent: disp=0, brt=0.15782775, rcmdBrt=0.15782775, preBrt=NaN, lux=74.5775, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic
  2025-06-20 13:16:02 - BrightnessEvent: disp=0, brt=0.023622047, rcmdBrt=0.15782775, preBrt=NaN, lux=74.5775, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic [ dim ]
  2025-06-20 13:18:39 - BrightnessEvent: disp=0, brt=0.15782775, rcmdBrt=0.15782775, preBrt=NaN, lux=74.5775, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic
  2025-06-20 13:19:35 - BrightnessEvent: disp=0, brt=0.01968504, rcmdBrt=0.15782775, preBrt=NaN, lux=74.5775, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic [ dim ]
  2025-06-20 13:20:08 - BrightnessEvent: disp=0, brt=0.15782775, rcmdBrt=0.15782775, preBrt=NaN, lux=74.5775, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic
  2025-06-20 13:30:23 - BrightnessEvent: disp=0, brt=0.015748031, rcmdBrt=0.15782775, preBrt=NaN, lux=74.5775, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic [ dim ]
  2025-06-20 13:32:35 - BrightnessEvent: disp=0, brt=0.15782775, rcmdBrt=0.15782775, preBrt=NaN, lux=74.5775, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic
  2025-06-20 13:33:30 - BrightnessEvent: disp=0, brt=0.015748031, rcmdBrt=0.15782775, preBrt=NaN, lux=74.5775, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic [ dim ]
  2025-06-20 13:34:36 - BrightnessEvent: disp=0, brt=0.15782775, rcmdBrt=0.15782775, preBrt=NaN, lux=74.5775, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic
  2025-06-20 13:44:51 - BrightnessEvent: disp=0, brt=0.027559055, rcmdBrt=0.15782775, preBrt=NaN, lux=74.5775, preLux=NaN, hbmMax=1.0, hbmMode=off, thrmMax=1.0, flags=, reason=automatic [ dim ]
HighBrightnessModeController:
  mBrightness=0.0
  mUnthrottledBrightness=0.0
  mThrottlingReason=none
  mCurrentMin=0.0
  mCurrentMax=1.0
  mHbmMode=off
  mHbmStatsState=HBM_OFF
  mHbmData=null
  mAmbientLux=74.5775 (old/invalid)
  mIsInAllowedAmbientRange=false
  mIsAutoBrightnessEnabled=false
  mIsAutoBrightnessOffByState=false
  mIsHdrLayerPresent=false
  mBrightnessMin=0.0
  mBrightnessMax=1.0
  remainingTime=0
  mIsTimeAvailable= false
  mRunningStartTimeMillis=-1 (6274050 ms ago)
  mIsThermalStatusWithinLimit=true
  mIsBlockedByLowPowerMode=false
  width*height=384*384
  mEvents=
  SkinThermalStatusObserver:
    mStarted: false
    ThermalService not available
BrightnessThrottler:
  mThrottlingData=null
  mThrottlingStatus=-1
  mBrightnessCap=1.0
  mBrightnessMaxReason=none
  SkinThermalStatusObserver:
    mStarted: false
    ThermalService not available


BrightnessTracker state:
  mStarted=false
  mLightSensor={Sensor name="TCS3701 light sensor", vendor="AMS", version=1, type=5, maxRange=1000000.0, resolution=0.01, power=0.001, minDelay=0}
  mLastBatteryLevel=NaN
  mLastBrightness=-1.0
  mLastSensorReadings.size=0
  mEventsDirty=false
  mEvents.size=0
  mWriteBrightnessTrackerStateScheduled=false
  mSensorRegistered=false
  mColorSamplingEnabled=false
  mNoFramesToSample=0
  mFrameRate=0.0

PersistentDataStore
  mLoaded=true
  mDirty=false
  RememberedWifiDisplays:
  DisplayStates:
    0: local:4630946526965601921
      ColorMode=0
      BrightnessValue=0.15782775
      DisplayBrightnessConfigurations:
  StableDeviceValues:
      StableDisplayWidth=384
      StableDisplayHeight=384
  GlobalBrightnessConfigurations:

Display Window Policy Controllers: size=0

DisplayModeDirector
  mSupportedModesByDisplay:
    0 -> [{id=1, width=384, height=384, fps=60.000004, alternativeRefreshRates=[]}]
  mDefaultModeByDisplay:
    0 -> {id=1, width=384, height=384, fps=60.000004, alternativeRefreshRates=[]}
  mVotesByDisplay:
    -1:
      PRIORITY_USER_SETTING_MIN_REFRESH_RATE -> Vote{width=-1, height=-1, minRefreshRate=0.0, maxRefreshRate=Infinity, disableRefreshRateSwitching=false, baseModeRefreshRate=0.0}
      PRIORITY_DEFAULT_REFRESH_RATE -> Vote{width=-1, height=-1, minRefreshRate=0.0, maxRefreshRate=60.0, disableRefreshRateSwitching=false, baseModeRefreshRate=0.0}
  mModeSwitchingType: SWITCHING_TYPE_WITHIN_GROUPS
  mAlwaysRespectAppRequest: false
  SettingsObserver
    mDefaultRefreshRate: 60.0
    mDefaultPeakRefreshRate: 0.0
  AppRequestObserver
    mAppRequestedModeByDisplay:
    mAppPreferredRefreshRateRangeByDisplay:
  BrightnessObserver
    mAmbientLux: -1.0
    mBrightness: 8
    mDefaultDisplayState: 2
    mLowPowerModeEnabled: false
    mRefreshRateChangeable: false
    mShouldObserveDisplayLowChange: false
    mShouldObserveAmbientLowChange: false
    mRefreshRateInLowZone: 0
    mShouldObserveDisplayHighChange: false
    mShouldObserveAmbientHighChange: false
    mRefreshRateInHighZone: 0
    mLastSensorData: 0.0
    mTimestamp: 1970-01-01 01:00:00.000
  UdfpsObserver
    mLocalHbmEnabled:
   HbmObserver
     mHbmMode: {}
     mHbmActive: {}
     mRefreshRateInHbmSunlight: 0
     mRefreshRateInHbmHdr: 0
  SkinThermalStatusObserver:
    mStatus: 0
  SensorObserver
    mIsProxActive=false
    mDozeStateByDisplay:
      0 -> false
BrightnessSynchronizer
  mLatestIntBrightness=41
  mLatestFloatBrightness=0.15782775
  mCurrentUpdate=null
  mPendingUpdate=null
"""
