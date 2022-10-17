package com.android.tools.screensharing;

import static android.media.MediaCodecList.REGULAR_CODECS;

import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.util.Range;

/**
 * Basic codec information and a static method to select a video encoder.
 * This code is in Java because NDK doesn't provide access to {@link MediaCodecList} and
 * {@link MediaCodecInfo}.
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
}
