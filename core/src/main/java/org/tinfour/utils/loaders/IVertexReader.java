/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.tinfour.utils.loaders;

import java.io.IOException;
import java.util.List;
import org.tinfour.common.IMonitorWithCancellation;
import org.tinfour.common.Vertex;

/**
 *
 */
public interface IVertexReader {

  /**
   * Gets the coordinate transform associated with this instance. May be null if
   * no coordinate transform was set.
   *
   * @return a valid transform or a null if none was set.
   */
  ICoordinateTransform getCoordinateTransform();

  /**
   * Gets the maximum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  double getXMax();

  /**
   * Gets the minimum x coordinate in the sample
   *
   * @return a valid floating point value
   */
  double getXMin();

  /**
   * Gets the maximum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  double getYMax();

  /**
   * Gets the minimum y coordinate in the sample
   *
   * @return a valid floating point value
   */
  double getYMin();

  /**
   * Gets the maximum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  double getZMax();

  /**
   * Gets the minimum z coordinate in the sample
   *
   * @return a valid floating point value
   */
  double getZMin();

  /**
   * Indicates whether the source data was in geographic coordinates
   *
   * @return true if the source data used geographic coordinates; otherwise,
   * false.
   */
  boolean isSourceInGeographicCoordinates();

  /**
   * Read a collection of vertices from the data source associated
   * with the current implementation and instance.
   * <p>
   * <strong>Monitoring Progress of the Read</strong> Because some input
   * sources can produce quite a large number of vertices and may require
   * several seconds for load operations, this interface supports an optional
   * monitor object.  Implementation classes are not required to support
   * monitors, but should do so if it is feasible.  The Tinfour monitor is
   * intended to support two operations. First, it provides a read operation
   * a mechanism for advising the calling application on the status of
   * its progress. Second, it provides the calling application a mechanism
   * for canceling a read operation. These operations are generally used
   * in support of a user interface, but may have other applications.
   * <p>
   * All implementations are expected to implement logic to handle a null
   * monitor reference.
   * @param monitor a valid instance or a null if no monitoring is desired.
   * @return a valid, potentially list
   * @throws IOException in the event of unreadable data or I/O access
   * exceptions.
   */
  List<Vertex> read(IMonitorWithCancellation monitor) throws IOException;

  /**
   * Sets a coordinate transform to be used for mapping values from the source
   * file to vertex coordinates.
   *
   * @param transform a valid transform or a null if none is to be applied.
   */
  void setCoordinateTransform(ICoordinateTransform transform);
  
}
