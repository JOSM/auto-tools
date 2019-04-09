package org.openstreetmap.josm.plugins.auto_tools.actions;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryUtils.buildReplaceNodeWithNewCommand;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Area;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.ChangeNodesCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DefaultNameFormatter;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.TagCollection;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.conflict.tags.CombinePrimitiveResolverDialog;
import org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryCommand;
import org.openstreetmap.josm.plugins.utilsplugin2.replacegeometry.ReplaceGeometryException;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.UserCancelException;
import org.openstreetmap.josm.tools.Utils;

import edu.princeton.cs.algs4.AssignmentProblem;

public class ReplaceBuilding extends JosmAction {

    public ReplaceBuilding() {
        super(tr("ReplaceBuilding"), null, tr("Replace imported buildings"),
                Shortcut.registerShortcut("AutoTools:ReplaceBuilding", tr("AutoTools:ReplaceBuilding"), KeyEvent.VK_A, Shortcut.CTRL_SHIFT), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (MainApplication.getLayerManager().getEditDataSet() == null) {
            return;
        }

        List<OsmPrimitive> selection = new ArrayList<>(MainApplication.getLayerManager().getEditDataSet().getSelected());
        if (selection.isEmpty()) {
            new Notification(tr("Select at least one building.")).setIcon(JOptionPane.WARNING_MESSAGE).show();
            return;
        }

        if (!Utils.filteredCollection(selection, Node.class).isEmpty()) {
            new Notification(tr("Select only buildings without nodes.")).setIcon(JOptionPane.WARNING_MESSAGE).show();
            return;
        }

        Set<Way> selectedWays = new HashSet<>(Utils.filteredCollection(selection, Way.class));
        Set<Way> newWays = new HashSet<>();

        if (selectedWays.size() == 1) {
            newWays = addWaysIntersectingWaysRecursively(MainApplication.getLayerManager().getEditDataSet().getWays(), selectedWays, newWays);
            if (newWays.size() > 2) {
                new Notification(tr("Select two buildings.")).setIcon(JOptionPane.WARNING_MESSAGE).show();
                return;
            } else if (newWays.size() < 2) {
                new Notification(tr("There is no building to replace")).setIcon(JOptionPane.WARNING_MESSAGE).show();
                return;
            }
            selection.clear();
            selection.addAll(newWays);
        } else {
            if (selectedWays.size() != 2) {
                new Notification(tr("Select maximum two buildings.")).setIcon(JOptionPane.WARNING_MESSAGE).show();
                return;
            }
        }

        //// To replace       
        OsmPrimitive firstObject = selection.get(0);
        OsmPrimitive secondObject = selection.get(1);

        if (firstObject.getKeys() == null || secondObject.getKeys() == null) {
            new Notification(tr("Cannot replace relations.")).setIcon(JOptionPane.WARNING_MESSAGE).show();
            return;
        }

        try {
            ReplaceGeometryCommand replaceCommand = buildReplaceWithNewCommand(firstObject, secondObject);
            if (replaceCommand == null) {
                return;
            }

            UndoRedoHandler.getInstance().add(replaceCommand);
        } catch (IllegalArgumentException | ReplaceGeometryException ex) {
            new Notification(ex.getMessage()).setIcon(JOptionPane.WARNING_MESSAGE).show();
        }
        selection.clear();
    }

    public static ReplaceGeometryCommand buildReplaceWithNewCommand(OsmPrimitive firstObject, OsmPrimitive secondObject) {
        if (firstObject instanceof Node && secondObject instanceof Node) {
            return buildReplaceNodeWithNewCommand((Node) firstObject, (Node) secondObject);
        } else if (firstObject instanceof Way && secondObject instanceof Way) {
            return buildReplaceWayWithNewCommand(Arrays.asList((Way) firstObject, (Way) secondObject));
        } else if (firstObject instanceof Node) {
            return buildUpgradeNodeCommand((Node) firstObject, secondObject);
        } else if (secondObject instanceof Node) {
            return buildUpgradeNodeCommand((Node) secondObject, firstObject);
        } else {
            throw new IllegalArgumentException(tr("This tool can only replace a node, upgrade a node to a way or a multipolygon, or replace a way with a way."));
        }
    }

    protected static List<Command> getTagConflictResolutionCommands(OsmPrimitive source, OsmPrimitive target) throws UserCancelException {
        //source --> imported
        //target--> osm
        Map<String, String> atrributes = new Hashtable<String, String>();

        String valSource = source.getKeys().get("building");
        String valTarget = target.getKeys().get("building");

        if (valSource != null || valTarget != null) {
            if (valSource != null && (valSource.equals("yes") || valSource.equals(valTarget))) {
                source.setKeys(getAttributes(source, valTarget));
                target.setKeys(getAttributes(target, valTarget));
            } else if (valTarget != null && valTarget.equals("yes")) {
                source.setKeys(getAttributes(source, valSource));
                target.setKeys(getAttributes(target, valSource));
            }

            Collection<OsmPrimitive> primitives = Arrays.asList(source, target);

            for (OsmPrimitive osm : primitives) {
                Set<String> keys1 = osm.getKeys().keySet();
                for (String key : keys1) {
                    if (!atrributes.containsKey(key)) {
                        atrributes.put(key, osm.get(key));
                    }
                }
            }
            if (valSource.equals("yes") || valTarget.equals("yes") || valSource.equals(valTarget)) {
                target.setKeys(atrributes);
                source.setKeys(atrributes);
            }
            List<Command> lc = CombinePrimitiveResolverDialog.launchIfNecessary(TagCollection.unionOfAllPrimitives(primitives), primitives, Collections.singleton(target));
            return lc;
        } else {
            new Notification(tr("Cannot replace relations")).setIcon(JOptionPane.WARNING_MESSAGE).show();
            return null;
        }
    }

    private static Map<String, String> getAttributes(OsmPrimitive osm, String value) {
        Map<String, String> atrributes = new Hashtable<String, String>();
        Set<String> keys = osm.getKeys().keySet();
        if (!keys.isEmpty()) {
            for (String key : keys) {
                if (!atrributes.containsKey(key)) {
                    if (key.equals("building")) {
                        atrributes.put(key, value);
                    } else {
                        atrributes.put(key, osm.get(key));
                    }
                }
            }
        }
        return atrributes;
    }

    public static ReplaceGeometryCommand buildUpgradeNodeCommand(Node subjectNode, OsmPrimitive referenceObject) {
        if (!Utils.filteredCollection(subjectNode.getReferrers(), Way.class).isEmpty()) {
            throw new ReplaceGeometryException(tr("Node belongs to way(s), cannot replace."));
        }

        if (referenceObject instanceof Relation && !((Relation) referenceObject).isMultipolygon()) {
            throw new ReplaceGeometryException(tr("Relation is not a multipolygon, cannot be used as a replacement."));
        }

        Node nodeToReplace = null;
        // see if we need to replace a node in the replacement way to preserve connection in history
        if (!subjectNode.isNew()) {
            // Prepare a list of nodes that are not important
            Collection<Node> nodePool = new HashSet<>();
            if (referenceObject instanceof Way) {
                nodePool.addAll(getUnimportantNodes((Way) referenceObject));
            } else if (referenceObject instanceof Relation) {
                for (RelationMember member : ((Relation) referenceObject).getMembers()) {
                    if ((member.getRole().equals("outer") || member.getRole().equals("inner"))
                            && member.isWay()) {
                        // TODO: could consider more nodes, such as nodes that are members of other ways,
                        // just need to replace occurences in all referrers
                        nodePool.addAll(getUnimportantNodes(member.getWay()));
                    }
                }
            } else {
                assert false;
            }
            nodeToReplace = findNearestNode(subjectNode, nodePool);
        }

        List<Command> commands = new ArrayList<>();
        AbstractMap<String, String> nodeTags = (AbstractMap<String, String>) subjectNode.getKeys();

        // merge tags
        try {
            commands.addAll(getTagConflictResolutionCommands(subjectNode, referenceObject));
        } catch (UserCancelException e) {
            // user canceled tag merge dialog
            return null;
        }

        // replace sacrificial node in way with node that is being upgraded
        if (nodeToReplace != null) {
            // node should only have one parent, a way
            Way parentWay = (Way) nodeToReplace.getReferrers().get(0);
            List<Node> wayNodes = parentWay.getNodes();
            int idx = wayNodes.indexOf(nodeToReplace);
            wayNodes.set(idx, subjectNode);
            if (idx == 0 && parentWay.isClosed()) {
                // node is at start/end of way
                wayNodes.set(wayNodes.size() - 1, subjectNode);
            }
            commands.add(new ChangeNodesCommand(parentWay, wayNodes));
            commands.add(new MoveCommand(subjectNode, nodeToReplace.getCoor()));
            commands.add(new DeleteCommand(nodeToReplace));

            // delete tags from node
            if (!nodeTags.isEmpty()) {
                for (String key : nodeTags.keySet()) {
                    commands.add(new ChangePropertyCommand(subjectNode, key, null));
                }

            }
        } else {
            // no node to replace, so just delete the original node
            commands.add(new DeleteCommand(subjectNode));
        }

        MainApplication.getLayerManager().getEditDataSet().setSelected(referenceObject);

        return new ReplaceGeometryCommand(
                tr("Replace geometry for node {0}", subjectNode.getDisplayName(DefaultNameFormatter.getInstance())),
                commands);
    }

    public static ReplaceGeometryCommand buildReplaceWayWithNewCommand(List<Way> selection) {
        // determine which way will be replaced and which will provide the geometry
        boolean overrideNewCheck = false;
        int idxNew = selection.get(0).isNew() ? 0 : 1;
        if (selection.get(1 - idxNew).isNew()) {
            // if both are new, select the one with all the DB nodes
            boolean areNewNodes = false;
            for (Node n : selection.get(0).getNodes()) {
                if (n.isNew()) {
                    areNewNodes = true;
                }
            }
            idxNew = areNewNodes ? 0 : 1;
            overrideNewCheck = true;
            for (Node n : selection.get(1 - idxNew).getNodes()) {
                if (n.isNew()) {
                    overrideNewCheck = false;
                }
            }
        }
        Way referenceWay = selection.get(idxNew);
        Way subjectWay = selection.get(1 - idxNew);

        if (!overrideNewCheck && (subjectWay.isNew() || !referenceWay.isNew())) {
            throw new ReplaceGeometryException(
                    tr("Please select one way that exists in the database and one new way with correct geometry."));
        }
        return buildReplaceWayCommand(subjectWay, referenceWay);
    }

    public static ReplaceGeometryCommand buildReplaceWayCommand(Way subjectWay, Way referenceWay) {

        Area a = MainApplication.getLayerManager().getEditDataSet().getDataSourceArea();
        if (!isInArea(subjectWay, a) || !isInArea(referenceWay, a)) {
            throw new ReplaceGeometryException(tr("The ways must be entirely within the downloaded area."));
        }

        if (hasImportantNode(referenceWay, subjectWay)) {
            throw new ReplaceGeometryException(
                    tr("The way to be replaced cannot have any nodes with properties or relation memberships unless they belong to both ways."));
        }

        List<Command> commands = new ArrayList<>();

        // merge tags
        try {

            commands.addAll(getTagConflictResolutionCommands(referenceWay, subjectWay));

        } catch (UserCancelException e) {
            // user canceled tag merge dialog
            return null;
        }

        // Prepare a list of nodes that are not used anywhere except in the way
        List<Node> nodePool = getUnimportantNodes(subjectWay);

        // And the same for geometry, list nodes that can be freely deleted
        List<Node> geometryPool = new LinkedList<>();
        for (Node node : referenceWay.getNodes()) {
            List<OsmPrimitive> referrers = node.getReferrers();
            if (node.isNew() && !node.isDeleted() && referrers.size() == 1
                    && referrers.get(0).equals(referenceWay) && !subjectWay.containsNode(node)
                    && !hasInterestingKey(node) && !geometryPool.contains(node)) {
                geometryPool.add(node);
            }
        }

        boolean useRobust = Config.getPref().getBoolean("utilsplugin2.replace-geometry.robustAssignment", true);

        // Find new nodes that are closest to the old ones, remove matching old ones from the pool
        // Assign node moves with least overall distance moved
        Map<Node, Node> nodeAssoc = new HashMap<>();
        if (geometryPool.size() > 0 && nodePool.size() > 0) {
            if (useRobust) {  // use robust, but slower assignment
                int gLen = geometryPool.size();
                int nLen = nodePool.size();
                int N = Math.max(gLen, nLen);
                double cost[][] = new double[N][N];
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        cost[i][j] = Double.MAX_VALUE;
                    }
                }

                double maxDistance = Double.parseDouble(Config.getPref().get("utilsplugin2.replace-geometry.max-distance", "1"));
                for (int i = 0; i < nLen; i++) {
                    for (int j = 0; j < gLen; j++) {
                        double d = nodePool.get(i).getCoor().distance(geometryPool.get(j).getCoor());
                        if (d > maxDistance) {
                            cost[i][j] = Double.MAX_VALUE;
                        } else {
                            cost[i][j] = d;
                        }
                    }
                }
                AssignmentProblem assignment;
                try {
                    assignment = new AssignmentProblem(cost);
                    for (int i = 0; i < N; i++) {
                        int nIdx = i;
                        int gIdx = assignment.sol(i);
                        if (cost[nIdx][gIdx] != Double.MAX_VALUE) {
                            nodeAssoc.put(geometryPool.get(gIdx), nodePool.get(nIdx));
                        }
                    }
                    // node will be moved, remove from pool
                    for (Node n : nodeAssoc.values()) {
                        nodePool.remove(n);
                    }
                } catch (Exception e) {
                    useRobust = false;
                    new Notification(
                            tr("Exceeded iteration limit for robust method, using simpler method.")
                    ).setIcon(JOptionPane.WARNING_MESSAGE).show();
                    nodeAssoc = new HashMap<>();
                }
            }
            if (!useRobust) { // use simple, faster, but less robust assignment method
                for (Node n : geometryPool) {
                    Node nearest = findNearestNode(n, nodePool);
                    if (nearest != null) {
                        nodeAssoc.put(n, nearest);
                        nodePool.remove(nearest);
                    }
                }

            }
        }

        // Now that we have replacement list, move all unused new nodes to nodePool (and delete them afterwards)
        for (Node n : geometryPool) {
            if (nodeAssoc.containsKey(n)) {
                nodePool.add(n);
            }
        }

        // And prepare a list of nodes with all the replacements
        List<Node> geometryNodes = referenceWay.getNodes();
        for (int i = 0; i < geometryNodes.size(); i++) {
            if (nodeAssoc.containsKey(geometryNodes.get(i))) {
                geometryNodes.set(i, nodeAssoc.get(geometryNodes.get(i)));
            }
        }

        // Now do the replacement
        commands.add(new ChangeNodesCommand(subjectWay, geometryNodes));

        // Move old nodes to new positions
        for (Node node : nodeAssoc.keySet()) {
            commands.add(new MoveCommand(nodeAssoc.get(node), node.getCoor()));
        }

        // Remove geometry way from selection
        MainApplication.getLayerManager().getEditDataSet().clearSelection(referenceWay);

        // And delete old geometry way
        commands.add(new DeleteCommand(referenceWay));

        // Delete nodes that are not used anymore
        if (!nodePool.isEmpty()) {
            commands.add(new DeleteCommand(nodePool));
        }

        // Two items in undo stack: change original way and delete geometry way
        return new ReplaceGeometryCommand(
                tr("Replace geometry for way {0}", subjectWay.getDisplayName(DefaultNameFormatter.getInstance())),
                commands);
    }

    protected static Node findNearestNode(Node node, Collection<Node> nodes) {
        if (nodes.contains(node)) {
            return node;
        }

        Node nearest = null;
        // TODO: use meters instead of degrees, but do it fast
        double distance = Double.parseDouble(Config.getPref().get("utilsplugin2.replace-geometry.max-distance", "1"));
        LatLon coor = node.getCoor();

        for (Node n : nodes) {
            double d = n.getCoor().distance(coor);
            if (d < distance) {
                distance = d;
                nearest = n;
            }
        }
        return nearest;
    }

    protected static boolean hasInterestingKey(OsmPrimitive object) {
        for (String key : object.getKeys().keySet()) {
            if (!OsmPrimitive.isUninterestingKey(key)) {
                return true;
            }
        }
        return false;
    }

    protected static List<Node> getUnimportantNodes(Way way) {
        List<Node> nodePool = new LinkedList<>();
        for (Node n : way.getNodes()) {
            List<OsmPrimitive> referrers = n.getReferrers();
            if (!n.isDeleted() && referrers.size() == 1 && referrers.get(0).equals(way)
                    && !hasInterestingKey(n) && !nodePool.contains(n)) {
                nodePool.add(n);
            }
        }
        return nodePool;
    }

    protected static boolean hasImportantNode(Way geometry, Way way) {
        for (Node n : way.getNodes()) {
            // if original and replacement way share a node, it's safe to replace
            if (geometry.containsNode(n)) {
                continue;
            }
            //TODO: if way is connected to other ways, warn or disallow?
            for (OsmPrimitive o : n.getReferrers()) {
                if (o instanceof Relation) {
                    return true;
                }
            }
            if (hasInterestingKey(n)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean isInArea(Way way, Area area) {
        if (area == null) {
            return true;
        }

        for (Node n : way.getNodes()) {
            if (!isInArea(n, area)) {
                return false;
            }
        }

        return true;
    }

    protected static boolean isInArea(Node node, Area area) {
        LatLon ll = node.getCoor();
        if (node.isNewOrUndeleted() || area == null || ll == null || area.contains(ll.getX(), ll.getY())) {
            return true;
        }
        return false;
    }

    public static Set<Way> addWaysIntersectingWaysRecursively(Collection<Way> allWays, Collection<Way> initWays, Set<Way> newWays) {
        Set<Way> foundWays = new HashSet<>();
        foundWays.addAll(initWays);
        newWays.addAll(initWays);
        Set<Way> newFoundWays;

        int level = 0, c;
        do {
            c = 0;
            newFoundWays = new HashSet<>();
            for (Way w : foundWays) {
                c += addWaysIntersectingWay(allWays, w, newFoundWays, newWays);
            }
            foundWays = newFoundWays;
            newWays.addAll(newFoundWays);
            level++;
            if (c > Config.getPref().getInt("selection.maxfoundways.intersection", 500)) {
                new Notification(tr("Too many ways are added: {0}!" + c)).setIcon(JOptionPane.WARNING_MESSAGE).show();
            }
        } while (c > 0 && level < Config.getPref().getInt("selection.maxrecursion", 15));

        return newWays;
    }

    static int addWaysIntersectingWay(Collection<Way> ways, Way w, Set<Way> newWays, Set<Way> excludeWays) {
        List<Pair<Node, Node>> nodePairs = w.getNodePairs(false);
        int count = 0;
        for (Way anyway : ways) {
            if (anyway == w) {
                continue;
            }
            if (newWays.contains(anyway) || excludeWays.contains(anyway)) {
                continue;
            }

            List<Pair<Node, Node>> nodePairs2 = anyway.getNodePairs(false);
            loop:
            for (Pair<Node, Node> p1 : nodePairs) {
                for (Pair<Node, Node> p2 : nodePairs2) {
                    if (null != Geometry.getSegmentSegmentIntersection(
                            p1.a.getEastNorth(), p1.b.getEastNorth(),
                            p2.a.getEastNorth(), p2.b.getEastNorth())) {
                        newWays.add(anyway);
                        count++;
                        break loop;
                    }
                }
            }
        }
        return count;
    }

}
