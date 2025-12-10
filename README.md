Tinfour
========

High-Performance 2D Delaunay Triangulation and Related Utilities Written in Java

**Notice**
>
> The Tinfour compiled binary files (Jar files) are available at
> [Sonatype's Maven Central Repository](https://search.maven.org/search?q=Tinfour)
> or the [Maven Central Repository](https://mvnrepository.com/search?q=tinfour)
>
> Notes about the Delaunay triangulation and related algorithms are available at
> [TinfourDocs](https://gwlucastrig.github.io/TinfourDocs/). For project-specific
> documentation and implementation guides for Tinfour users, take a look at our [Wiki](https://github.com/gwlucastrig/Tinfour/wiki).
> 
> Recently, we learned about a new .NET port of Tinfour. To find out more, visit
> [Tinfour.NET](https://github.com/matthewfender/Tinfour.NET) on Github.
>

### Delaunay Triangulation ###
The Delaunay Triangulation defines an optimal form for organizing unstructured or semi-random
sample points into a triangular mesh. That optimality makes the Delaunay Triangulation
a useful tool for interpolation, grid construction, and surface analysis.  

![Surface Models using TINs](doc/images/TwoTins.png "Tinfour rendering of surface models based on a Delaunay Triangulation")

### Tinfour ###
Tinfour is a software library written in Java that provides tools for constructing 
and applying Triangulated Irregular Networks (TINs) that conform to the Delaunay
criterion. Because it is intended to process large data sets,
the implementation gives a great deal of attention to performance and
memory use. On a conventional laptop, Tinfour is capable of processing sample
points at a rate of better than one million points per second.

The Tinfour source code includes extensive documentation. This project also includes
an informal paper that describes the uses, algorithms, and implementation
of the software with enough detail to support potential developers 
who may wish to contribute code or employ Tinfour in their own work. For more details, see
[Data Elements and Algorithms for the Tinfour Libary](http://gwlucastrig.github.io/Tinfour/doc/TinfourAlgorithmsAndDataElements.pdf).
If you would like to discuss the Tinfour project or tell us about your own work, feel free to visit [The Tinfour Discussion Page](https://github.com/gwlucastrig/Tinfour/discussions). 

### The Tinfour Viewer ###
When someone first sees a project like Tinfour, they might reasonably ask
that most thorny of questions "What is it good for?"  To answer that question,
this library includes a simple demonstration application called Tinfour Viewer
that allows the user to exercise the major functions of the Tinfour library.
Using Tinfour Viewer, the user can explore data sets ranging in size from just a few points
up to the millions.

Here's a screenshot from the Tinfour Viewer showing a collection of Lidar elevation data
collected over a section of Interstate highway in the U.S. Northeast.

![Lidar over Guilford, CT](doc/images/TinfourViewerGuilford.jpg "View of Lidar sample collected over Guilford, Connecticut, U.S.A.")

The Tinfour Viewer application is intended to show how the Tinfour library could be integrated
into a full-featured GIS application or other analysis tool. It's a simple
implementation with a minimum of features.
Instructions for setting up and running the Tinfour Viewer application
are provided at the wiki page [Tinfour Execution from the Command Line](https://github.com/gwlucastrig/Tinfour/wiki/Tinfour-Execution-from-the-Command-Line).
Our wiki page attempts to simplify the process of running Tinfour demostration applications as much as possible. It also
explains some of the nuances of the launch procedures and provides the details
you will need to set up a command window and run the command-line variations
for all the various Tinfour applications.

To run the Tinfour software, you must have Java installed
on your system.  If you do not have Java installed on your computer, you may
download an installer for free from 
[Oracle Corporation, Java Downloads](https://java.com/en/download/ "Java downloads from Oracle").

### Sources of Data ###
Lidar is a system for collecting surface elevation using laser measuring devices
mounted on low flying aircraft. It's pretty amazing technology.
There are excellent sources of Lidar data to be had for free, you might start at 
[Free LiDAR Data Sources](http://gisgeography.com/top-6-free-lidar-data-sources/ "Gis Geography")
or the [USGS 3D Elevation Program](https://www.usgs.gov/core-science-systems/ngp/3dep/data-tools "USGS 3D Elevation Program Data and Tools").
The Commonwealth of Pennsylvania was one of the first states to collect and post
a comprehensive survey of lidar data, and they did the job right... Their site includes 
not just lidar data, but the supporting breakline files (Shapefiles), multi-spectral imagery,
and project metadata (including Dewberry reports). Visit this excellent resource at
[PAMAP Lidar Elevation Data](http://www.dcnr.state.pa.us/topogeo/pamap/lidar/index.htm "PAMAP Lidar Elevation Data").

If you just want to download a single Lidar file and view it, we recommend PAMAP Tile 4100133PAS
which can be found at ftp://pamap.pasda.psu.edu/pamap_lidar/cycle1/LAS/South/2006/40000000/41001330PAS.zip.
At 36.7 megabytes, the PAMAP file isn't dainty. But it does contain interesting land features and sufficient
detail to exercise the major functions of the viewer.

### A short demo ###
Recently, we found an earlier Delaunay triangulation project by "The Mad Creator" (Bill Dwyer)
that provided a four-line demo. It was such a elegant way of introducing the package,
that we decided to include one of our own.

    public static void main(String []args) throws Exception {
        IncrementalTin tin = new IncrementalTin(1.0);
        List<Vertex>vertexList = TestVertices.makeRandomVertices(100, 0);
        tin.add(vertexList, null);
        TinRenderingUtility.drawTin(tin, 500, 500, new File("tin.png"));
    }



### Does Tinfour require external project dependencies? ###
The core Tinfour module has no external dependencies. All it requires
is the standard Java API. Thus, you can integrate the core classes
into your own applications without adding unnecessary object code to
your software.

The associated, extended-functionality modules do depend on object code from external projects.
These include modules that can read data from Geographic Information System (GIS) sources
(Shapefiles and airborne Lidar LAS files) and those that perform advanced mathematical
and statistical analysis. These modules and dependencies are described in the Tinfour wiki page
[Tinfour Builds and Dependencies](https://github.com/gwlucastrig/Tinfour/wiki/Tinfour-Builds-and-Dependencies).


### What version of Java is required for Tinfour? ###
Tinfour is compiled under Java 11 or higher.

### Configuring Tinfour in an IDE ###
In terms of its software and package organization, Tinfour has a relatively simple structure, so opening
it in an Integrated Development Environment (IDE) is straight forward.
The major Java IDEs (Netbeans, Eclipse, and IntelliJ) all support direct access to Maven projects.
If you have one of these IDE's you can simply load the Tinfour project and run with it. All work fine.
More hints and background information on configuring Tinfour for use in an IDE are included in the Tinfour wiki page
[Tinfour Builds and Dependencies](https://github.com/gwlucastrig/Tinfour/wiki/Tinfour-Builds-and-Dependencies).
 
### Current Work ###
Development work on the Constrained Conforming Delaunay Triangulation is now complete.

Release 2.1.9 introduces the ability to perform Delaunay Refinement using Ruppert's Algorithm.
Delaunay Refinement is a technique for improving the quality of the triangles
formed by a Delaunay Triangulation through the introduction of synthetic vertices
at well-chosen positions. Refinement techniques are particularly useful in areas near
the boundaries of constraints or near the permimeter of a triangulation. These
areas are often prone to the formation of "skinny" triangles (triangles with two small
angles and one very large angle). 
 
For more detail about the Tinfour project development plans, see the
[Tinfour Project Status and Roadmap](https://github.com/gwlucastrig/Tinfour/wiki/Tinfour-Project-Roadmap) page.
 
### Our Companion Project ###
Visit the [Gridfour Software Project](https://gwlucastrig.github.io/gridfour/) to learn more about our companion
software project dedicated to creating open-source software tools for raster (grid) data sets.

### Conclusion ###
Finally, the whole point of working on a project like Tinfour is to see 
it employed to do something useful. To that end, I welcome ideas, requests, and
recommendations for analysis tools and applications that would
benefit the open source and scientific communities. Got something
to say? You can contact the Tinfour project at contact.tinfour@gmail.com
 
