/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 12/2015  G. Lucas     Created
 *
 * Notes:
 *
 *  The Monotonic Luminance palette comes from Matteo Niccoli's
 *  blog "MYCARTA" article "The rainbow is dead... Long live the rainbow"
 * -----------------------------------------------------------------------
 */
package tinfour.test.utils;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Provides definitions and operations for assigning color
 * values to data.
 */
@SuppressWarnings("PMD.FieldDeclarationsShouldBeAtStartOfClassRule")
public class TestPalette {

  private static class Recipe {

    final String name;
    final TestPalette palette;

    Recipe(String name, float[][] rgb) {
      this.name = name;
      this.palette = new TestPalette(name, rgb);
    }

  }

  /**
   * A palette giving values from blue to yellow based on the
   * CIE LCH color model.
   */
  private static final float[][] paletteB2Y = {
    {0, 0, 255},
    {0, 34, 255},
    {0, 50, 255},
    {0, 63, 255},
    {0, 73, 255},
    {0, 82, 255},
    {0, 90, 255},
    {0, 97, 255},
    {0, 104, 255},
    {0, 110, 255},
    {0, 116, 255},
    {0, 121, 255},
    {0, 127, 255},
    {0, 132, 255},
    {0, 136, 255},
    {0, 141, 255},
    {0, 145, 255},
    {0, 149, 255},
    {0, 153, 255},
    {0, 157, 255},
    {0, 160, 255},
    {0, 164, 255},
    {0, 167, 255},
    {0, 170, 255},
    {0, 174, 255},
    {0, 177, 255},
    {0, 180, 255},
    {0, 183, 255},
    {0, 186, 255},
    {0, 189, 255},
    {0, 192, 255},
    {0, 195, 249},
    {0, 198, 241},
    {0, 201, 233},
    {0, 203, 224},
    {0, 206, 216},
    {0, 209, 207},
    {0, 212, 199},
    {0, 214, 190},
    {0, 217, 181},
    {0, 220, 173},
    {0, 222, 164},
    {0, 225, 156},
    {0, 227, 147},
    {0, 230, 139},
    {0, 232, 131},
    {0, 234, 122},
    {0, 236, 114},
    {0, 238, 106},
    {0, 240, 98},
    {0, 242, 90},
    {0, 244, 82},
    {0, 245, 74},
    {65, 247, 67},
    {94, 248, 59},
    {117, 250, 51},
    {136, 251, 42},
    {154, 252, 34},
    {170, 253, 25},
    {186, 253, 15},
    {200, 254, 4},
    {215, 254, 0},
    {228, 255, 0},
    {242, 255, 0},
    {255, 255, 0}
  };

  /**
   * A multi-hue color scheme adapted from "ColorBrewer: Color Advice for Maps"
   * by Cynthia Brewer, et al., at
   * <a href="http://colorbrewer2.org/">ColorBrewer</a>
   */
  private static final float[][] purpleTones = {
    {78, 49, 95},
    {85, 53, 104},
    {92, 58, 112},
    {99, 62, 121},
    {106, 67, 130},
    {114, 72, 139},
    {121, 76, 148},
    {128, 81, 158},
    {136, 86, 167},
    {136, 89, 169},
    {136, 92, 172},
    {135, 95, 174},
    {135, 97, 176},
    {135, 100, 178},
    {135, 103, 180},
    {135, 106, 182},
    {135, 108, 184},
    {135, 111, 186},
    {135, 114, 188},
    {135, 116, 190},
    {135, 119, 191},
    {135, 121, 193},
    {135, 124, 195},
    {135, 127, 196},
    {136, 129, 198},
    {136, 132, 199},
    {136, 134, 201},
    {137, 137, 202},
    {137, 139, 204},
    {138, 142, 205},
    {138, 144, 207},
    {139, 146, 208},
    {140, 149, 209},
    {141, 151, 210},
    {141, 154, 212},
    {142, 156, 213},
    {143, 158, 214},
    {144, 161, 215},
    {146, 163, 216},
    {147, 165, 217},
    {148, 168, 218},
    {149, 170, 219},
    {151, 172, 220},
    {152, 175, 221},
    {154, 177, 222},
    {156, 179, 223},
    {157, 181, 224},
    {159, 184, 225},
    {161, 186, 226},
    {163, 188, 226},
    {165, 190, 227},
    {167, 193, 228},
    {169, 195, 229},
    {171, 197, 230},
    {174, 199, 230},
    {176, 201, 231},
    {178, 203, 232},
    {181, 205, 233},
    {183, 208, 233},
    {186, 210, 234},
    {189, 212, 235},
    {191, 214, 235},
    {194, 216, 236},
    {197, 218, 237},
    {200, 220, 238},
    {203, 222, 238},
    {205, 224, 239},
    {208, 226, 240},
    {211, 228, 241},
    {215, 230, 242},
    {218, 232, 242},
    {221, 234, 243},
    {224, 236, 244}
  };

  private static final float[][] redToYellowToWhite = {
    {255, 0, 0},
    {255, 32, 0},
    {255, 64, 0},
    {255, 96, 0},
    {255, 128, 0},
    {255, 160, 0},
    {255, 192, 0},
    {255, 224, 0},
    {255, 255, 255},};

  private static final float rainbow[][] = {
    {0, 0, 255},
    {0, 255, 0},
    {230, 230, 0}, // yellow toned down a bit
    {255, 0, 0}
  };

  private static final float[][] linearGray = {
    {64, 64, 64},
    {232, 232, 232}
  };

  static final float[][] topoElev = {
    {0, 60, 16},
    {9, 87, 2},
    {0, 142, 0},
    {38, 202, 0},
    {173, 231, 45},
    {246, 252, 83},
    {204, 150, 52},
    {138, 29, 0},
    {239, 222, 222},};

  static final float[][] monotonicLuminance = {
    {4, 4, 4},
    {10, 3, 8},
    {13, 4, 11},
    {16, 5, 14},
    {18, 5, 16},
    {21, 6, 18},
    {22, 7, 19},
    {24, 8, 21},
    {26, 8, 22},
    {27, 9, 24},
    {28, 10, 25},
    {30, 11, 26},
    {31, 12, 27},
    {32, 12, 28},
    {33, 13, 29},
    {35, 14, 31},
    {36, 14, 32},
    {37, 15, 32},
    {38, 15, 33},
    {39, 16, 34},
    {40, 17, 35},
    {41, 17, 36},
    {42, 18, 38},
    {43, 19, 38},
    {44, 19, 39},
    {46, 20, 41},
    {46, 20, 45},
    {46, 21, 50},
    {45, 21, 55},
    {45, 21, 60},
    {45, 22, 64},
    {45, 23, 67},
    {45, 23, 71},
    {45, 24, 75},
    {45, 24, 77},
    {45, 25, 81},
    {45, 25, 84},
    {44, 26, 87},
    {44, 27, 90},
    {45, 27, 92},
    {45, 28, 95},
    {44, 29, 98},
    {44, 29, 100},
    {44, 30, 103},
    {44, 31, 106},
    {44, 31, 109},
    {44, 32, 110},
    {44, 33, 113},
    {44, 34, 116},
    {43, 34, 118},
    {42, 35, 121},
    {40, 38, 120},
    {38, 40, 119},
    {36, 42, 120},
    {34, 44, 120},
    {33, 46, 120},
    {32, 47, 120},
    {31, 49, 121},
    {30, 50, 122},
    {30, 51, 123},
    {29, 52, 123},
    {29, 53, 125},
    {28, 55, 125},
    {28, 56, 126},
    {27, 57, 127},
    {28, 58, 128},
    {28, 59, 129},
    {27, 60, 129},
    {27, 61, 131},
    {27, 62, 132},
    {27, 63, 133},
    {28, 64, 134},
    {27, 65, 135},
    {27, 66, 136},
    {27, 68, 137},
    {27, 69, 138},
    {25, 71, 136},
    {22, 73, 134},
    {21, 74, 133},
    {20, 76, 131},
    {17, 78, 129},
    {16, 79, 128},
    {15, 81, 126},
    {14, 82, 125},
    {10, 84, 123},
    {10, 85, 122},
    {9, 87, 120},
    {8, 88, 119},
    {7, 89, 118},
    {6, 91, 117},
    {4, 92, 115},
    {4, 94, 114},
    {4, 95, 114},
    {3, 96, 112},
    {1, 98, 111},
    {1, 99, 110},
    {0, 100, 109},
    {0, 101, 108},
    {0, 103, 107},
    {0, 104, 106},
    {0, 105, 105},
    {0, 107, 104},
    {0, 108, 101},
    {0, 110, 100},
    {0, 111, 99},
    {0, 112, 98},
    {0, 114, 96},
    {0, 115, 95},
    {0, 116, 93},
    {0, 118, 92},
    {0, 119, 90},
    {0, 120, 89},
    {0, 121, 88},
    {0, 123, 86},
    {0, 124, 85},
    {0, 125, 83},
    {0, 127, 82},
    {0, 128, 80},
    {0, 129, 79},
    {0, 131, 77},
    {0, 132, 75},
    {0, 133, 73},
    {0, 134, 72},
    {0, 136, 70},
    {0, 137, 68},
    {0, 138, 66},
    {0, 139, 65},
    {0, 141, 64},
    {0, 142, 63},
    {0, 143, 61},
    {0, 145, 60},
    {0, 146, 60},
    {0, 147, 58},
    {0, 149, 57},
    {0, 150, 56},
    {0, 151, 55},
    {0, 153, 53},
    {0, 154, 52},
    {0, 155, 51},
    {0, 157, 50},
    {0, 158, 48},
    {0, 159, 47},
    {0, 160, 45},
    {0, 162, 44},
    {0, 163, 42},
    {0, 164, 41},
    {0, 165, 39},
    {0, 167, 36},
    {0, 168, 34},
    {0, 169, 31},
    {0, 170, 23},
    {0, 169, 8},
    {9, 170, 0},
    {20, 171, 0},
    {29, 172, 0},
    {35, 173, 0},
    {40, 174, 0},
    {45, 175, 0},
    {48, 176, 0},
    {52, 177, 0},
    {55, 178, 0},
    {59, 179, 0},
    {61, 180, 0},
    {64, 181, 0},
    {66, 182, 0},
    {68, 183, 0},
    {71, 184, 0},
    {73, 185, 0},
    {76, 186, 0},
    {78, 187, 0},
    {79, 188, 0},
    {81, 189, 0},
    {83, 190, 0},
    {85, 191, 0},
    {87, 192, 0},
    {92, 193, 0},
    {99, 193, 0},
    {106, 193, 0},
    {114, 193, 0},
    {119, 194, 0},
    {125, 194, 0},
    {130, 194, 0},
    {135, 195, 0},
    {140, 195, 0},
    {145, 195, 0},
    {149, 196, 0},
    {153, 196, 0},
    {157, 197, 0},
    {161, 197, 0},
    {165, 197, 0},
    {169, 198, 0},
    {172, 198, 0},
    {176, 199, 0},
    {180, 199, 0},
    {184, 199, 0},
    {186, 200, 0},
    {190, 201, 0},
    {193, 201, 0},
    {197, 201, 0},
    {200, 202, 0},
    {201, 201, 24},
    {203, 202, 51},
    {206, 202, 65},
    {207, 203, 77},
    {209, 203, 87},
    {212, 203, 95},
    {213, 204, 103},
    {215, 205, 109},
    {218, 205, 116},
    {219, 206, 121},
    {221, 207, 127},
    {223, 207, 132},
    {226, 207, 138},
    {227, 208, 143},
    {229, 209, 147},
    {231, 209, 151},
    {232, 210, 155},
    {235, 211, 159},
    {237, 211, 164},
    {238, 212, 168},
    {240, 212, 172},
    {243, 213, 175},
    {243, 214, 179},
    {245, 214, 183},
    {248, 215, 186},
    {248, 216, 189},
    {248, 218, 193},
    {247, 219, 195},
    {247, 220, 198},
    {247, 222, 201},
    {248, 223, 204},
    {247, 224, 206},
    {247, 226, 209},
    {247, 227, 211},
    {247, 229, 214},
    {247, 230, 216},
    {247, 231, 218},
    {247, 232, 220},
    {248, 234, 224},
    {247, 235, 225},
    {247, 236, 229},
    {247, 238, 231},
    {247, 239, 232},
    {248, 240, 235},
    {248, 242, 237},
    {247, 243, 239},
    {248, 244, 241},
    {248, 246, 244},
    {248, 247, 246},
    {248, 248, 248},
    {249, 249, 249},
    {251, 251, 251},
    {252, 252, 252},
    {253, 253, 253},
    {254, 254, 254},
    {255, 255, 255},};

  private static Recipe[] namedPalettes = {
    new Recipe("BlueToYellow", paletteB2Y),
    new Recipe("PurpleTones", purpleTones),
    new Recipe("RedToYellowToWhite", redToYellowToWhite),
    new Recipe("Rainbow", rainbow),
    new Recipe("LinearGray", linearGray),
    new Recipe("Elevation", topoElev),
    new Recipe("MonotonicLuminance", monotonicLuminance)
  };

  private final float[][] palette;
  private final String name;

  /**
   * Construct a palette colorizer using the palette specified as
   * an array of red/green/blue values in the range 0 to 255.
   *
   * @param name the name of the palette (null if not used)
   * @param rgb an n by 3 array of rgb values.
   */
  public TestPalette(String name, final float[][] rgb) {
    if (name == null || name.trim().isEmpty()) {
      this.name = "Unnamed";
    } else {
      this.name = name;
    }
    palette = new float[rgb.length][3];
    for (int i = 0; i < rgb.length; i++) {
      palette[i] = Arrays.copyOf(rgb[i], 3);
    }
  }

  /**
   * Gets an instance of the palette colorizer using
   * the blue-to-yellow palette. The palette used is based on
   * the CIE LCH model which tends to give better color transitions
   * for the blue-to-yellow range than the simpler RGB model.
   *
   * @return a valid instance.
   */
  public static TestPalette getBlueToYellowPalette() {
    return new TestPalette("BlueToYellow", paletteB2Y);
  }

  /**
   * Gets an instance of the palette colorizer using
   * the default palette. The choice of palette for this routine
   * is undefined and subject to change.
   *
   * @return a valid instance.
   */
  public static TestPalette getDefaultPalette() {
    return getRainbowPalette();
  }

  /**
   * Gets an instance of the palette colorizer using
   * the purple-tones palette. The palette used is based on
   * the CIE LCH model which tends to give better color transitions
   * for the blue-to-yellow range than the simpler RGB model.
   *
   * @return a valid instance.
   */
  public static TestPalette getPurpleTonesPalette() {
    return new TestPalette("PurpleTones", purpleTones);
  }

  /**
   * Constructs an instance of the palette colorizer using
   * the red-to-yellow-to-white palette.
   *
   * @return a valid instance.
   */
  public static TestPalette getRedToYellowToWhitePalette() {
    return new TestPalette("RedToYellowToWhite", redToYellowToWhite);
  }

  /**
   * Constructs an instance of the palette colorizer using
   * the rainbow palette.
   *
   * @return a valid instance.
   */
  public static TestPalette getRainbowPalette() {
    return new TestPalette("Rainbow", rainbow);
  }

  /**
   * Constructs an instance of the palette colorizer using
   * the a linear interpolation of values in the RGB color space.
   *
   * @return a valid instance.
   */
  public static TestPalette getLinearGrayPalette() {
    return new TestPalette("Linear Gray", linearGray);
  }

  /**
   * Default constructor using the blue to yellow palette.
   */
  public TestPalette() {
    this.name = "Default";
    this.palette = paletteB2Y;
  }

  /**
   * Assigns a color value in the ARGB system to a z value based
   * on its position within a range of values and the palette associated
   * with this instance.
   * <p>
   * The alpha channel value (high-order byte) for the return is always
   * set to fully opaque for valid inputs. If given a
   * undefined input value (Double.NaN), a zero (fully transparent)
   * value will be returned. If given a z value outside the specified
   * region, its value will be constrained as necessary.
   *
   * @param z the z value of interest
   * @param zMin the value associated with the minimum-index color
   * in the palette.
   * @param zMax the value associated with the maximum-index color
   * in the palette.
   * @return an integer in the ARGB format
   */
  public int getARGB(double z, double zMin, double zMax) {
    double t = (palette.length - 1) * (z - zMin) / (zMax - zMin);
    if (Double.isNaN(t)) {
      return 0;
    }

    int r, g, b;

    if (t <= 0) {
      r = (int) palette[0][0];
      g = (int) palette[0][1];
      b = (int) palette[0][2];
    } else if (t >= palette.length - 1) {
      r = (int) palette[palette.length - 1][0];
      g = (int) palette[palette.length - 1][1];
      b = (int) palette[palette.length - 1][2];
    } else {
      int i = (int) Math.floor(t);
      double s = t - i;
      r = (int) (s * (palette[i + 1][0] - palette[i][0]) + palette[i][0]);
      g = (int) (s * (palette[i + 1][1] - palette[i][1]) + palette[i][1]);
      b = (int) (s * (palette[i + 1][2] - palette[i][2]) + palette[i][2]);
    }
    return ((((0xff00 | r) << 8) | g) << 8) | b;

  }

  /**
   * Assigns a Color object to a z value based
   * on its position within a range of values and the palette associated
   * with this instance.
   * <p>
   * The alpha channel value (high-order byte) for the return is always
   * set to fully opaque for valid inputs. If given a
   * undefined input value (Double.NaN), a zero (fully transparent)
   * value will be returned. If given a z value outside the specified
   * region, its value will be constrained as necessary.
   *
   * @param z the z value of interest
   * @param zMin the value associated with the minimum-index color
   * in the palette.
   * @param zMax the value associated with the maximum-index color
   * in the palette.
   * @return an integer in the ARGB format
   */
  public Color getColor(double z, double zMin, double zMax) {
    return new Color(getARGB(z, zMin, zMax));
  }

  /**
   * Gets the palette specified by name
   *
   * @param paletteName a valid string matching one a named palette
   * defined by this class.
   * @return if found, a valid palette; otherwise, a null
   */
  public static TestPalette getPaletteByName(String paletteName) {
    if (paletteName == null || paletteName.isEmpty()) {
      throw new NullPointerException(
        "Null or empty specification for palette name");
    }
    String s = paletteName.trim();
    for (Recipe r : TestPalette.namedPalettes) {
      if (r.name.equalsIgnoreCase(s)) {
        return r.palette;
      }
    }
    return null;
  }

  /**
   * Gets a list of the names of palettes defined by this class.
   *
   * @return a valid, non-empty list.
   */
  public static List<String> getPaletteNames() {
    List<String> aList = new ArrayList<>();
    for (Recipe r : TestPalette.namedPalettes) {
      aList.add(r.name);
    }
    return aList;
  }

  /**
   * Gets the name of the palette
   *
   * @return a valid string.
   */
  public String getName() {
    return name;
  }

  public static Icon getIconByName(String name, int width, int height) {
    TestPalette p = getPaletteByName(name);
    BufferedImage bImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics g = bImage.getGraphics();
    for (int i = 0; i < width; i++) {
      Color c = p.getColor(i, 0, width - 1);
      g.setColor(c);
      g.drawRect(i, 0, 2, height + 1);
    }
    return new ImageIcon(bImage);
  }

  public static void loadRecipiesFromStream(InputStream ins, boolean flip)
    throws IOException, NumberFormatException {

    DelimitedReader reader = new DelimitedReader(ins, ',');
    List<String> list;
    List<Recipe> rList = new ArrayList<>();
    for (int i = 0; i < namedPalettes.length; i++) {
      rList.add(namedPalettes[i]);
    }
    list = reader.readStrings();  // read the header line
    while (true) {
      list = reader.readStrings();
      if (list.isEmpty()) {
        break;
      }
      String name = list.get(0);
      int count = Integer.parseInt(list.get(1));
      float[][] rgb = new float[count][3];
      int k = (flip ? count - 1 : 0);

      rgb[k][0] = Integer.parseInt(list.get(2));
      rgb[k][1] = Integer.parseInt(list.get(3));
      rgb[k][2] = Integer.parseInt(list.get(4));
      for (int i = 1; i < count; i++) {
        k = (flip ? count - 1 - i : i);
        list = reader.readStrings();
        if (list.isEmpty()) {
          break;
        }
        rgb[k][0] = Integer.parseInt(list.get(2));
        rgb[k][1] = Integer.parseInt(list.get(3));
        rgb[k][2] = Integer.parseInt(list.get(4));
      }
      rList.add(new Recipe(name, rgb));
    }
    namedPalettes = rList.toArray(new Recipe[rList.size()]);
  }

}
