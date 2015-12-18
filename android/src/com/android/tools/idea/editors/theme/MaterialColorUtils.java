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
 */
package com.android.tools.idea.editors.theme;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

@SuppressWarnings({"UseJBColor"})
public class MaterialColorUtils {

  private static final ImmutableMap<Color, Color> PRIMARY_DARK_COLORS_MAP = ImmutableMap.<Color, Color>builder()
    .put(MaterialColors.RED_500, MaterialColors.RED_700)
    .put(MaterialColors.PINK_500, MaterialColors.PINK_700)
    .put(MaterialColors.PURPLE_500, MaterialColors.PURPLE_700)
    .put(MaterialColors.DEEP_PURPLE_500, MaterialColors.DEEP_PURPLE_700)
    .put(MaterialColors.INDIGO_500, MaterialColors.INDIGO_700)
    .put(MaterialColors.BLUE_500, MaterialColors.BLUE_700)
    .put(MaterialColors.LIGHT_BLUE_500, MaterialColors.LIGHT_BLUE_700)
    .put(MaterialColors.CYAN_500, MaterialColors.CYAN_700)
    .put(MaterialColors.TEAL_500, MaterialColors.TEAL_700)
    .put(MaterialColors.GREEN_500, MaterialColors.GREEN_700)
    .put(MaterialColors.LIGHT_GREEN_500, MaterialColors.LIGHT_GREEN_700)
    .put(MaterialColors.LIME_500, MaterialColors.LIME_700)
    .put(MaterialColors.YELLOW_500, MaterialColors.YELLOW_700)
    .put(MaterialColors.AMBER_500, MaterialColors.AMBER_700)
    .put(MaterialColors.ORANGE_500, MaterialColors.ORANGE_700)
    .put(MaterialColors.DEEP_ORANGE_500, MaterialColors.DEEP_ORANGE_700)
    .put(MaterialColors.BROWN_500, MaterialColors.BROWN_700)
    .put(MaterialColors.GREY_500, MaterialColors.GREY_700)
    .put(MaterialColors.BLUE_GREY_500, MaterialColors.BLUE_GREY_700)
    .build();

  private static final ImmutableMap<Color, String> MATERIAL_NAMES_MAP = ImmutableMap.<Color, String>builder()
    .put(MaterialColors.RED_50, "Material Red 50")
    .put(MaterialColors.RED_100, "Material Red 100")
    .put(MaterialColors.RED_200, "Material Red 200")
    .put(MaterialColors.RED_300, "Material Red 300")
    .put(MaterialColors.RED_400, "Material Red 400")
    .put(MaterialColors.RED_500, "Material Red 500")
    .put(MaterialColors.RED_600, "Material Red 600")
    .put(MaterialColors.RED_700, "Material Red 700")
    .put(MaterialColors.RED_800, "Material Red 800")
    .put(MaterialColors.RED_900, "Material Red 900")
    .put(MaterialColors.PINK_50, "Material Pink 50")
    .put(MaterialColors.PINK_100, "Material Pink 100")
    .put(MaterialColors.PINK_200, "Material Pink 200")
    .put(MaterialColors.PINK_300, "Material Pink 300")
    .put(MaterialColors.PINK_400, "Material Pink 400")
    .put(MaterialColors.PINK_500, "Material Pink 500")
    .put(MaterialColors.PINK_600, "Material Pink 600")
    .put(MaterialColors.PINK_700, "Material Pink 700")
    .put(MaterialColors.PINK_800, "Material Pink 800")
    .put(MaterialColors.PINK_900, "Material Pink 900")
    .put(MaterialColors.PURPLE_50, "Material Purple 50")
    .put(MaterialColors.PURPLE_100, "Material Purple 100")
    .put(MaterialColors.PURPLE_200, "Material Purple 200")
    .put(MaterialColors.PURPLE_300, "Material Purple 300")
    .put(MaterialColors.PURPLE_400, "Material Purple 400")
    .put(MaterialColors.PURPLE_500, "Material Purple 500")
    .put(MaterialColors.PURPLE_600, "Material Purple 600")
    .put(MaterialColors.PURPLE_700, "Material Purple 700")
    .put(MaterialColors.PURPLE_800, "Material Purple 800")
    .put(MaterialColors.PURPLE_900, "Material Purple 900")
    .put(MaterialColors.DEEP_PURPLE_50, "Material Deep Purple 50")
    .put(MaterialColors.DEEP_PURPLE_100, "Material Deep Purple 100")
    .put(MaterialColors.DEEP_PURPLE_200, "Material Deep Purple 200")
    .put(MaterialColors.DEEP_PURPLE_300, "Material Deep Purple 300")
    .put(MaterialColors.DEEP_PURPLE_400, "Material Deep Purple 400")
    .put(MaterialColors.DEEP_PURPLE_500, "Material Deep Purple 500")
    .put(MaterialColors.DEEP_PURPLE_600, "Material Deep Purple 600")
    .put(MaterialColors.DEEP_PURPLE_700, "Material Deep Purple 700")
    .put(MaterialColors.DEEP_PURPLE_800, "Material Deep Purple 800")
    .put(MaterialColors.DEEP_PURPLE_900, "Material Deep Purple 900")
    .put(MaterialColors.INDIGO_50, "Material Indigo 50")
    .put(MaterialColors.INDIGO_100, "Material Indigo 100")
    .put(MaterialColors.INDIGO_200, "Material Indigo 200")
    .put(MaterialColors.INDIGO_300, "Material Indigo 300")
    .put(MaterialColors.INDIGO_400, "Material Indigo 400")
    .put(MaterialColors.INDIGO_500, "Material Indigo 500")
    .put(MaterialColors.INDIGO_600, "Material Indigo 600")
    .put(MaterialColors.INDIGO_700, "Material Indigo 700")
    .put(MaterialColors.INDIGO_800, "Material Indigo 800")
    .put(MaterialColors.INDIGO_900, "Material Indigo 900")
    .put(MaterialColors.BLUE_50, "Material Blue 50")
    .put(MaterialColors.BLUE_100, "Material Blue 100")
    .put(MaterialColors.BLUE_200, "Material Blue 200")
    .put(MaterialColors.BLUE_300, "Material Blue 300")
    .put(MaterialColors.BLUE_400, "Material Blue 400")
    .put(MaterialColors.BLUE_500, "Material Blue 500")
    .put(MaterialColors.BLUE_600, "Material Blue 600")
    .put(MaterialColors.BLUE_700, "Material Blue 700")
    .put(MaterialColors.BLUE_800, "Material Blue 800")
    .put(MaterialColors.BLUE_900, "Material Blue 900")
    .put(MaterialColors.LIGHT_BLUE_50, "Material Light Blue 50")
    .put(MaterialColors.LIGHT_BLUE_100, "Material Light Blue 100")
    .put(MaterialColors.LIGHT_BLUE_200, "Material Light Blue 200")
    .put(MaterialColors.LIGHT_BLUE_300, "Material Light Blue 300")
    .put(MaterialColors.LIGHT_BLUE_400, "Material Light Blue 400")
    .put(MaterialColors.LIGHT_BLUE_500, "Material Light Blue 500")
    .put(MaterialColors.LIGHT_BLUE_600, "Material Light Blue 600")
    .put(MaterialColors.LIGHT_BLUE_700, "Material Light Blue 700")
    .put(MaterialColors.LIGHT_BLUE_800, "Material Light Blue 800")
    .put(MaterialColors.LIGHT_BLUE_900, "Material Light Blue 900")
    .put(MaterialColors.CYAN_50, "Material Cyan 50")
    .put(MaterialColors.CYAN_100, "Material Cyan 100")
    .put(MaterialColors.CYAN_200, "Material Cyan 200")
    .put(MaterialColors.CYAN_300, "Material Cyan 300")
    .put(MaterialColors.CYAN_400, "Material Cyan 400")
    .put(MaterialColors.CYAN_500, "Material Cyan 500")
    .put(MaterialColors.CYAN_600, "Material Cyan 600")
    .put(MaterialColors.CYAN_700, "Material Cyan 700")
    .put(MaterialColors.CYAN_800, "Material Cyan 800")
    .put(MaterialColors.CYAN_900, "Material Cyan 900")
    .put(MaterialColors.TEAL_50, "Material Teal 50")
    .put(MaterialColors.TEAL_100, "Material Teal 100")
    .put(MaterialColors.TEAL_200, "Material Teal 200")
    .put(MaterialColors.TEAL_300, "Material Teal 300")
    .put(MaterialColors.TEAL_400, "Material Teal 400")
    .put(MaterialColors.TEAL_500, "Material Teal 500")
    .put(MaterialColors.TEAL_600, "Material Teal 600")
    .put(MaterialColors.TEAL_700, "Material Teal 700")
    .put(MaterialColors.TEAL_800, "Material Teal 800")
    .put(MaterialColors.TEAL_900, "Material Teal 900")
    .put(MaterialColors.GREEN_50, "Material Green 50")
    .put(MaterialColors.GREEN_100, "Material Green 100")
    .put(MaterialColors.GREEN_200, "Material Green 200")
    .put(MaterialColors.GREEN_300, "Material Green 300")
    .put(MaterialColors.GREEN_400, "Material Green 400")
    .put(MaterialColors.GREEN_500, "Material Green 500")
    .put(MaterialColors.GREEN_600, "Material Green 600")
    .put(MaterialColors.GREEN_700, "Material Green 700")
    .put(MaterialColors.GREEN_800, "Material Green 800")
    .put(MaterialColors.GREEN_900, "Material Green 900")
    .put(MaterialColors.LIGHT_GREEN_50, "Material Light Green 50")
    .put(MaterialColors.LIGHT_GREEN_100, "Material Light Green 100")
    .put(MaterialColors.LIGHT_GREEN_200, "Material Light Green 200")
    .put(MaterialColors.LIGHT_GREEN_300, "Material Light Green 300")
    .put(MaterialColors.LIGHT_GREEN_400, "Material Light Green 400")
    .put(MaterialColors.LIGHT_GREEN_500, "Material Light Green 500")
    .put(MaterialColors.LIGHT_GREEN_600, "Material Light Green 600")
    .put(MaterialColors.LIGHT_GREEN_700, "Material Light Green 700")
    .put(MaterialColors.LIGHT_GREEN_800, "Material Light Green 800")
    .put(MaterialColors.LIGHT_GREEN_900, "Material Light Green 900")
    .put(MaterialColors.LIME_50, "Material Lime 50")
    .put(MaterialColors.LIME_100, "Material Lime 100")
    .put(MaterialColors.LIME_200, "Material Lime 200")
    .put(MaterialColors.LIME_300, "Material Lime 300")
    .put(MaterialColors.LIME_400, "Material Lime 400")
    .put(MaterialColors.LIME_500, "Material Lime 500")
    .put(MaterialColors.LIME_600, "Material Lime 600")
    .put(MaterialColors.LIME_700, "Material Lime 700")
    .put(MaterialColors.LIME_800, "Material Lime 800")
    .put(MaterialColors.LIME_900, "Material Lime 900")
    .put(MaterialColors.YELLOW_50, "Material Yellow 50")
    .put(MaterialColors.YELLOW_100, "Material Yellow 100")
    .put(MaterialColors.YELLOW_200, "Material Yellow 200")
    .put(MaterialColors.YELLOW_300, "Material Yellow 300")
    .put(MaterialColors.YELLOW_400, "Material Yellow 400")
    .put(MaterialColors.YELLOW_500, "Material Yellow 500")
    .put(MaterialColors.YELLOW_600, "Material Yellow 600")
    .put(MaterialColors.YELLOW_700, "Material Yellow 700")
    .put(MaterialColors.YELLOW_800, "Material Yellow 800")
    .put(MaterialColors.YELLOW_900, "Material Yellow 900")
    .put(MaterialColors.AMBER_50, "Material Amber 50")
    .put(MaterialColors.AMBER_100, "Material Amber 100")
    .put(MaterialColors.AMBER_200, "Material Amber 200")
    .put(MaterialColors.AMBER_300, "Material Amber 300")
    .put(MaterialColors.AMBER_400, "Material Amber 400")
    .put(MaterialColors.AMBER_500, "Material Amber 500")
    .put(MaterialColors.AMBER_600, "Material Amber 600")
    .put(MaterialColors.AMBER_700, "Material Amber 700")
    .put(MaterialColors.AMBER_800, "Material Amber 800")
    .put(MaterialColors.AMBER_900, "Material Amber 900")
    .put(MaterialColors.ORANGE_50, "Material Orange 50")
    .put(MaterialColors.ORANGE_100, "Material Orange 100")
    .put(MaterialColors.ORANGE_200, "Material Orange 200")
    .put(MaterialColors.ORANGE_300, "Material Orange 300")
    .put(MaterialColors.ORANGE_400, "Material Orange 400")
    .put(MaterialColors.ORANGE_500, "Material Orange 500")
    .put(MaterialColors.ORANGE_600, "Material Orange 600")
    .put(MaterialColors.ORANGE_700, "Material Orange 700")
    .put(MaterialColors.ORANGE_800, "Material Orange 800")
    .put(MaterialColors.ORANGE_900, "Material Orange 900")
    .put(MaterialColors.DEEP_ORANGE_50, "Material Deep Orange 50")
    .put(MaterialColors.DEEP_ORANGE_100, "Material Deep Orange 100")
    .put(MaterialColors.DEEP_ORANGE_200, "Material Deep Orange 200")
    .put(MaterialColors.DEEP_ORANGE_300, "Material Deep Orange 300")
    .put(MaterialColors.DEEP_ORANGE_400, "Material Deep Orange 400")
    .put(MaterialColors.DEEP_ORANGE_500, "Material Deep Orange 500")
    .put(MaterialColors.DEEP_ORANGE_600, "Material Deep Orange 600")
    .put(MaterialColors.DEEP_ORANGE_700, "Material Deep Orange 700")
    .put(MaterialColors.DEEP_ORANGE_800, "Material Deep Orange 800")
    .put(MaterialColors.DEEP_ORANGE_900, "Material Deep Orange 900")
    .put(MaterialColors.BROWN_50, "Material Brown 50")
    .put(MaterialColors.BROWN_100, "Material Brown 100")
    .put(MaterialColors.BROWN_200, "Material Brown 200")
    .put(MaterialColors.BROWN_300, "Material Brown 300")
    .put(MaterialColors.BROWN_400, "Material Brown 400")
    .put(MaterialColors.BROWN_500, "Material Brown 500")
    .put(MaterialColors.BROWN_600, "Material Brown 600")
    .put(MaterialColors.BROWN_700, "Material Brown 700")
    .put(MaterialColors.BROWN_800, "Material Brown 800")
    .put(MaterialColors.BROWN_900, "Material Brown 900")
    .put(MaterialColors.GREY_50, "Material Grey 50")
    .put(MaterialColors.GREY_100, "Material Grey 100")
    .put(MaterialColors.GREY_200, "Material Grey 200")
    .put(MaterialColors.GREY_300, "Material Grey 300")
    .put(MaterialColors.GREY_400, "Material Grey 400")
    .put(MaterialColors.GREY_500, "Material Grey 500")
    .put(MaterialColors.GREY_600, "Material Grey 600")
    .put(MaterialColors.GREY_700, "Material Grey 700")
    .put(MaterialColors.GREY_800, "Material Grey 800")
    .put(MaterialColors.GREY_900, "Material Grey 900")
    .put(MaterialColors.BLUE_GREY_50, "Material Blue Grey 50")
    .put(MaterialColors.BLUE_GREY_100, "Material Blue Grey 100")
    .put(MaterialColors.BLUE_GREY_200, "Material Blue Grey 200")
    .put(MaterialColors.BLUE_GREY_300, "Material Blue Grey 300")
    .put(MaterialColors.BLUE_GREY_400, "Material Blue Grey 400")
    .put(MaterialColors.BLUE_GREY_500, "Material Blue Grey 500")
    .put(MaterialColors.BLUE_GREY_600, "Material Blue Grey 600")
    .put(MaterialColors.BLUE_GREY_700, "Material Blue Grey 700")
    .put(MaterialColors.BLUE_GREY_800, "Material Blue Grey 800")
    .put(MaterialColors.BLUE_GREY_900, "Material Blue Grey 900")
    .put(MaterialColors.RED_ACCENT_100, "Red accent 100")
    .put(MaterialColors.RED_ACCENT_200, "Red accent 200")
    .put(MaterialColors.RED_ACCENT_400, "Red accent 400")
    .put(MaterialColors.RED_ACCENT_700, "Red accent 700")
    .put(MaterialColors.PINK_ACCENT_100, "Pink accent 100")
    .put(MaterialColors.PINK_ACCENT_200, "Pink accent 200")
    .put(MaterialColors.PINK_ACCENT_400, "Pink accent 400")
    .put(MaterialColors.PINK_ACCENT_700, "Pink accent 700")
    .put(MaterialColors.PURPLE_ACCENT_100, "Purple accent 100")
    .put(MaterialColors.PURPLE_ACCENT_200, "Purple accent 200")
    .put(MaterialColors.PURPLE_ACCENT_400, "Purple accent 400")
    .put(MaterialColors.PURPLE_ACCENT_700, "Purple accent 700")
    .put(MaterialColors.INDIGO_ACCENT_100, "Indigo accent 100")
    .put(MaterialColors.INDIGO_ACCENT_200, "Indigo accent 200")
    .put(MaterialColors.INDIGO_ACCENT_400, "Indigo accent 400")
    .put(MaterialColors.INDIGO_ACCENT_700, "Indigo accent 700")
    .put(MaterialColors.DEEP_PURPLE_ACCENT_100, "Deep purple accent 100")
    .put(MaterialColors.DEEP_PURPLE_ACCENT_200, "Deep purple accent 200")
    .put(MaterialColors.DEEP_PURPLE_ACCENT_400, "Deep purple accent 400")
    .put(MaterialColors.DEEP_PURPLE_ACCENT_700, "Deep purple accent 700")
    .put(MaterialColors.BLUE_ACCENT_100, "Blue accent 100")
    .put(MaterialColors.BLUE_ACCENT_200, "Blue accent 200")
    .put(MaterialColors.BLUE_ACCENT_400, "Blue accent 400")
    .put(MaterialColors.BLUE_ACCENT_700, "Blue accent 700")
    .put(MaterialColors.LIGHT_BLUE_ACCENT_100, "Light blue accent 100")
    .put(MaterialColors.LIGHT_BLUE_ACCENT_200, "Light blue accent 200")
    .put(MaterialColors.LIGHT_BLUE_ACCENT_400, "Light blue accent 400")
    .put(MaterialColors.LIGHT_BLUE_ACCENT_700, "Light blue accent 700")
    .put(MaterialColors.CYAN_ACCENT_100, "Cyan accent 100")
    .put(MaterialColors.CYAN_ACCENT_200, "Cyan accent 200")
    .put(MaterialColors.CYAN_ACCENT_400, "Cyan accent 400")
    .put(MaterialColors.CYAN_ACCENT_700, "Cyan accent 700")
    .put(MaterialColors.TEAL_ACCENT_100, "Teal accent 100")
    .put(MaterialColors.TEAL_ACCENT_200, "Teal accent 200")
    .put(MaterialColors.TEAL_ACCENT_400, "Teal accent 400")
    .put(MaterialColors.TEAL_ACCENT_700, "Teal accent 700")
    .put(MaterialColors.GREEN_ACCENT_100, "Green accent 100")
    .put(MaterialColors.GREEN_ACCENT_200, "Green accent 200")
    .put(MaterialColors.GREEN_ACCENT_400, "Green accent 400")
    .put(MaterialColors.GREEN_ACCENT_700, "Green accent 700")
    .put(MaterialColors.LIGHT_GREEN_ACCENT_100, "Light green accent 100")
    .put(MaterialColors.LIGHT_GREEN_ACCENT_200, "Light green accent 200")
    .put(MaterialColors.LIGHT_GREEN_ACCENT_400, "Light green accent 400")
    .put(MaterialColors.LIGHT_GREEN_ACCENT_700, "Light green accent 700")
    .put(MaterialColors.LIME_ACCENT_100, "Lime accent 100")
    .put(MaterialColors.LIME_ACCENT_200, "Lime accent 200")
    .put(MaterialColors.LIME_ACCENT_400, "Lime accent 400")
    .put(MaterialColors.LIME_ACCENT_700, "Lime accent 700")
    .put(MaterialColors.YELLOW_ACCENT_100, "Yellow accent 100")
    .put(MaterialColors.YELLOW_ACCENT_200, "Yellow accent 200")
    .put(MaterialColors.YELLOW_ACCENT_400, "Yellow accent 400")
    .put(MaterialColors.YELLOW_ACCENT_700, "Yellow accent 700")
    .put(MaterialColors.AMBER_ACCENT_100, "Amber accent 100")
    .put(MaterialColors.AMBER_ACCENT_200, "Amber accent 200")
    .put(MaterialColors.AMBER_ACCENT_400, "Amber accent 400")
    .put(MaterialColors.AMBER_ACCENT_700, "Amber accent 700")
    .put(MaterialColors.ORANGE_ACCENT_100, "Orange accent 100")
    .put(MaterialColors.ORANGE_ACCENT_200, "Orange accent 200")
    .put(MaterialColors.ORANGE_ACCENT_400, "Orange accent 400")
    .put(MaterialColors.ORANGE_ACCENT_700, "Orange accent 700")
    .put(MaterialColors.DEEP_ORANGE_ACCENT_100, "Deep orange accent 100")
    .put(MaterialColors.DEEP_ORANGE_ACCENT_200, "Deep orange accent 200")
    .put(MaterialColors.DEEP_ORANGE_ACCENT_400, "Deep orange accent 400")
    .put(MaterialColors.DEEP_ORANGE_ACCENT_700, "Deep orange accent 700")
    .put(Color.BLACK, "Black")
    .put(Color.WHITE, "White")
    .build();

  private static final ImmutableList<Color> PRIMARY_COLORS_LIST = ImmutableList.copyOf(PRIMARY_DARK_COLORS_MAP.keySet());

  @NotNull
  public static List<Color> suggestPrimaryColors() {
    return PRIMARY_COLORS_LIST;
  }

  /**
   * Method that suggests accent colors for a given primary color based on the material guidelines and classical color theory
   * <a href="http://www.tigercolor.com/color-lab/color-theory/color-harmonies.htm">Complementary, Analogous and Triad combinations</a>
   * <a href="https://en.wikipedia.org/wiki/Monochromatic_color">Monochromatic colors</a>
   * For each hue (complementary, analogous, triad or monochromatic), two colors are suggested as accents:
   * <ul>
   * <li>75% saturation and 100% brightness</li>
   * <li>100% saturation and original brightness of the color</li>
   * </ul>
   */
  @NotNull
  public static List<Color> suggestAccentColors(@NotNull Color primaryColor) {
    ImmutableList.Builder<Color> builder = ImmutableList.builder();
    if (AccentSuggestionsUtils.isMaterialPrimary(primaryColor)) {
      builder.addAll(AccentSuggestionsUtils.getMonochromaticAccents(primaryColor));
      builder.addAll(AccentSuggestionsUtils.getAssociatedAccents(primaryColor));
      builder.addAll(AccentSuggestionsUtils.getComplementaryAccents(primaryColor));
      builder.addAll(AccentSuggestionsUtils.getTriadAccents(primaryColor));
    }
    else {
      float[] hsv = Color.RGBtoHSB(primaryColor.getRed(), primaryColor.getGreen(), primaryColor.getBlue(), null);

      // If the primaryColor's brightness is too low, we set it to a higher value (0.6) to have a bright accent color
      hsv[2] = Math.max(0.6f, hsv[2]);

      // Monochromatic
      builder.add(Color.getHSBColor(hsv[0], 0.75f, 1));
      builder.add(Color.getHSBColor(hsv[0], 1, hsv[2]));
      // Analogous
      float associatedHue1 = (hsv[0] + 0.125f) % 1;
      builder.add(Color.getHSBColor(associatedHue1, 0.75f, 1));
      builder.add(Color.getHSBColor(associatedHue1, 1, hsv[2]));
      float associatedHue2 = (hsv[0] + 0.875f) % 1;
      builder.add(Color.getHSBColor(associatedHue2, 0.75f, 1));
      builder.add(Color.getHSBColor(associatedHue2, 1, hsv[2]));
      // Complementary
      float complementaryHue = (hsv[0] + 0.5f) % 1;
      builder.add(Color.getHSBColor(complementaryHue, 0.75f, 1));
      builder.add(Color.getHSBColor(complementaryHue, 1, hsv[2]));
      // Triad
      float triadHue1 = (hsv[0] + 0.625f) % 1;
      builder.add(Color.getHSBColor(triadHue1, 0.75f, 1));
      builder.add(Color.getHSBColor(triadHue1, 1, hsv[2]));
      float triadHue2 = (hsv[0] + 0.375f) % 1;
      builder.add(Color.getHSBColor(triadHue2, 0.75f, 1));
      builder.add(Color.getHSBColor(triadHue2, 1, hsv[2]));
    }
    return builder.build();
  }

  @NotNull
  public static List<Color> suggestPrimaryDarkColors(@NotNull Color primaryColor) {
    Color suggestedColor = PRIMARY_DARK_COLORS_MAP.get(primaryColor);
    if (suggestedColor == null) {
      suggestedColor = primaryColor.darker();
    }
    return ImmutableList.of(suggestedColor);
  }

  /**
   * @return the material name for a given color
   */
  @Nullable("if the color is not material")
  public static String getMaterialName(@NotNull Color color) {
    return MATERIAL_NAMES_MAP.get(color);
  }

  /**
   * @return the closest material color to a given color
   */
  @NotNull
  public static Color getClosestMaterialColor(@NotNull Color color) {
    String name = MATERIAL_NAMES_MAP.get(color);
    if (name == null) {
      float minDistance = Float.MAX_VALUE;
      Color minDistanceColor = null;

      for (Color materialColor : MATERIAL_NAMES_MAP.keySet()) {

        float distance = colorDistance(color, materialColor);

        if (distance < minDistance) {
          minDistance = distance;
          minDistanceColor = materialColor;
        }
      }
      //noinspection ConstantConditions // The returned value cannot be null because it is a minimum of a non-empty collection
      return minDistanceColor;
    }
    return color;
  }

  /**
   * taken from: http://www.compuphase.com/cmetric.htm
   */
  public static float colorDistance(Color c1, Color c2) {
    double rmean = (c1.getRed() + c2.getRed()) / 2;
    int r = c1.getRed() - c2.getRed();
    int g = c1.getGreen() - c2.getGreen();
    int b = c1.getBlue() - c2.getBlue();
    double weightR = 2 + rmean / 256;
    double weightG = 4.0;
    double weightB = 2 + (255 - rmean) / 256;
    return (float)Math.sqrt(weightR * r * r + weightG * g * g + weightB * b * b);
  }
}