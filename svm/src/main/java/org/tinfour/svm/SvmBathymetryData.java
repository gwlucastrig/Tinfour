/* --------------------------------------------------------------------
 * Copyright 2018 Gary W. Lucas.
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
 * 14/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm;

import java.awt.geom.Rectangle2D;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.tinfour.common.IConstraint;
import org.tinfour.common.PolygonConstraint;
import org.tinfour.common.Vertex;
import org.tinfour.gis.utils.ConstraintReaderShapefile;
import org.tinfour.gis.utils.IVerticalCoordinateTransform;
import org.tinfour.gis.utils.VertexReaderLas;
import org.tinfour.gis.utils.VertexReaderShapefile;
import org.tinfour.utils.HilbertSort;
import org.tinfour.utils.Tincalc;
import org.tinfour.utils.loaders.VertexReaderText;

/**
 * A class for loading bathymetry and shoreline data to be used for estimating
 * lake volume.
 */
@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
public class SvmBathymetryData {

  /**
   * Used to set an auxiliary value for a vertex indicating that it
   * is original data from a bathymetry data source.
   */
  public static final int BATHYMETRY_SOURCE = 1;

  /**
   * Used to set an auxiliary value for a vertex indicating that it
   * is data obtained from a supplemental source.
   */
  public static final int SUPPLEMENTAL_SOURCE =2;
  /**
   * Used to set an auxiliary value for a vertex indicating that it
   * is a modeled data point used to adjust flat areas.
   */
  public static final int FLAT_ADJUSTMENT = 3;

  private double zMin;
  private double zMax;
  private double zMean;
  private int zMaxIndex;
  private int zMinIndex;

  private final List<Vertex> soundings = new ArrayList<>();
  private final List<Vertex> supplement = new ArrayList<>();
  private final List<PolygonConstraint> boundaryConstraints = new ArrayList<>();
  private final List<PolygonConstraint> lakeConstraints = new ArrayList<>();
  private final List<PolygonConstraint> islandConstraints = new ArrayList<>();

  private List<Vertex>surveyPerimeter;

  double shoreReferenceElevation;

  private Rectangle2D soundingBounds;
  private Rectangle2D constraintBounds;
  private double nominalPointSpacing;
  private long timeToLoadData;

  private String prjContent;

  private final SvmBathymetryModel bathymetryModel;

  private SvmBathymetryData(){
     bathymetryModel = SvmBathymetryModel.Elevation;
  }

  /**
   * Constructs an instance to load and store bathymetry data.
   * @param model a valid bathymetry model specification
   */
  public SvmBathymetryData(SvmBathymetryModel model){
    if(model==null){
      throw new IllegalArgumentException(
        "A null bathymetry model specification is not supported");
    }
    this.bathymetryModel = model;
  }

  private List<Vertex> loadVertices(
          File vertexFile,
          String dbfBathymetryField,
          IVerticalCoordinateTransform verticalTransform)
          throws IOException {
    String extension = this.getFileExtension(vertexFile);
    List<Vertex> list;
    if ("csv".equalsIgnoreCase(extension) || ".txt".equalsIgnoreCase(extension)) {
      VertexReaderText vertexReader = new VertexReaderText(vertexFile);
      list = vertexReader.read(null);
    } else if ("shp".equalsIgnoreCase(extension)) {
      VertexReaderShapefile vls = new VertexReaderShapefile(vertexFile);
      vls.setDbfFieldForZ(dbfBathymetryField);
      vls.setVerticalCoordinateTransform(verticalTransform);
      list = vls.read(null);
    } else if("las".equalsIgnoreCase(extension) || "laz".equalsIgnoreCase("laz")){
         VertexReaderLas reader = new VertexReaderLas(vertexFile);
         list = reader.read(null);
    } else {
      throw new IllegalArgumentException("Unsupported file format "
              + extension
              + " for input soundings " + vertexFile.getPath());
    }


    return list;

  }

  /**
   * Load main set of soundings from a file. This process is incremental and any
   * new soundings will be added to the list of those already loaded.
   *
   * @param inputSoundingsFile the input file giving soundings.
   * @param dbfBathymetryField the optional string giving the name of the DBF
   * field to be used to extracting data from the input file (used for
   * Shapefiles).
   * @param verticalTransform the optional\transform used to map vertical
   * coordinates to a new value; or null if no transform is to be applied.
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  public void loadSamples(
          File inputSoundingsFile,
          String dbfBathymetryField,
          IVerticalCoordinateTransform verticalTransform)
          throws IOException {

    long time0 = System.nanoTime();

    List<Vertex> list = loadVertices(
            inputSoundingsFile,
            dbfBathymetryField,
            verticalTransform);

    for (Vertex v : list) {
      v.setAuxiliaryIndex(BATHYMETRY_SOURCE);
    }
    soundings.addAll(list);


    String tmpStr = loadShapePrjFile(inputSoundingsFile);
    if(tmpStr!=null){
      this.prjContent = tmpStr;
    }

    double z0 = Double.POSITIVE_INFINITY;
    double z1 = Double.NEGATIVE_INFINITY;
    double zSum = 0;
    int indexOfMaxZ = -1;
    int indexOfMinZ = -1;

    Vertex v0 = soundings.get(0);
    Rectangle2D r2d = new Rectangle2D.Double(v0.getX(), v0.getY(), 0, 0);
    for (Vertex v : soundings) {
      r2d.add(v.getX(), v.getY());
      double z = v.getZ();
      if (z > z1) {
        z1 = z;
        indexOfMaxZ = v.getIndex();
      }
      if (z < z0) {
        z0 = z;
        indexOfMinZ = v.getIndex();
      }
      zSum += z;

    }

    zMin = z0;
    zMax = z1;
    zMean = zSum / soundings.size();
    zMaxIndex = indexOfMaxZ;
    zMinIndex = indexOfMinZ;
    soundingBounds = r2d;
    double area = soundingBounds.getWidth() * soundingBounds.getHeight();
    if (area == 0) {
      throw new IllegalArgumentException(
              "Degenerate set of input samples, "
              + inputSoundingsFile.getPath());
    }
    int n = soundings.size();
    nominalPointSpacing = Tincalc.sampleSpacing(area, n);
    long time1 = System.nanoTime();

    timeToLoadData += time1 - time0;
  }

  /**
   * Load supplemental soundings from a file. This process is incremental and
   * any new soundings will be added to the list of those already loaded.
   *
   * @param inputSupplementFile the input file giving soundings.
   * @param dbfBathymetryField the optional string giving the name of the DBF
   * field to be used to extracting data.
   * @param verticalTransform the optional\transform used to map vertical
   * coordinates to a new value; or null if no transform is to be applied.
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  public void loadSupplement(
          File inputSupplementFile,
          String dbfBathymetryField,
          IVerticalCoordinateTransform verticalTransform)
          throws IOException {

    long time0 = System.nanoTime();

    List<Vertex> list = this.loadVertices(
            inputSupplementFile,
            dbfBathymetryField,
            verticalTransform);
    for (Vertex v : list) {
      v.setAuxiliaryIndex(SvmBathymetryData.SUPPLEMENTAL_SOURCE);
    }
    getSupplements().addAll(list);

    long time1 = System.nanoTime();
    timeToLoadData += time1 - time0;
  }

  /**
   * Load main set of polygon constraints defining the boundary of the body of
   * water. This process is incremental and any new constraints will be added to
   * the list of those already loaded.
   *
   * @param inputBoundaryFile the input file giving constraints.
   * @param dbfFieldForZ the optional string giving the name of the DBF
   * field to be used to extracting data from the input file (used for
   * Shapefiles).
   * @param verticalTransform the optional\transform used to map vertical
   * coordinates to a new value; or null if no transform is to be applied.
   * @throws IOException in the event of an unrecoverable I/O condition
   */
  public void loadBoundaryConstraints(
          File inputBoundaryFile,
          String dbfFieldForZ,
          IVerticalCoordinateTransform verticalTransform) throws IOException {
    long time0 = System.nanoTime();
    try (ConstraintReaderShapefile reader
            = new ConstraintReaderShapefile(inputBoundaryFile)) {
      reader.setDbfFieldForZ(dbfFieldForZ);
      reader.setVerticalCoordinateTransform(verticalTransform);
      List<IConstraint> list = reader.read();
      shoreReferenceElevation = Double.NaN;
      for (IConstraint c : list) {
        if (c instanceof PolygonConstraint) {
          PolygonConstraint p = (PolygonConstraint) c;
          // this is true for both fills and holes based on the assumption
          // that the fills are oriented counter clockwise and the holes
          // are oriented clockwise.  This should be the result if the
          // boundary file was strictly based on polygons that define
          // water areas.  Note that while Shapefiles use the opposite
          // orientation, the reader operation reverses their ordering
          // to reflect the conventions generally used in mathematics.
          p.setApplicationData(true);
          boundaryConstraints.add(p);
          if (p.getArea() > 0) {
            lakeConstraints.add(p);
          } else {
            islandConstraints.add(p);
          }
          List<Vertex> vList = p.getVertices();
          if (Double.isNaN(shoreReferenceElevation)) {
            Vertex v = vList.get(0);
            shoreReferenceElevation = v.getZ();
          }
          if (constraintBounds == null) {
            constraintBounds = p.getBounds();
          } else {
            constraintBounds.add(p.getBounds());
          }
        }
      }
    }

    // if we don't already have the content of a PRJ file, see if there
    // was one for the inputBoundaryFile
    if (prjContent == null) {
      String tmpStr = loadShapePrjFile(inputBoundaryFile);
      if (tmpStr != null) {
        this.prjContent = tmpStr;
      }
    }


    long time1 = System.nanoTime();
    timeToLoadData += time1 - time0;
  }

  /**
   * Get the minimum sounding value in the source data
   *
   * @return the minimum sounding value
   */
  public double getMinZ() {
    return zMin;
  }

  /**
   * Get the maximum sounding value in the source data
   *
   * @return the maximum sounding value
   */
  public double getMaxZ() {
    return zMax;
  }

  /**
   * Get the mean of the sounding values in the source data
   *
   * @return the mean sounding value
   */
  public double getMeanZ() {
    return zMean;
  }

  /**
   * Gets the nominal spacing for the data set.
   *
   * @return a positive floating point value
   */
  public double getNominalPointSpacing() {
    return nominalPointSpacing;
  }

  /**
   * Get a list of the soundings. The result includes the main set of soundings,
   * but does not include any supplemental soundings that may have been
   * loaded.
   *
   * @return the soundings
   */
  public List<Vertex> getSoundings() {
    ArrayList<Vertex> result = new ArrayList<>(soundings.size());
    result.addAll(soundings);
    return result;
  }

  /**
   * Append additional soundings to the soundings collection.
   * @param extraSoundings a valid list of soundings.
   */
  public void addSoundings(List<Vertex>extraSoundings){
    soundings.addAll(extraSoundings);
  }

  /**
   * Gets all bathymetry sounding data, including both the main soundings list
   * and any supplemental data that was loaded.
   *
   * @return a valid list of vertices.
   */
  public List<Vertex> getSoundingsAndSupplements() {
    ArrayList<Vertex> result = new ArrayList<>(soundings.size() + supplement.size());
    result.addAll(soundings);
    result.addAll(supplement);

    return result;
  }

  /**
   * Get a reduced list of the soundings. Intended for diagnostic and rendering
   * purposes.
   *
   * @param nTarget the target number of soundings for the list
   * @return the soundings
   */
  public List<Vertex> getReducedListOfSoundings(int nTarget) {
    int n = soundings.size();
    if (n > 16) {
      HilbertSort hilbertSort = new HilbertSort();
      hilbertSort.sort(soundings);
    }
    ArrayList<Vertex> result = new ArrayList<>(nTarget + 10);
    int skip = (int) (soundings.size() / (double) nTarget + 0.5);
    if (skip == 0) {
      skip = 1;
    }
    int k = 0;
    for (int i = 0; i < nTarget; i++) {
      if (k >= n) {
        break;
      }
      result.add(soundings.get(k));
      k += skip;
    }

    return result;
  }

  /**
   * Get the bounds of the sounding data
   *
   * @return a safe copy of a valid, non-empty rectangle.
   */
  public Rectangle2D getSoundingBounds() {
    return new Rectangle2D.Double(
            soundingBounds.getX(),
            soundingBounds.getY(),
            soundingBounds.getWidth(),
            soundingBounds.getHeight());
  }

  /**
   * Get the overall bounds of the sounding and constraint data
   *
   * @return a safe copy of a valid, non-empty rectangle.
   */
  public Rectangle2D getBounds() {
    Rectangle2D bounds = null;
    if (soundingBounds != null) {
      bounds = new Rectangle2D.Double(
              soundingBounds.getX(),
              soundingBounds.getY(),
              soundingBounds.getWidth(),
              soundingBounds.getHeight());
    }
    if (constraintBounds != null) {
      if (bounds == null) {
        bounds = new Rectangle2D.Double(
                constraintBounds.getX(),
                constraintBounds.getY(),
                constraintBounds.getWidth(),
                constraintBounds.getHeight());
      } else {
        bounds.add(constraintBounds);
      }
    }
    if (bounds == null) {
      return new Rectangle2D.Double(0, 0, 0, 0);
    } else {
      return bounds;
    }
  }

  /**
   * Get the reference elevation for the shoreline (conservation pool elevation)
   *
   * @return a valid floating point value greater than the vertical coordinate
   * of the set of bathymetry samples to be used for analysis.
   */
  public double getShoreReferenceElevation() {
    return shoreReferenceElevation;
  }

  /**
   * Gets a list of constraints defining the boundary of the body of water to be
   * analyzed.
   *
   * @return a valid, potentially empty list
   */
  public List<PolygonConstraint> getBoundaryConstraints() {
    return boundaryConstraints;
  }

  /**
   * Gets the time required to load the input data
   *
   * @return a valid time in nanoseconds\
   */
  public long getTimeToLoadData() {
    return timeToLoadData;
  }

  /**
   * Print a summary of the input data.
   *
   * @param ps a valid print stream such as system output.
   */
  public void printSummary(PrintStream ps) {
    double x0 = soundingBounds.getMinX();
    double y0 = soundingBounds.getMinY();
    double x1 = soundingBounds.getMaxX();
    double y1 = soundingBounds.getMaxY();
    ps.format("Input Data%n");
    ps.format("  Soundings%n");
    ps.format("     Count:               %7d%n", soundings.size());
    ps.format("     Min (x,y,z):         %9.1f, %9.1f, %9.2f (feature %d)%n", x0, y0, zMin, zMinIndex);
    ps.format("     Max (x,y,z):         %9.1f, %9.1f, %9.2f (feature %d)%n", x1, y1, zMax, zMaxIndex);
    ps.format("     width,height:        %9.1f, %9.1f%n", x1 - x0, y1 - y0);
    ps.format("     Est. sample spacing: %9.1f%n", nominalPointSpacing);
  }

  private String getFileExtension(File file) {
    if (file != null) {
      String name = file.getName();
      int i = name.lastIndexOf('.');
      if (i > 0 && i < name.length() - 1) {
        return name.substring(i + 1, name.length());
      }
    }
    return null;
  }

  /**
   * When we are trying to find one of the Shapefile auxilliary files
   * (.dbf, .prj. etc.), we try to format the file extension to match the
   * same case structure as the Shapefile.  This is relevant under Linux which
   * uses case-sensitive file name.   So if we have the extension .SHP, we would
   * use .PRJ, not .prj, etc.
   * @param source the extension from the source string.
   * @param target the extension that we wish to format.
   * @return if successful, the target extension with the proper case structure.
   */
  String matchCase(String source, String target) {
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < target.length(); i++) {
      char s;
      if (i < source.length()) {
        s = source.charAt(i);
      } else {
        s = source.charAt(source.length() - 1);
      }
      char t = target.charAt(i);
      if (Character.isLowerCase(s) && Character.isUpperCase(t)) {
        t = Character.toLowerCase(t);
      } else if (Character.isUpperCase(s) && Character.isLowerCase(t)) {
        t = Character.toUpperCase(t);
      }
      sb.append(t);
    }
    return sb.toString();
  }

  /**
   * Gets a list of those constraints which enclose the data area
   *
   * @return A valid, potentially empty list of polygon constraints oriented in
   * anticlockwise order.
   */
  public List<PolygonConstraint> getLakeConstraints() {
    return lakeConstraints;
  }

  /**
   * Gets a list of those constraints which enclose island areas (non data
   * areas)
   *
   * @return A valid, potentially empty list of polygon constraints oriented in
   * clockwise order.
   */
  public List<PolygonConstraint> getIslandConstraints() {
    return islandConstraints;
  }

  /**
   * Gets a list of supplementary samples.
   *
   * @return A valid, potentially list
   */
  public List<Vertex> getSupplements() {
    return supplement;
  }


  private String loadShapePrjFile(File target) throws IOException {
       String extension = getFileExtension(target);
       if(!"shp".equals(extension)){
         // the input file is not a Shapefile. As a convenience,
         // we handle it, returning a null.
         return null;
       }
       File parent = target.getParentFile();
       if(parent==null){
         parent = new File(".");
       }
       String name = target.getName();
       String baseName = name.substring(0, name.length()-4);
       String targetName =  baseName+'.'+matchCase(extension, "prj");
       File targetFile  = new File(parent, targetName);



       // Assuming that prj files are always ASCII
       StringBuilder sb = new StringBuilder();
       try(FileInputStream fins = new FileInputStream(targetFile);
         BufferedInputStream bins = new BufferedInputStream(fins))
       {
         int c;
         while((c = bins.read())>0){
             sb.append((char)c);
       }
       }
       return sb.toString();
  }

  /**
   * Gets the content of the PRJ file associated with the input.
   * This value will be non-null only if the input comes from a Shapefile
   * (rather than a CSV or text file) and a matching PRJ file is provided.
   * @return if available, a valid String; otherwise, a null.
   */
  public String getShapefilePrjContent(){
    return prjContent;
  }

  /**
   * Gets the bathymetry defined for the soundings in this instances.
   * @return a valid enumeration value.
   */
  public SvmBathymetryModel getBathymetryModel(){
    return bathymetryModel;
  }

  /**
   * Provides a means for the model to attach the perimeter of the
   * triangulation (its convex hull) to the data collection. Intended
   * as a way of propagating the initial collection perimeter into
   * other modules once the data is initially triangulated.
   * @param perimeterVertices a valid list of vertices
   */
  public void setSurveyPerimeter(List<Vertex>perimeterVertices){
    this.surveyPerimeter = perimeterVertices;
  }

  /**
   * Gets the survey perimeter.
   * @return if the perimeter is set, a valid list; otherwise, a null.
   */
  public List<Vertex>getSurveyPerimeter(){
    return surveyPerimeter;
  }
}
