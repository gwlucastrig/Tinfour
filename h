[1mdiff --git a/dist/Tinfour-1.0.jar b/dist/Tinfour-1.0.jar[m
[1mindex 4a26636..c71edff 100644[m
Binary files a/dist/Tinfour-1.0.jar and b/dist/Tinfour-1.0.jar differ
[1mdiff --git a/dist/TinfourViewer-1.0.jar b/dist/TinfourViewer-1.0.jar[m
[1mindex 02d3b49..c4abd0f 100644[m
Binary files a/dist/TinfourViewer-1.0.jar and b/dist/TinfourViewer-1.0.jar differ
[1mdiff --git a/src/main/java/tinfour/edge/QuadEdge.java b/src/main/java/tinfour/edge/QuadEdge.java[m
[1mindex 8655f5b..19bc324 100644[m
[1m--- a/src/main/java/tinfour/edge/QuadEdge.java[m
[1m+++ b/src/main/java/tinfour/edge/QuadEdge.java[m
[36m@@ -513,4 +513,6 @@[m [mpublic class QuadEdge implements IQuadEdge {[m
     return new QuadEdgePinwheel(this);[m
   }[m
 [m
[32m+[m
[32m+[m
 }[m
[1mdiff --git a/src/main/java/tinfour/edge/QuadEdgePartner.java b/src/main/java/tinfour/edge/QuadEdgePartner.java[m
[1mindex bffd4fb..2a71500 100644[m
[1m--- a/src/main/java/tinfour/edge/QuadEdgePartner.java[m
[1m+++ b/src/main/java/tinfour/edge/QuadEdgePartner.java[m
[36m@@ -182,12 +182,23 @@[m [mclass QuadEdgePartner extends QuadEdge {[m
 [m
   @Override[m
   public void setConstrainedAreaMemberFlag() {[m
[32m+[m[32m    // The base-flag indicates that the base edge is a constrained[m
[32m+[m[32m    // area member. An edge/dual pair can only be a member of one[m
[32m+[m[32m    // constrained area.  So if we are setting the dual to be[m
[32m+[m[32m    // a constrained area member, we must clear the base-flag[m
     index |= CONSTRAINT_AREA_FLAG;[m
[32m+[m[32m    index &= ~CONSTRAINT_AREA_BASE_FLAG;[m
   }[m
 [m
[32m+[m
   @Override[m
   public boolean isConstraintAreaOnThisSide() {[m
[32m+[m[32m    // An edge/dual pair can only be a member of one[m
[32m+[m[32m    // constrained area. So the constraint is on this side (the side[m
[32m+[m[32m    // of the dual), only if the base flag is set.[m
[32m+[m[32m    // This call is only meaningful if the edge is a member of a[m
[32m+[m[32m    // constraint area.[m
     return  (index & CONSTRAINT_AREA_BASE_FLAG) == 0;[m
   }[m
[31m-[m
[32m+[m[41m [m
 }[m
[1mdiff --git a/src/main/java/tinfour/semivirtual/SemiVirtualIncrementalTin.java b/src/main/java/tinfour/semivirtual/SemiVirtualIncrementalTin.java[m
[1mindex 67cf02e..1c21cc6 100644[m
[1m--- a/src/main/java/tinfour/semivirtual/SemiVirtualIncrementalTin.java[m
[1m+++ b/src/main/java/tinfour/semivirtual/SemiVirtualIncrementalTin.java[m
[36m@@ -2115,6 +2115,9 @@[m [mpublic class SemiVirtualIncrementalTin implements IIncrementalTin {[m
       SemiVirtualEdge md = dm.getDual();[m
 [m
       am.setConstrained(mb.getConstraintIndex());[m
[32m+[m[32m      if(mb.isConstrainedAreaMember()){[m
[32m+[m[32m        am.setConstrainedAreaMemberFlag();[m
[32m+[m[32m      }[m
 [m
       ma.setForward(ad);  // should already be set[m
       ad.setForward(dm);[m
[1mdiff --git a/src/main/java/tinfour/standard/IncrementalTin.java b/src/main/java/tinfour/standard/IncrementalTin.java[m
[1mindex 7a6c72b..022893f 100644[m
[1m--- a/src/main/java/tinfour/standard/IncrementalTin.java[m
[1m+++ b/src/main/java/tinfour/standard/IncrementalTin.java[m
[36m@@ -2428,7 +2428,10 @@[m [mpublic class IncrementalTin implements IIncrementalTin {[m
       QuadEdge md = dm.getDual();[m
 [m
       am.setConstrained(mb.getConstraintIndex());[m
[31m-[m
[32m+[m[32m      if(mb.isConstrainedAreaMember()){[m
[32m+[m[32m        am.setConstrainedAreaMemberFlag();[m
[32m+[m[32m      }[m
[32m+[m[41m      [m
       ma.setForward(ad);  // should already be set[m
       ad.setForward(dm);[m
       dm.setForward(ma);[m
[1mdiff --git a/src/main/java/tinfour/vividsolutions/jts/math/DD.java b/src/main/java/tinfour/vividsolutions/jts/math/DD.java[m
[1mindex 8477198..d78af7c 100644[m
[1m--- a/src/main/java/tinfour/vividsolutions/jts/math/DD.java[m
[1m+++ b/src/main/java/tinfour/vividsolutions/jts/math/DD.java[m
[36m@@ -117,6 +117,7 @@[m [mimport java.io.Serializable;[m
 public strictfp final class DD[m
   implements Serializable, Comparable, Cloneable[m
 {[m
[32m+[m[32m  private static final long serialVersionUID = 1L;[m
   /**[m
    * The value nearest to the constant Pi.[m
    */[m
[1mdiff --git a/src/test/java/tinfour/test/examples/LogoCDT.java b/src/test/java/tinfour/test/examples/LogoCDT.java[m
[1mindex ba46200..aade3c0 100644[m
[1m--- a/src/test/java/tinfour/test/examples/LogoCDT.java[m
[1m+++ b/src/test/java/tinfour/test/examples/LogoCDT.java[m
[36m@@ -83,7 +83,7 @@[m [mfinal public class LogoCDT {[m
           +tinClass.getSimpleName();[m
         List<IConstraint> outlineList = getOutlineConstraints(text);[m
         IIncrementalTin tin = options.getNewInstanceOfTestTin();[m
[31m-        tin.addConstraints(outlineList, false);[m
[32m+[m[32m        tin.addConstraints(outlineList, true);[m
         LogoPanel.plot(tin, title);[m
     }[m
 [m
[1mdiff --git a/src/test/java/tinfour/test/viewer/ColorButton.java b/src/test/java/tinfour/test/viewer/ColorButton.java[m
[1mindex 8454d6b..0707fe6 100644[m
[1m--- a/src/test/java/tinfour/test/viewer/ColorButton.java[m
[1m+++ b/src/test/java/tinfour/test/viewer/ColorButton.java[m
[36m@@ -27,9 +27,9 @@[m [mimport javax.swing.colorchooser.ColorSelectionModel;[m
 import javax.swing.event.ChangeEvent;[m
 import javax.swing.event.ChangeListener;[m
 [m
[31m- [m
[31m-public class ColorButton extends JButton {[m
 [m
[32m+[m[32mpublic class ColorButton extends JButton {[m
[32m+[m[32m    private static final long serialVersionUID = 1L;[m
     Color colorChoice = Color.white;[m
     JColorChooser chooser;[m
     JDialog dialog;[m
[36m@@ -105,7 +105,7 @@[m [mpublic class ColorButton extends JButton {[m
     public Color getColor(){[m
         return colorChoice;[m
     }[m
[31m-    [m
[32m+[m
     @Override[m
     public void paintComponent(Graphics g) {[m
         super.paintComponent(g);[m
[1mdiff --git a/src/test/java/tinfour/test/viewer/ViewOptionsPanel.java b/src/test/java/tinfour/test/viewer/ViewOptionsPanel.java[m
[1mindex 786f059..36aaa1e 100644[m
[1m--- a/src/test/java/tinfour/test/viewer/ViewOptionsPanel.java[m
[1m+++ b/src/test/java/tinfour/test/viewer/ViewOptionsPanel.java[m
[36m@@ -179,6 +179,7 @@[m [mimport tinfour.test.viewer.backplane.ViewOptions.RasterInterpolationMethod;[m
         break;[m
       case LastReturn:[m
         lidarLastReturnButton.setSelected(true);[m
[32m+[m[32m        break;[m
       case AllPoints:[m
         lidarAllPointsButton.setSelected(false);[m
         break;[m
warning: CRLF will be replaced by LF in src/main/java/tinfour/vividsolutions/jts/math/DD.java.
The file will have its original line endings in your working directory.
