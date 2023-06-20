/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.screensharing;

import static android.media.MediaCodecList.REGULAR_CODECS;

import static java.lang.Math.min;

import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import android.media.MediaCodecList;
import android.os.Build.VERSION;
import android.util.Log;
import android.util.Range;
import java.util.List;

/**
 * Basic codec information and a static method to select a video encoder.
 * This code is in Java because NDK doesn't provide access to {@link MediaCodecList} and
 * {@link MediaCodecInfo}. Used from native code.
 */
public class CodecInfo {
  public final String name;
  public final int maxWidth;
  public final int maxHeight;
  public final int widthAlignment;
  public final int heightAlignment;

  private CodecInfo(String name, int maxWidth, int maxHeight, int widthAlignment, int heightAlignment) {
    this.name = name;
    this.maxWidth = maxWidth;
    this.maxHeight = maxHeight;
    this.widthAlignment = widthAlignment;
    this.heightAlignment = heightAlignment;
  }

  /**
   * Returns parameters of the video encoder for the given mime type.
   *
   * @param mimeType the mime type of the codec, e.g. "video/x-vnd.on2.vp8"
   * @return a CodecInfo object, or null if the given mime type is not supported by any encoder
   */
  public static CodecInfo selectVideoEncoderForType(String mimeType) {
    for (MediaCodecInfo codecInfo : new MediaCodecList(REGULAR_CODECS).getCodecInfos()) {
      if (!codecInfo.isEncoder()) {
        continue;
      }

      String[] types = codecInfo.getSupportedTypes();
      for (String type : types) {
        if (type.equalsIgnoreCase(mimeType)) {
          VideoCapabilities videoCapabilities = codecInfo.getCapabilitiesForType(mimeType).getVideoCapabilities();
          if (videoCapabilities != null) {
            // The first codec in MediaCodecList for a given mime type is always the most capable one.
            Range<Integer> heights = videoCapabilities.getSupportedHeights();
            Range<Integer> widths = videoCapabilities.getSupportedWidths();
            return new CodecInfo(codecInfo.getName(), widths.getUpper(), heights.getUpper(),
                                 videoCapabilities.getWidthAlignment(), videoCapabilities.getHeightAlignment());
          }
        }
      }
    }

    return null;
  }

  /**
   * Returns diagnostic information for the specified video encoder and video dimensions.
   */
  public static String getVideoEncoderDetails(String codecName, String mimeType, int width, int height) {
    for (MediaCodecInfo codecInfo : new MediaCodecList(REGULAR_CODECS).getCodecInfos()) {
      if (codecInfo.getName().equals(codecName)) {
        VideoCapabilities videoCapabilities = codecInfo.getCapabilitiesForType(mimeType).getVideoCapabilities();
        if (videoCapabilities == null) {
          return codecName + " is not a video encoder";
        }
        Range<Integer> heights = videoCapabilities.getSupportedHeights();
        Range<Integer> widths = videoCapabilities.getSupportedWidths();
        int maxWidth = widths.getUpper();
        int maxHeight = heights.getUpper();
        StringBuilder result = new StringBuilder();
        result.append("encoder: ").append(codecInfo.getName());
        result.append("\nmime type: ").append(mimeType);
        if (VERSION.SDK_INT >= 29) {
          if (codecInfo.isHardwareAccelerated()) {
            result.append(codecInfo.isHardwareAccelerated() ? " hardware accelerated" : " not hardware accelerated");
          }
        }
        result.append("\nmax resolution: ").append(maxWidth).append("x").append(maxHeight);
        result.append("\nmin resolution: ").append(widths.getLower()).append("x").append(heights.getLower());
        result.append("\nalignment: ")
            .append(videoCapabilities.getWidthAlignment()).append("x").append(videoCapabilities.getHeightAlignment());
        result.append("\nmax frame rate: ").append(videoCapabilities.getSupportedFrameRates().getUpper());
        double scale = min(1.0, min((double) maxWidth / width, (double) maxHeight / height));
        width = (int) Math.round(width * scale);
        height = (int) Math.round(height * scale);
        result.append("\nmax frame rate for ").append(width).append('x').append(height).append(": ")
            .append((int) Math.round(videoCapabilities.getSupportedFrameRatesFor(width, height).getUpper()));
        result.append("\nmax bitrate: ").append(videoCapabilities.getBitrateRange().getUpper());
        if (VERSION.SDK_INT >= 29) {
          List<PerformancePoint> performancePoints = videoCapabilities.getSupportedPerformancePoints();
          if (performancePoints != null) {
            result.append("\nperformance points:");
            for (PerformancePoint point : performancePoints) {
              result.append("\n  ").append(point);
            }
          }
        }
        return result.toString();
      }
    }
    return "Could not find " + codecName;
  }
}
