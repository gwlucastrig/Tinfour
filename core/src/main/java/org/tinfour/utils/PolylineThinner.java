/*
 * Copyright 2017 Gary W. Lucas.
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

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 02/2017  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package org.tinfour.utils;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.tinfour.common.IPolyline;
import org.tinfour.common.Vertex;


/**
 * Provides utilities for reducing the number of points in a polygon or line feature
 * by using Visvalingam's algorithm.
 */
@SuppressWarnings({"PMD.AvoidDeeplyNestedIfStmts", "PMD.CompareObjectsWithEquals"})

public class PolylineThinner {

  private static class Node {

    Vertex vertex;
    double area;
    double areaAbs;
    double x;
    double y;
    boolean prohibited;  // prohibited from removal
    Node prior;
    Node next;

    Node(Vertex v) {
      this.vertex = v;
      this.x = v.getX();
      this.y = v.getY();
    }

    private double computeArea() {
      // the vertex of interest, a, is treated as the origin.
      double cx = prior.x - x;
      double cy = prior.y - y;
      double bx = next.x - x;
      double by = next.y - y;
      area = (bx * cy - by * cx) / 2;
      areaAbs = Math.abs(area);
      return areaAbs;
    }

    private boolean testForProhibition(List<Iterable<Vertex>> testList, double threshold) {
      // in the logic below, note the important assumption that the first
      // feature in the test list is the feature from which the
      // node was constructed.
      double ax = x;
      double ay = y;
      double bx = next.x;
      double by = next.y;
      double cx = prior.x;
      double cy = prior.y;
      double xAB = bx - ax;
      double yAB = by - ay;
      double xBC = cx - bx;
      double yBC = cy - by;
      double xCA = ax - cx;
      double yCA = ay - cy;

      prohibited = false;
      if (areaAbs < threshold / 1.0e+9) {
        // There are two special cases:
        //   The spike.  The edges are basically folded together
        //   The straight line:  The edges lie on the same line
        //   compute CA dot AB
        double dot = xCA * xAB + yCA * yAB;
        if (dot < 0) {
          // the spike case, the turn at vertex A is 180 degrees.
          // spike removal is aways enabled.
          prohibited = false;
          return false;
        }
        double c2 = (xBC * xBC + yBC * yBC) * (1.0 - 1.0e-9);
        boolean selfConstraint = true;
        for (Iterable<Vertex> test : testList) {
          for (Vertex vTest : test) {
            double vx = vTest.getX() - bx;
            double vy = vTest.getY() - by;
            double test2 = Math.abs(xCA * vy - yCA * vx);
            if (test2 < 1.0e-12) {
              dot = xBC * vx + yBC * vy;
              if (1.0e-12 < dot && dot < c2) {
                if (selfConstraint && vTest == vertex) {
                  continue; // tested against self, do not prohibit at this time
                }
                prohibited = true;
                return true;
              }
            }
          }
          selfConstraint = false;
        }
      } else {
        boolean selfConstraint = true;
        for (Iterable<Vertex> test : testList) {
          for (Vertex vTest : test) {
            double vx = vTest.getX();
            double vy = vTest.getY();
            double test0 = xAB * (vy - ay) - yAB * (vx - ax);
            if (test0 >= 0) {
              double test1 = xBC * (vy - by) - yBC * (vx - bx);
              if (test1 >= 0) {
                double test2 = xCA * (vy - cy) - yCA * (vx - cx);
                if (test2 >= 0) {
                  if (selfConstraint &&
                    (  vTest == vertex
                    || vTest == next.vertex
                    || vTest == prior.vertex))
                  {
                    continue;
                  }
                  prohibited = true;
                  return true;
                }
              }
            }
          }
          selfConstraint = false;
        }
      }
      return false;
    }

    @Override
    public String toString() {
      return String.format("%s %12.6f %s", vertex.toString(), area, prohibited ? "prohibited" : "");
    }

  }

  private static class NodeIterator implements Iterator<Vertex> {

    Node node;
    int iNode;
    int nNode;

    NodeIterator(Node firstNode, int nNode) {
      node = firstNode;
      this.nNode = nNode;
    }

    @Override
    public boolean hasNext() {
      return iNode < nNode;
    }

    @Override
    public Vertex next() {
      if(node == null){
          throw new NoSuchElementException("Attempt to access beyond end of iterator");
      }
      iNode++;
      Vertex v = node.vertex;
      node = node.next;
      return v;
    }

    @Override
    public void remove(){
      throw new UnsupportedOperationException("Remove operation not supported");
    }
  }

  private static class NodeIterable implements Iterable<Vertex> {

    Node firstNode;
    int nNode;

    NodeIterable(Node firstNode, int nNode, boolean polygon) {
      if (polygon) {
        this.firstNode = firstNode;
        this.nNode = nNode;
      } else {
        this.firstNode = firstNode.prior;
        this.nNode = nNode + 2;
      }
    }

    @Override
    public Iterator<Vertex> iterator() {
      return new NodeIterator(firstNode, nNode);
    }

  }

  /**
   * Given a feature, apply Visvalingam's algorithm to reduce the
   * complexity of the feature geometry. Features are compared against
   * their neighbors to ensure that reductions in line complexity do not
   * introduce intersections of line segments to the collection.
   *
   * @param feature the feature to be reduced
   * @param neighborList a potentially empty list of neighboring features
   * (null values are allowed); this list is allowed to include the input
   * feature itself.
   * @param threshold the value to be used for the Visvalingham area criterion.
   * @return if points were removed from the feature, a new
   * instance reflecting the changed geometry; if the feature was unchanged,
   * a null.
   */
  public IPolyline thinPoints(
    final IPolyline feature,
    final List<IPolyline> neighborList,
    double threshold) {
    feature.complete();
    List<Vertex> vList = feature.getVertices();
    if (vList.size() < 2) {
      return null;
    }
    Rectangle2D r2d = feature.getBounds();

    int nNode = vList.size();
    int nNodeMin;  // the minimum number of nodes in a feature
    int nRemoved = 0;
    boolean polygon = feature.isPolygon();
    if (polygon) {
      // when completed, polygons are defined so that
      // the initial point is also included as the final point
      if (nNode < 3) {
        return  null;
      }
      nNodeMin = 3;
    } else {
      if (nNode < 3) {
        return null;
      }
      nNodeMin = 0;
    }

    Node firstNode = new Node(vList.get(0));
    Node lastNode = firstNode;
    for (int i = 1; i < nNode; i++) {
      Node node = new Node(vList.get(i)); //NOPMD
      node.prior = lastNode;
      lastNode.next = node;
      lastNode = node;
    }

    // if polygon, close the loop.
    // if linear, move both ends in one step
    if (feature.isPolygon()) {
      firstNode.prior = lastNode;
      lastNode.next = firstNode;
    } else {
      firstNode = firstNode.next;
      lastNode = lastNode.prior;
      nNode -= 2;
    }

    // see if any of the nodes define an area less than the threshold.
    // if not, then no further processing is required.  If so, then
    // initialize a list of other features that overlap the current
    // one (including the current one itself).
    boolean foundAreaLessThanThreshold = false;

    Node node = firstNode;
    for (int i = 0; i < nNode; i++) {
      node.computeArea();
      if (node.areaAbs < threshold) {
        foundAreaLessThanThreshold = true;
      }
      node = node.next;
    }

    if (!foundAreaLessThanThreshold) {
      // we did not detect any area less than threshold,
      // we are done.
      return null;
    }

    // to deal with enclosed vertices, set up a list of all features
    // that could potentially overlay this one, including this one itself
    List<Iterable<Vertex>> testList = new ArrayList<>();
    testList.add(feature);
    if (neighborList != null) {
      for (IPolyline cTest : neighborList) {
        if (cTest == feature) {
          continue; // do not test self, already added to the test list.
        }
        if (cTest.getBounds().intersects(r2d)) {
          testList.add(cTest);
        }
      }
    }

    // initialize the occupancy.  this function is relatively expensive,
    // so we have delayed processing it until it was necessary.
    node = firstNode;
    for (int i = 0; i < nNode; i++) {
      if (node.areaAbs < threshold) {
        node.testForProhibition(testList, threshold);
      }
      node = node.next;
    }

    // remove all non-occupied nodes that form a triangle of
    // of less than the specified area. The loop continues
    // until there's nothing left to remove.
    while (true) {
      testList.set(0, new NodeIterable(firstNode, nNode, polygon)); //NOPMD
      Node minNode = null;
      double minArea = threshold;
      node = firstNode;
      for (int i = 0; i < nNode; i++) {
        if (node.areaAbs < minArea && !node.prohibited) {
          minArea = node.areaAbs;
          minNode = node;
        }
        node = node.next;
      }
      if (minNode == null) {
        break; // no more nodes to be removed
      }

      // remove minNode
      minNode.prior.next = minNode.next;
      minNode.next.prior = minNode.prior;
      if (minNode == firstNode) {
        firstNode = minNode.next;
      }
      nNode--;
      nRemoved++;

      if (nNode > nNodeMin) {
        testList.set(0, new NodeIterable(firstNode, nNode, polygon)); //NOPMD
      } else {
        break;
      }

      // update the two neighbor nodes.  In the case
      // of the non-polygon feature, there will be "open ends"
      // with null prior or next references.  So a pre-check is
      // performed on these values.  In the polygon case, this check
      // is unnecessary, but benign.
      double a;
      if (minNode.prior.prior != null) {
        a = minNode.prior.computeArea();
        if (a < threshold) {
          minNode.prior.testForProhibition(testList, threshold);
        }
      }
      if (minNode.next.next != null) {
        a = minNode.next.computeArea();
        if (a < threshold) {
          minNode.next.testForProhibition(testList, threshold);
        }
      }
    }

       // Diagnostic print out
    //node = firstNode;
    //for (int i = 0; i < nNode; i++) {
    //  System.out.println("" + node.toString());
    //  node = node.next;
    //}

    if (nRemoved > 0) {
      return feature.refactor(new NodeIterable(firstNode, nNode, polygon));
    }
    return null;
  }

}
