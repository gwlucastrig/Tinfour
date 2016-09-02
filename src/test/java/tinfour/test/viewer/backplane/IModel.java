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

package tinfour.test.viewer.backplane;

import java.io.File;
import java.io.IOException;
import java.util.List;
import tinfour.common.IIncrementalTin;
import tinfour.common.IMonitorWithCancellation;
import tinfour.common.Vertex;
import tinfour.utils.LinearUnits;

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
   * @return a positive value in the units associated with the horizontal
   * coordinate system.
   */
  double getNominalPointSpacing();

  /**
   * Gets the overall area of the model, in the units squared associated
   * with the horizontal coordinate system.
   * @return
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
   * Gets the unique serial index associated with this model. Each time
   * a new model instance is constructed, it is assigned a unique serial
   * index number.
   * @return a valid integer.
   */
  public int getModelSerialIndex();


  /**
   * Get the vertex with the specified vertex index.
   * @param index an arbitrary integer.
   * @return if matched, a valid vertex; otherwise, a null;
   */
  public Vertex getVertexForIndex(int index);
}
