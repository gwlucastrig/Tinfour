/* --------------------------------------------------------------------
 * Copyright 2016 Gary W. Lucas.
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
 * 04/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.viewer.backplane;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import tinfour.common.IIncrementalTin;
import tinfour.common.IMonitorWithCancellation;
import tinfour.common.INeighborEdgeLocator;
import tinfour.common.NeighborEdgeVertex;
import tinfour.common.Vertex;
import tinfour.utils.TinInstantiationUtility;

/**
 * Provides a runnable for rendering in cases where a new model or
 * changed view options require the processing of new TIN or grid elements.
 */
class MvTaskBuildTinAndRender implements IModelViewTask {

  /**
   * An arbitrary setting for how many pixels (on average) we require
   * between vertices when computing thinning.
   */
  private static final int spaceInPixelsWireframe = 50;

  private static final int spaceInPixelsRaster = 2;

  private static final int MAX_VERTICES_FOR_TIN = 50000;

  private final BackplaneManager backplaneManager;

  private final MvComposite composite;
  private final int taskIndex;
  private final IModel model;
  private final ViewOptions view;
  private final int width;
  private final int height;

  private boolean isCancelled;

  MvTaskBuildTinAndRender(
    BackplaneManager backplaneController,
    MvComposite composite,
    int taskIndex) {
    this.backplaneManager = backplaneController;
    this.composite = composite;
    this.model = this.composite.getModel();
    this.view = this.composite.getView();
    this.width = this.composite.getWidth();
    this.height = this.composite.getHeight();
    this.taskIndex = taskIndex;

  }

  @Override
  public void cancel() {
    isCancelled = true;
  }

  @Override
  public boolean isCancelled() {
    return isCancelled;
  }

  @Override
  public void run() {
    if (isCancelled) {
      return; // done
    }

    composite.setWireframeTin(null); // should already be the case anyway
    composite.setReductionForWireframe(Integer.MAX_VALUE);

    if (!model.isLoaded()) {
      System.err.println("Internal error, rendering when model not loaded");
      return;
    }

    int nVertices = model.getVertexCount();
    double mx0 = model.getMinX();
    double my0 = model.getMinY();
    double mx1 = model.getMaxX();
    double my1 = model.getMaxY();
    double area = (mx1 - mx0) * (my1 - my0);
    double nominalPointSpacing = Math.sqrt(area / nVertices / 0.866);

    IIncrementalTin tin = null;
    SelectionResult result = null;
    if (view.isWireframeSelected()) {
      result = selectVerticesForProcessing(
        view.isThinningSelected(),
        spaceInPixelsWireframe,
        MAX_VERTICES_FOR_TIN,
        true);
      List<Vertex> vList = result.list;

      if (isCancelled) {
        return;
      }
      double zVisMin = Double.POSITIVE_INFINITY;
      double zVisMax = Double.NEGATIVE_INFINITY;
      for (Vertex v : vList) {
        double z = v.getZ();
        if (z < zVisMin) {
          zVisMin = z;
        }
        if (z > zVisMax) {
          zVisMax = z;
        }
      }
      composite.recordRangeOfVisibleSamples(zVisMin, zVisMax);

      int n = vList.size();
      backplaneManager.postStatusMessage(taskIndex,
        "Building wireframe TIN from " + n + " vertices");
      TinInstantiationUtility tinOven
        = new TinInstantiationUtility(MvComposite.tinMemoryUseFraction, n);
      tin = tinOven.constructInstance(nominalPointSpacing);
      boolean isBootstrapped = tin.add(vList, null);
      if (!isBootstrapped) {
        backplaneManager.postStatusMessage(taskIndex, "Failed to bootstrap TIN");
        return;
      }

      if (isCancelled) {
        return;
      }

      backplaneManager.postStatusMessage(taskIndex, "TIN complete");
      composite.submitCandidateTinForInterpolation(tin, result.reduction);

      composite.setWireframeTin(tin);
      composite.setReductionForWireframe(result.reduction);

      backplaneManager.postStatusMessage(taskIndex, "Rendering wireframe image");
      BufferedImage bImage = composite.renderWireframe();
      RenderProduct product = new RenderProduct(
        RenderProductType.Wireframe,
        composite,
        bImage);
      backplaneManager.postImageUpdate(this, product);
    }

    if (!isCancelled && (view.isRasterSelected() || view.isHillshadeSelected())) {
      launchRasterProcessing(nominalPointSpacing, tin, result);
    }

  }

  void launchRasterProcessing(double nominalPointSpacing, IIncrementalTin wireframeTin, SelectionResult wireframeSelection) {

    composite.startGridBuildTimer();
    IMonitorWithCancellation monitor = backplaneManager.getProgressMonitor(taskIndex);

    SelectionResult result;
    IIncrementalTin tin;
    if (wireframeSelection != null && wireframeSelection.reduction <= 1 && wireframeTin != null) {
      // the wireframe TIN is full resolution, so it can be used for the raster
      result = wireframeSelection;
      tin = wireframeTin;
    } else {
      boolean thinning = !view.isFullResolutionGridSelected();

      result = selectVerticesForProcessing(
        thinning, spaceInPixelsRaster, MAX_VERTICES_FOR_TIN * 2,
        false);

      List<Vertex> vList = result.list;
      int n = vList.size();
      backplaneManager.postStatusMessage(
        taskIndex, "Building TIN for raster processing from " + n + " vertices");
      TinInstantiationUtility tinOven
        = new TinInstantiationUtility(
          MvComposite.tinMemoryUseFraction,
          n);
      tin = tinOven.constructInstance(nominalPointSpacing);

      boolean isBootstrapped = tin.add(vList, monitor);
      if (!isBootstrapped) {
        monitor.reportDone();
        backplaneManager.postStatusMessage(
          taskIndex, "Failed build TIN, insufficient data");
        return;
      }
      double zVisMin = Double.POSITIVE_INFINITY;
      double zVisMax = Double.NEGATIVE_INFINITY;
      for (Vertex v : vList) {
        double z = v.getZ();
        if (z < zVisMin) {
          zVisMin = z;
        }
        if (z > zVisMax) {
          zVisMax = z;
        }
      }
      composite.recordRangeOfVisibleSamples(zVisMin, zVisMax);
    }
    composite.setReductionForRaster(result.reduction);
    composite.setRasterTin(tin);
    composite.submitCandidateTinForInterpolation(tin, result.reduction);

    monitor.postMessage("Interpolating surface for raster image");
    monitor.reportProgress(0);
    int poolSize = BackplaneExecutor.getInstance().getCorePoolSize();
    int nTasks = poolSize;
    if (nTasks < 4) {
      nTasks = 4;
    }
    int k = height / nTasks;
    if (k == 0) {
      k = height;
    }
    int nBlock = (height + k - 1) / k;
    AtomicInteger blockCounter = new AtomicInteger(nBlock);
    for (int i = 0; i < nBlock; i++) {
      int row0 = i * k;
      int row1 = row0 + k;
      if (row1 > height) {
        row1 = height;
      }

      int nRow = row1 - row0;
      MvTaskBuildRasterBlock blockBuilder
        = new MvTaskBuildRasterBlock( //NOPMD
          backplaneManager,
          composite,
          blockCounter,
          nBlock,
          row0,
          nRow,
          taskIndex,
          monitor);
      BackplaneExecutor.getInstance().runTask(blockBuilder);

    }

  }

  private Vertex getNearbyVertex(double x, double y) {
    IIncrementalTin referenceTin = model.getReferenceTin();
    referenceTin.getNeighborEdgeLocator();
    INeighborEdgeLocator locator = referenceTin.getNeighborEdgeLocator();
    NeighborEdgeVertex nev = locator.getEdgeWithNearestVertex(x, y);
    return nev.getNearestVertex();
  }

  /**
   * Select the vertices for processing or display
   *
   * @param thinningSelected indicates that thinning is specified
   * @param pixelSpacing the target distance between samples, in pixels
   * @param nVertexLimit an approximate maximum number of vertices to be
   * accepted
   * @param factorOfTwoSteps thinning factors are to be powers of two
   * @return
   */
  private SelectionResult selectVerticesForProcessing(
    boolean thinningSelected,
    double pixelSpacing,
    int nVertexLimit,
    boolean factorOfTwoSteps) {
    AffineTransform c2m = composite.c2m;
    int nVertices = model.getVertexCount();
    double mx0 = model.getMinX();
    double my0 = model.getMinY();
    double mx1 = model.getMaxX();
    double my1 = model.getMaxY();
    double area = (mx1 - mx0) * (my1 - my0);
    double nominalPointSpacing = Math.sqrt(area / nVertices / 0.866);

    double uPerPixel = Math.sqrt(Math.abs(c2m.getDeterminant()));

    List<Vertex> vList = model.getVertexList();
    int reduction = 1;

    if (thinningSelected) {
      // First off, when we compute thinning, we don't care about
      // how many vertices are actually included in the viewing area
      // but rather the visual DENSITY of the vertices everywhere.
      // We make the simplifying assumption that the data denisity is
      // uniform. We also assume that the triangular mesh will
      // be organized into a regular tesselation of equilateral triangles.
      // The area of each triangle will be s^2 * sqrt(3)/4 where s is
      // the side of the triangle. For a Delaunay triangulation containing
      // n vertices, there are 2*n triangles.
      //   So solve for n where area = 2 * n * s^2 * sqrt(3)/4;
      double areaInPixels = (area / uPerPixel / uPerPixel);
      double s = pixelSpacing;
      double k = areaInPixels / (s * s * 0.866);
      // only thin the vertex list if k is sufficiently smaller than
      // the number of vertices we wish to plot.
      if (k < nVertices * 0.9) {

        double kSkip = nVertices / k;
        int iSkip = (int) kSkip;
        if (factorOfTwoSteps) {
          // by selecting the skip interval so that it's always a power of two,
          // it ensures that as we zoom in, the vertices that were already
          //
          int iPow2 = (int) Math.floor(Math.log(kSkip) / Math.log(2.0) + 0.5);
          iSkip = 1 << iPow2;
        }
        List<Vertex> thinList = new ArrayList<>();
        for (int i = 0; i < nVertices; i += iSkip) {
          thinList.add(vList.get(i));
        }
        thinList.add(vList.get(vList.size() - 1));

        vList = thinList;
        reduction = iSkip;
      }
    }

    // the thinning above does not have any consideration of what
    // part of the sample is actually visible on the composite.
    // if we're zoomed way in, the thinning may include most or even all
    // of the vertices.  so now we can apply a second level of reduction.
    if (vList.size() > nVertexLimit) {
      // we select an area using two steps:
      //  1. get an area somewhat larger than the composite and
      //     project it into the model's coordinate space to obtain
      //     the bounds for which points we are to select.
      //     some relevant features may be in the vicinity of the visible area,
      //     but outside it. these will need to be included so that the TIN
      //     is properly formed and the edges leading from within the view area
      //     to features nearby can be properly rendered.
      //  2. In the event that the user has panned the image away to one of
      //     the outter edges of the sample data and zoomed way in so that
      //     the visible sample are far apart, the above method may have
      //     collected only a small number of sample points and missed
      //     relevant features because they fall outside the composite.
      //     To remedy that, search the reference TIN to find a vertex in the
      //     model near to the center of the composite.  Then
      //     construct a rectangle in the model's coordinate system that
      //     centered on the vertex an covering an area estimated to be
      //     large enought to contain roughly N_VERTEX_LIMIT samples.
      //  When the user zooms in to an area that is comfortably inside the
      //  area of coverage for the sample points, the results from steps
      //  1 and 2 should be pretty close.  When the user zooms in to an area
      //  close to the edge, the areas may be widely separated.

      double[] c = new double[8];

      // recall that in pixel coordinates, the origin is to the upper-left
      // coordinate of the image and the y coordinates are increasing downwards.
      // in the model, the coordinates use a Cartesian coordinate system.
      // In the code below, we use the Java Rectangle2D class as a convenience
      // for collecting information about the extent of the inclusion area.
      // lower-left corner
      c[0] = -0.25 * width;
      c[1] = 1.5 * height;
      // upper-right corner
      c[2] = 1.5 * width;
      c[3] = -0.25 * height;
      c2m.transform(c, 0, c, 4, 2);
      Rectangle2D r2d = new Rectangle2D.Double(c[4], c[5], c[6] - c[4], c[7] - c[5]);

      // We could be zoomed in so far that no vertex is visible in
      // the display.  Pick the nearest vertex and add it
      // to the bounding rectangle so that some local samples get included in
      // the TIN.  A bit of extra space is allowed based on the nominal
      // point spacing.
      c[0] = width / 2.0;
      c[1] = height / 2.0;
      c2m.transform(c, 0, c, 2, 1);
      Vertex nV = getNearbyVertex(c[2], c[3]);
      double s = Math.sqrt(nVertexLimit) * nominalPointSpacing;
      r2d.add(nV.getX() - s / 2, nV.getY() - s / 2);
      r2d.add(nV.getX() + s / 2, nV.getY() + s / 2);

      double x0 = r2d.getMinX();
      double x1 = r2d.getMaxX();
      double y0 = r2d.getMinY();
      double y1 = r2d.getMaxY();
      List<Vertex> clipList = new ArrayList<Vertex>();
      for (Vertex v : vList) {
        double x = v.getX();
        double y = v.getY();
        if (x0 < x && x < x1 && y0 < y && y < y1) {
          clipList.add(v);
        }
      }
      vList = clipList;
    }

    // TO DO: compare vList.size() to the HARD LIMIT of 50 percent
    // of all available memory.  If it's too big, reduce it anyway
    // no matter what is set for thinning flag.
    if (vList.size() < model.getVertexCount()) {
      // not all vertices in the model are in the list
      // so add the perimeter to the list
      List<Vertex> pList = model.getPerimeterVertices();
      vList.addAll(pList);
    }
    return new SelectionResult(reduction, vList);
  }

  private class SelectionResult {

    final int reduction;
    final List<Vertex> list;

    SelectionResult(int reduction, List<Vertex> list) {
      this.reduction = reduction;
      this.list = list;
    }

    @Override
    public String toString() {
      return "SelectionResult " + list.size() + " vertices, "
        + reduction + ":1 reduction";
    }

  }

}
