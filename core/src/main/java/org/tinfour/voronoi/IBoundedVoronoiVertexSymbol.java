/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.tinfour.voronoi;

import java.awt.Graphics;

/**
 *
 */
public interface IBoundedVoronoiVertexSymbol {

  /**
   * Draw the icon for a vertex positioned at the indicated coordinates
   *
   * @param g a valid Graphics surface
   * @param x the user (pixel) x coordinate of the vertex
   * @param y the user (pixel) y coordinate of the vertex
   */
  void draw(Graphics g, double x, double y);
  
}
