package org.openstreetmap.josm.plugins.auto_tools.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.command.SplitWayCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

/**
 *
 * @author samely
 */
public class SplittingTool extends MapMode {

    private Point mousePos;
    private double toleranceMultiplier;
    private static int snapToIntersectionThreshold;
    int counter = 0;

    public SplittingTool(MapFrame mapFrame) {
        super(tr("Knife tool"), "iconknife", tr("Split way."),
                Shortcut.registerShortcut("mapmode:KnifeTool", tr("Mode: {0}", tr("Knife tool")), KeyEvent.VK_T, Shortcut.DIRECT),
                ImageProvider.getCursor("crosshair", null));
    }

    @Override
    public void enterMode() {
        if (!isEnabled()) {
            return;
        }
        super.enterMode();
        toleranceMultiplier = 0.01 * NavigatableComponent.PROP_SNAP_DISTANCE.get();
        MainApplication.getMap().mapView.addMouseListener(this);

    }

    @Override
    public void exitMode() {
        super.exitMode();
        MainApplication.getMap().mapView.removeMouseListener(this);

    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON3 || e.getButton() != MouseEvent.BUTTON1 || !MainApplication.getMap().mapView.isActiveLayerDrawable()) {
            return;
        }

        // Focus to enable shortcuts       
        MainApplication.getMap().mapView.requestFocus();
        MainApplication.getMap().mapView.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (MainApplication.getMap() != null && MainApplication.getMap().mapMode != null
                        && e.getKeyCode() == MainApplication.getMap().mapMode.getShortcut().getAssignedKey()
                        && MainApplication.getLayerManager().getEditLayer() != null) {
                    counter++;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (counter != 0) {
                    if (MainApplication.getMap() != null) {
                        MainApplication.getMap().selectMapMode(MainApplication.getMap().mapModeSelect);
                    }
                    counter = 0;
                }
            }
        });

        //Control key modifiers
        updateKeyModifiers(e);
        mousePos = e.getPoint();

        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        Collection<Command> cmds = new LinkedList<>();
        Collection<OsmPrimitive> newSelection = new LinkedList<>(ds.getSelected());

        List<Way> reuseWays = new ArrayList<>();
        List<Way> replacedWays = new ArrayList<>();

        boolean newNode = false;
        Node n = null;

        n = MainApplication.getMap().mapView.getNearestNode(mousePos, OsmPrimitive::isSelectable);

        if (Utils.filteredCollection(newSelection, Node.class).size() == 1 && Utils.filteredCollection(newSelection, Way.class).isEmpty()) {
            newSelection.clear();
            MainApplication.getLayerManager().getEditDataSet().setSelected(newSelection);
        }

        if (n != null) {
            if (!newSelection.isEmpty()) {
                SplitRoad(n, ds, newSelection);
                MainApplication.getMap().selectMapMode(MainApplication.getMap().mapModeSelect);
                return;
            }
        } else {
            if (n != null) {
                // do not add new node if there is some node within snapping distance
                EastNorth foundPoint = n.getEastNorth();
                double tolerance = MainApplication.getMap().mapView.getDist100Pixel() * toleranceMultiplier;
                if (foundPoint.distance(foundPoint) > tolerance) {
                    n = new Node(foundPoint);
                    newNode = true;
                }

            } else {
                // n==null, no node found in clicked area                
                EastNorth mouseEN = MainApplication.getMap().mapView.getEastNorth(e.getX(), e.getY());
                n = new Node(mouseEN);
                newNode = true;
            }
        }
        //Create new node
        if (newNode) {
            if (n.getCoor().isOutSideWorld()) {
                JOptionPane.showMessageDialog(
                        MainApplication.getMainFrame(),
                        tr("Cannot add a node outside of the world."),
                        tr("Warning"),
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }
            cmds.add(new AddCommand(ds, n));

            // Insert the node into all the nearby way segments
            List<WaySegment> wss = MainApplication.getMap().mapView.getNearestWaySegments(MainApplication.getMap().mapView.getPoint(n), OsmPrimitive::isSelectable);
            insertNodeIntoAllNearbySegments(wss, n, newSelection, cmds, replacedWays, reuseWays);
        }

        if (!cmds.isEmpty()) {
            Command c = new SequenceCommand("Add node into way and connect", cmds);
            UndoRedoHandler.getInstance().add(c);
        }

        //Delete the created node if not in a way
        if (Utils.filteredCollection(n.getReferrers(), Way.class).isEmpty()) {
            MainApplication.getLayerManager().getEditDataSet().removePrimitive(n.getPrimitiveId());

        } else {
            SplitRoad(n, ds, newSelection);
            MainApplication.getMap().selectMapMode(MainApplication.getMap().mapModeSelect);
        }
    }

    private void insertNodeIntoAllNearbySegments(List<WaySegment> wss, Node n, Collection<OsmPrimitive> newSelection,
            Collection<Command> cmds, List<Way> replacedWays, List<Way> reuseWays) {
        Map<Way, List<Integer>> insertPoints = new HashMap<>();
        for (WaySegment ws : wss) {
            List<Integer> is;
            if (insertPoints.containsKey(ws.way)) {
                is = insertPoints.get(ws.way);
            } else {
                is = new ArrayList<>();
                insertPoints.put(ws.way, is);
            }
            is.add(ws.lowerIndex);
        }

        Set<Pair<Node, Node>> segSet = new HashSet<>();

        for (Map.Entry<Way, List<Integer>> insertPoint : insertPoints.entrySet()) {
            Way w = insertPoint.getKey();
            List<Integer> is = insertPoint.getValue();

            Way wnew = new Way(w);

            pruneSuccsAndReverse(is);
            for (int i : is) {
                segSet.add(Pair.sort(new Pair<>(w.getNode(i), w.getNode(i + 1))));
                wnew.addNode(i + 1, n);
            }

            cmds.add(new ChangeCommand(insertPoint.getKey(), wnew));
            replacedWays.add(insertPoint.getKey());
            reuseWays.add(wnew);
        }
        adjustNode(segSet, n);
    }

    private static void adjustNode(Collection<Pair<Node, Node>> segs, Node n) {
        switch (segs.size()) {
            case 0:
                return;
            case 2:
                // This computes the intersection between the two segments and adjusts the node position.
                Iterator<Pair<Node, Node>> i = segs.iterator();
                Pair<Node, Node> seg = i.next();
                EastNorth pA = seg.a.getEastNorth();
                EastNorth pB = seg.b.getEastNorth();
                seg = i.next();
                EastNorth pC = seg.a.getEastNorth();
                EastNorth pD = seg.b.getEastNorth();

                double u = det(pB.east() - pA.east(), pB.north() - pA.north(), pC.east() - pD.east(), pC.north() - pD.north());

                // Check for parallel segments and do nothing if they are
                // In practice this will probably only happen when a way has been duplicated
                if (u == 0) {
                    return;
                }

                // q is a number between 0 and 1
                // It is the point in the segment where the intersection occurs
                // if the segment is scaled to lenght 1
                double q = det(pB.north() - pC.north(), pB.east() - pC.east(), pD.north() - pC.north(), pD.east() - pC.east()) / u;
                EastNorth intersection = new EastNorth(
                        pB.east() + q * (pA.east() - pB.east()),
                        pB.north() + q * (pA.north() - pB.north()));

                // only adjust to intersection if within snapToIntersectionThreshold pixel of mouse click; otherwise
                // fall through to default action.
                // (for semi-parallel lines, intersection might be miles away!)
                if (MainApplication.getMap().mapView.getPoint2D(n).distance(MainApplication.getMap().mapView.getPoint2D(intersection)) < snapToIntersectionThreshold) {
                    n.setEastNorth(intersection);
                    return;
                }
            // fall through
            default:
                EastNorth p = n.getEastNorth();
                seg = segs.iterator().next();
                pA = seg.a.getEastNorth();
                pB = seg.b.getEastNorth();
                double a = p.distanceSq(pB);
                double b = p.distanceSq(pA);
                double c = pA.distanceSq(pB);
                q = (a - b + c) / (2 * c);
                n.setEastNorth(new EastNorth(pB.east() + q * (pA.east() - pB.east()), pB.north() + q * (pA.north() - pB.north())));
        }
    }

    static double det(double a, double b, double c, double d) {
        return a * d - b * c;
    }

    private static void pruneSuccsAndReverse(List<Integer> is) {
        Set<Integer> is2 = new HashSet<>();
        for (int i : is) {
            if (!is2.contains(i - 1) && !is2.contains(i + 1)) {
                is2.add(i);
            }
        }
        is.clear();
        is.addAll(is2);
        Collections.sort(is);
        Collections.reverse(is);
    }

    public void SplitRoad(Node node, DataSet ds, Collection<OsmPrimitive> newSelection) {
        Collection<OsmPrimitive> selectionToSplit = new LinkedList<>(ds.getSelected());
        newSelection.add(node);

        //get way to split
        Way way = null;

        for (Way w : Utils.filteredCollection(node.getReferrers(), Way.class)) {
            if (!w.isUsable() || w.getNodesCount() < 1) {
                continue;
            }
            way = w;
        }

        newSelection.add(way);

        List<Node> selectedNodes = new ArrayList<>(Utils.filteredCollection(newSelection, Node.class));
        List<Way> selectedWays = new ArrayList<>(Utils.filteredCollection(newSelection, Way.class));
        List<Relation> selectedRelations = new ArrayList<>(Utils.filteredCollection(newSelection, Relation.class));

        List<Way> applicableWays = getApplicableWays(selectedWays, selectedNodes);

        if (applicableWays == null) {
            new Notification(
                    tr("The current selection cannot be used for splitting - no node is selected."))
                    .setIcon(JOptionPane.WARNING_MESSAGE)
                    .show();
            return;
        } else if (applicableWays.isEmpty()) {
            new Notification(
                    tr("The selected nodes do not share the same way."))
                    .setIcon(JOptionPane.WARNING_MESSAGE)
                    .show();
            return;
        }

        // If several ways have been found, remove ways that doesn't have selected node in the middle
        if (applicableWays.size() > 1) {
            WAY_LOOP:
            for (Iterator<Way> it = applicableWays.iterator(); it.hasNext();) {
                Way w = it.next();
                for (Node no : selectedNodes) {
                    if (!w.isInnerNode(no)) {
                        it.remove();
                        continue WAY_LOOP;
                    }
                }
            }
        }

        if (applicableWays.isEmpty()) {
            new Notification(
                    tr("The selected node is not in the middle of any way.",
                            "The selected nodes are not in the middle of any way.",
                            selectedNodes.size()))
                    .setIcon(JOptionPane.WARNING_MESSAGE)
                    .show();
            return;
        }
        Way selectedWay = null;

        // Finally, applicableWays contains only one perfect way
        if (selectionToSplit != null && selectionToSplit.size() == 1 && applicableWays.contains(new ArrayList<>(Utils.filteredCollection(selectionToSplit, Way.class)).get(0))) {
            selectedWay = new ArrayList<>(Utils.filteredCollection(selectionToSplit, Way.class)).get(0);
        } else {
            selectedWay = applicableWays.get(0);
        }

        List<List<Node>> wayChunks = SplitWayCommand.buildSplitChunks(selectedWay, selectedNodes);
        if (wayChunks != null) {
            List<OsmPrimitive> sel = new ArrayList<OsmPrimitive>(selectedWays.size() + selectedRelations.size());
            sel.addAll(selectedWays);
            sel.addAll(selectedRelations);
            SplitWayCommand result = SplitWayCommand.splitWay(selectedWay, wayChunks, sel);
            UndoRedoHandler.getInstance().add(result);

            //Select the way to tag
            Way way2 = result.getNewWays().get(0);
            try {
                if (selectedWay.firstNode().equals(way2.firstNode())) {
                    selectTheWay(selectedWay, way2, selectedWay.lastNode(), way2.lastNode(), selectedWay.firstNode());
                } else if (selectedWay.firstNode().equals(way2.lastNode())) {
                    selectTheWay(selectedWay, way2, selectedWay.lastNode(), way2.firstNode(), selectedWay.firstNode());
                } else if (selectedWay.lastNode().equals(way2.firstNode())) {
                    selectTheWay(selectedWay, way2, selectedWay.firstNode(), way2.lastNode(), selectedWay.lastNode());
                } else if (selectedWay.lastNode().equals(way2.lastNode())) {
                    selectTheWay(selectedWay, way2, selectedWay.firstNode(), way2.firstNode(), selectedWay.lastNode());
                }

            } catch (Exception e) {
                Logging.error(e);
            }
        }

//        Main.map.selectMapMode(Main.map.mapModeSelect);
    }

    private List<Way> getApplicableWays(List<Way> selectedWays, List<Node> selectedNodes) {
        if (selectedNodes.isEmpty()) {
            return null;
        }

        // Special case - one of the selected ways touches (not cross) way that we want to split
        if (selectedNodes.size() == 1) {
            Node n = selectedNodes.get(0);
            List<Way> referedWays = new ArrayList<>(Utils.filteredCollection(n.getReferrers(), Way.class));
            Way inTheMiddle = null;
            boolean foundSelected = false;
            for (Way w : referedWays) {

                if (selectedWays.contains(w)) {
                    foundSelected = true;
                }
                if (w.getNode(0) != n && w.getNode(w.getNodesCount() - 1) != n) {
                    if (inTheMiddle == null) {
                        inTheMiddle = w;
                    } else {
                        inTheMiddle = null;
                        break;
                    }
                }
            }
            if (foundSelected && inTheMiddle != null) {
                return Collections.singletonList(inTheMiddle);
            }
        }

        // List of ways shared by all nodes
        List<Way> result = new ArrayList<Way>(Utils.filteredCollection(selectedNodes.get(0).getReferrers(), Way.class));
        for (int i = 1; i < selectedNodes.size(); i++) {
            List<OsmPrimitive> ref = selectedNodes.get(i).getReferrers();
            for (Iterator<Way> it = result.iterator(); it.hasNext();) {
                if (!ref.contains(it.next())) {
                    it.remove();
                }
            }
        }

        // Remove broken ways
        for (Iterator<Way> it = result.iterator(); it.hasNext();) {
            if (it.next().getNodesCount() <= 2) {
                it.remove();
            }
        }

        if (selectedWays.isEmpty()) {
            return result;
        } else {
            // Return only selected ways
            for (Iterator<Way> it = result.iterator(); it.hasNext();) {
                if (!selectedWays.contains(it.next())) {
                    it.remove();
                }
            }
            return result;
        }
    }

    public static void selectTheWay(Way way, Way way2, Node n1, Node n2, Node common) {

        int ws1 = Utils.filteredCollection(n1.getReferrers(), Way.class).size();
        int ws2 = Utils.filteredCollection(n2.getReferrers(), Way.class).size();
        int wsc = Utils.filteredCollection(common.getReferrers(), Way.class).size();

        try {
            if ((ws1 > 2 && ws2 > 2) || (ws1 <= 2 && ws2 <= 2)) {
                if (way.getLength() > way2.getLength()) {
                    MainApplication.getLayerManager().getEditDataSet().setSelected(way2);
                } else {
                    MainApplication.getLayerManager().getEditDataSet().setSelected(way);
                }
            } else if (ws1 > 2 && ws2 <= 2) {
                if (wsc > 2) {
                    MainApplication.getLayerManager().getEditDataSet().setSelected(way2);
                } else {
                    MainApplication.getLayerManager().getEditDataSet().setSelected(way);
                }
            } else if (ws1 <= 2 && ws2 > 2) {
                if (wsc > 2) {
                    MainApplication.getLayerManager().getEditDataSet().setSelected(way);
                } else {
                    MainApplication.getLayerManager().getEditDataSet().setSelected(way2);
                }

            }
        } catch (Exception e) {
            Logging.error(e);
        }

    }

}
