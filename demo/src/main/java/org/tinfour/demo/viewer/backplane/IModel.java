/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 04/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.tinfour.demo.viewer.backplane;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.tinfour.common.IConstraint;
import org.tinfour.common.IIncrementalTin;
import org.tinfour.common.IMonitorWithCancellation;
import org.tinfour.common.Vertex;
import org.tinfour.utils.LinearUnits;
import org.tinfour.utils.loaders.ICoordinateTransform;

/**
 * Defines methods for accessing and maintaining a collection of
 * sample points (a "model" of a surface).
 */
public interface IModel {

  /**
   * Get the number of samples in the model
   *
   * @return a positive integer.
   */
  int getVertexCount();

  /**
   * Gets the maximum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  double getMaxX();

  /**
   * Gets the minimum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  double getMinX();

  /**
   * Gets the maximum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  double getMaxY();

  /**
   * Gets the minimum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  double getMinY();

  /**
   * Gets the maximum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  double getMaxZ();

  /**
   * Gets the minimum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  double getMinZ();

  /**
   * Gets the estimated nominal point spacing for the model
   *
   * @return a positive value in the units associated with the horizontal
   * coordinate system.
   */
  double getNominalPointSpacing();

  /**
   * Gets the overall area of the model, in the units squared associated
   * with the horizontal coordinate system.
   *
   * @return a positive real value
   */
  double getArea();

  /**
   * Load the metadata and data associated with the file for this model
   * and compile a list of vertices for access by the application.
   *
   * @param monitor an optional monitor for tracking progress (null
   * if not used)
   * @throws IOException If a non-recoverable IO condition is encountered
   * when reading the source file.
   */
  public void load(IMonitorWithCancellation monitor) throws IOException;

  /**
   * Gets the file associated with the model
   *
   * @return a valid file
   */
  public File getFile();

  /**
   * Get the name or identification associated with the model. For
   * file-based models, this value is usually the name (not path)
   * of the file.
   *
   * @return a valid, potentially empty string.
   */
  public String getName();

  /**
   * Gets a <strong>short</strong>description of the model
   *
   * @return a valid string.
   */
  public String getDescription();

  /**
   * Gets the list of vertices currently stored in the model; for efficiency
   * purposes, this is simply a reference to internal storage and
   * <strong>not a safe copy</strong>.
   *
   * @return a valid list (empty if not properly loaded)
   */
  public List<Vertex> getVertexList();

  /**
   * Get a string representation for the specified model coordinates.
   * Some models may include special information about formatting
   * coordinates (such as those that implement geographic coordinates).
   *
   * @param x the x coordinate in the model coordinate system
   * @param y the y coordinate in the model coordinate system
   * @return a valid string (content left to the implementation).
   */
  public String getFormattedCoordinates(double x, double y);

  /**
   * Get a string representation of the specified model X coordinate.
   * Some models may include special information about formatting
   * coordinates based on their magnitude or if they
   * represent geographic coordinates.
   *
   * @param x the x coordinate in the model coordinate system.
   * @return a valid string (content left to the implementation)/
   */
  public String getFormattedX(double x);

  /**
   * Get a string representation of the specified model X coordinate.
   * Some models may include special information about formatting
   * coordinates based on their magnitude or if they
   * represent geographic coordinates.
   *
   * @param y the y coordinate in the model coordinate system.
   * @return a valid string (content left to the implementation)/
   */
  public String getFormattedY(double y);

  /**
   * Indicates whether the content of the model was already loaded.
   * Typically this call is used for file-based models to indicate that data was
   * read from disk.
   *
   * @return true if the model is loaded, otherwise false.
   */
  public boolean isLoaded();

  /**
   * Gets the "reference" TIN that was created when the data was
   * first loaded. It is expected that application code will
   * access this TIN on a read-only basis and will <strong>not</strong>
   * modify it. They are, however, allowed to replace it when a
   * more detailed TIN becomes available.
   *
   * @return a valid TIN to be accessed on a read-only basis.
   */
  public IIncrementalTin getReferenceTin();

  /**
   * Gets the reduction factor for the reference TIN
   *
   * @return a positive value; the large the value the larger the reduction.
   */
  public double getReferenceReductionFactor();

  /**
   * Gets the time required to load the model, in milliseconds.
   * This value is used to format performance reports.
   *
   * @return a positive long integer.
   */
  public long getTimeToLoadInMillis();

  /**
   * Gets the time required to perform the post-loading Hilbert sort
   * on the vertices, in milliseconds. This value is used to format
   * performance reports.
   *
   * @return a positive long integer.
   */
  public long getTimeToSortInMillis();

  /**
   * Gets a list of the vertices that lie on the perimeter
   * of the TIN. The perimeter should be a convex polygon.
   *
   * @return a list of vertices.
   */
  public List<Vertex> getPerimeterVertices();

  /**
   * Gets the linear units for the coordinate system used by the
   * data. It is assumed that the vertical and horizontal coordinate
   * systems will be in the same unit system, though assumption
   * could change in a future implementation.
   *
   * @return a valid enumeration instance
   */
  public LinearUnits getLinearUnits();

  /**
   * Indicates whether the coordinates used by this instance are
   * geographic in nature.
   *
   * @return true if coordinates are geographic; otherwise, false.
   */
  public boolean isCoordinateSystemGeographic();

  /**
   * Get the vertex with the specified vertex index.
   *
   * @param index an arbitrary integer.
   * @return if matched, a valid vertex; otherwise, a null;
   */
  public Vertex getVertexForIndex(int index);

  /**
   * If defined, converts a pair of scaled Cartesian coordinates
   * to a latitude and longitude. If undefined, no operation is performed.
   *
   * @param x a valid scaled Cartesian coordinate
   * @param y a valid scaled Cartesian coordinate
   * @param geo an array of dimension two to store the results
   * (where geo[0]=latitude, geo[1]=longitude)
   *
   */
  public void xy2geo(double x, double y, double[] geo);

  /**
   * If defined, converts a pair of geographic (latitude,longitude)
   * coordinates to a pair of scaled Cartesian coordinates.
   * If undefined, no operation is performed.
   *
   * @param latitude the latitude
   * @param longitude the longitude
   * @param xy a array of dimension two to store the results.
   */
  public void geo2xy(double latitude, double longitude, double[] xy);

  /**
   * Indicates whether the model has constraints and is thus a
   * Constrained Delaunay Triangulation (CDT).
   *
   * @return true if the model has constraints.
   */
  public boolean hasConstraints();

  /**
   * Adds constraints to the model
   *
   * @param constraintsFile if the constraints were derived from a file, a
   * valid reference; otherwise, a null
   * @param constraints a valid list of constraints.
   */
  public void addConstraints(File constraintsFile, List<IConstraint> constraints);

  /**
   * Gets the current list of constraints for the model/
   *
   * @return a valid, potentially empty list.
   */
  public List<IConstraint> getConstraints();

  /**
   * Indicates that the model has a definition for a vertex source.
   * At this time, all implementations define this value as true,
   * though that is subject to change in the future.
   * A model that has a vertex source might not yet have a valid list
   * of vertices if the data from the source has not yet been loaded
   *
   * @return true if the model has a source of vertices; otherwise false.
   */
  public boolean hasVertexSource();

  /**
   * Indicates if the vertex source for the model has been loaded
   *
   * @return true if the vertices for the model have been loaded;
   * otherwise, false.
   */
  public boolean areVerticesLoaded();

  /**
   * Indicates that the model has a definition for a constraint source.
   * At this time, all implementations define this value as true,
   * though that is subject to change in the future.
   * A model that has a constraint source might not yet have a valid list
   * of constraints if the data from the source has not yet been loaded
   *
   * @return true if the model has a source of vertices; otherwise false.
   */
  public boolean hasConstraintsSource();

  /**
   * Indicates if the constraint source for the model has been loaded
   *
   * @return true if the vertices for the model have been loaded;
   * otherwise, false.
   */
  public boolean areConstraintsLoaded();

  /**
   * Gets the coordinate transform associated with the model.
   * If no transform is set, this method will return a null
   * @return if set, a valid transform; otherwise, a null.
   */
  public ICoordinateTransform getCoordinateTransform();


}
