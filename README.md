Tinfour
========

High-Performance 2D Delaunay Triangulation and Related Utilities Written in Java


### Delaunay Triangulation ###
The Delaunay Triangulation defines an optimal form for organizing unstructured or semi-random
sample points into a triangular mesh. That optimality makes the Delaunay Triangulation
a useful tool for interpolation, grid construction, and surface analysis.

### Tinfour ###
Tinfour is a Java package that provides tools for constructing and applying
Delaunay Triangulations. Because it is intended to process large data sets,
the implementation gives a great deal of attention to performance and
memory use. On a conventional laptop is is capable of processing sample
points at a rate of better than one million points per second.

The Tinfour source code includes extensive Javadoc. This project also includes
an informal paper that describes the algorithms and implementation
details for the software.

### The Tinfour Viewer ###
When someone first sees a project like Tinfour, they might reasonably ask
that most thorny of questions "What is it good for?"  To try to address that question,
this libary includes a simple demonstration program which allows the user to view
and explore raw data, including samples such as Lidar files that contain
huge numbers of points. To run the demonstrator, you must have Java installed
on your system.  Depending on your setup, you may be able to invoke the viewer
by simply navigating to your copy of the Tinfour "dist" folder and double clicking
the TinfourViewer jar.  From the command window, you can invoke the viewer
using the following:
```
     java -Xmx1500m -jar TinfourViewer-1.0.jar
```
The demonstrator is intended to show how the Tinfour library could be integrated
into a fully feature GIS application or other analysis tool. It's a simple
implementation with a minimum of features. 

### External Project Dependencies ###
Tinfour does have one external project dependency and, at least for now,
you will have to download it manually before you can use Tinfour.  The dependency
is on the well-known Apache Commons Math library.  Download a recenter version
of the library from
[Apache Commons Math Download](http://commons.apache.org/proper/commons-math/download_math.cgi "Apache Commons Math")
store the jar in the Tinfour/lib directory (folder) and Tinfour will be good to go.

### Sources of Data ###
Lidar is a system for collecting surface elevation using laser measuring devices
mounted on low flying aircraft. It's pretty amazing technology.
There are some excellent sources of Lidar data to be had for free, you might start at 
[Free LiDAR Data Sources](http://gisgeography.com/top-6-free-lidar-data-sources/ "Gis Geography")
or [USGS Cebter for LIDAR Information] (http://lidar.cr.usgs.gov/ "USGS")

### A short demo ###
Recently, I found an earlier Delaunay triangulation project by "The Mad Creator" (Bill Dwyer)
that provided a four-line demo. It was such a elegant way of introducing the package,
that I decided to include one of my own.


```Java
  public static void main(String []args) throws Exception {
      IncrementalTin tin = new IncrementalTin(1.0);
      List<Vertex>vertexList = TestVertices.makeRandomVertices(100, 0);
      tin.add(vertexList);
      TinRenderingUtility.drawTin(tin, 500, 500, new File("tin.png"));
  }
``` 


### Why are there External Project Dependencies? ###
The only external dependency in the Tinfour package is the
[Apache Commons Math Library](https://commons.apache.org/proper/commons-math/).
This dependency is required by the linear algebra and statistics functions
needed by the Geographically Weighted Regression classes. If you have
an alternate linear algebra library in your own software, it would be
possible to refactor the Tinfour code to perform the core regression
functions using the alternative. You would, however, have to remove
those functions that specifically require statistics elements
(such as the T-Distribution) or provide your own alternative

### Configuring Tinfour in an IDE ###
Configuring Tinfour in an IDE is pretty simple:
 * Create a Java project
 * Set up a source reference to (installed path)Tinfour/src/main/java
   so that your IDE picks up the packages tinfour.*
 * If you wish to include the test and example applications, 
   set up a source reference to (installed path)/Tinfour/src/test/java
   so your IDE picks up the packages tinfour.test.*
 * Set up a jar reference to (installed path)/Tinfour/lib/commons-math-3.3.6.1.jar
 * Configure the IDE to run TinfourViewerMain.  If you are working with very
   large datasets, you may include the Java runtime option -Xmx1500m or larger
   to increase the heap size.
 
### Future Work ###
The primary feature remaining for future work in Tinfour is support
for the constrained Delaunay triangulation to handle breakline features,
boundaries, and other linear features representing discontinuities in
the modeled surface.

I would very much like to extend the Lidar file reader to be able
to process the compressed LAZ format files. Doing so would 
make file access far more conveient. I'd also like to have the
reader obtain the metadata from LAS files, particular those
elements using Well-Known Text (WKT) format and GeoTIFF tags.
