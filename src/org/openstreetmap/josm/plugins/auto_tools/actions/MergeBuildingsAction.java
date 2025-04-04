package org.openstreetmap.josm.plugins.auto_tools.actions;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JoinAreasAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.TagCollection;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

/**
 *
 * @author ruben
 */
public class MergeBuildingsAction extends JosmAction {

    public MergeBuildingsAction() {
        super(tr("Combine LA buildings"), null, tr("Combine LA import buildings"),
                Shortcut.registerShortcut("AutoTools:CLAbuildings", tr("AutoTools:CLAbuildings"), KeyEvent.VK_M, Shortcut.ALT_CTRL), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (MainApplication.getLayerManager().getEditDataSet() != null) {
            Relation rela = null;

            LinkedList<Way> ways = new LinkedList<>(MainApplication.getLayerManager().getEditDataSet().getSelectedWays());
            if (ways.isEmpty() || ways.size() == 1) {
                JOptionPane.showMessageDialog(null, "Select at least two ways");
            } else {
                //Filtrar los Tags, y sacar un promerio de ellos
                Map<String, String> atrributes = new Hashtable<>();
                List<Double> areaList = new ArrayList<>();
                List<String> bvList = new ArrayList<>();
                LinkedList<OsmPrimitive> sel = new LinkedList<>();

                for (Way osm : ways) {
                    if (!Utils.filteredCollection(osm.getReferrers(), Relation.class).isEmpty()) {
                        Relation relation = new ArrayList<>(Utils.filteredCollection(osm.getReferrers(), Relation.class)).get(0);
                        Set<String> keys = relation.getKeys().keySet();
                        for (String key : keys) {
                            if (!atrributes.containsKey(key)) {
                                atrributes.put(key, relation.get(key));
                            } else {
                                if (!atrributes.get(key).equals(relation.get(key))) {
                                    String atrr = atrributes.get(key) + ";" + relation.get(key);
                                    atrributes.put(key, atrr);
                                }
                            }
                        }

                        areaList.add(findArea(osm));
                        bvList.add(relation.get("building"));
                        rela = relation;
                        sel.add(relation);
                    } else {
                        Set<String> keys = osm.getKeys().keySet();

                        for (String key : keys) {
                            if (!atrributes.containsKey(key)) {
                                atrributes.put(key, osm.get(key));
                            } else {
                                if (!atrributes.get(key).equals(osm.get(key))) {
                                    String atrr = atrributes.get(key) + ";" + osm.get(key);
                                    atrributes.put(key, atrr);
                                }
                            }
                        }
                        areaList.add(findArea(osm));
                        bvList.add(osm.get("building"));
                    }
                }
                if (bvList.get(areaList.indexOf(Collections.max(areaList))) != null) {
                    atrributes.put("building", bvList.get(areaList.indexOf(Collections.max(areaList))));
                }

                //Convertir  a tag collections
                TagCollection tagCollection = new TagCollection();
                for (Map.Entry<String, String> entry : atrributes.entrySet()) {
                    //https://github.com/osmlab/labuildings/blob/master/IMPORTING.md
                    //lacounty:ain -> ALL
                    //lacount:bld_id -> ALL
                    //start_date -> None if multiple or the one option if there's only one
                    //height -> largest number
                    //ele -> largest number
                    //building:units -> none if different
                    Tag tag;
                    if (("start_date".equals(entry.getKey()) || "building:units".equals(entry.getKey())) && entry.getValue().contains(";")) {
                        tag = new Tag(entry.getKey(), null);
                    } else if (("height".equals(entry.getKey()) || "ele".equals(entry.getKey())) && entry.getValue().contains(";")) {
                        String[] stringArray = entry.getValue().split(";");
                        double max = Double.parseDouble(stringArray[0]);
                        for (int index = 1; index < stringArray.length; index++) {
                            double h = Double.parseDouble(stringArray[index]);
                            if (h > max) {
                                max = h;
                            }
                        }
                        tag = new Tag(entry.getKey(), Double.toString(max));
                    } else {
                        tag = new Tag(entry.getKey(), entry.getValue());
                    }
                    tagCollection.add(tag);
                }
                sel.addAll(ways);

                UndoRedoHandler.getInstance().add(new SequenceCommand(tr("revert tags"), MergeAllTags(rela, sel, tagCollection)));
                JosmAction build = new JoinAreasAction();
                build.actionPerformed(e);
            }
        }
    }

    protected Command MergeAllTags(Relation relation, List<OsmPrimitive> selection, TagCollection tc) {
        List<OsmPrimitive> selectiontemporal = new ArrayList<>();
        Set<Way> selectionways = new HashSet<>(Utils.filteredCollection(selection, Way.class));
        List<Command> commands = new ArrayList<>();

        if (relation != null) {
            for (OsmPrimitive op : selection) {
                if (op.getType() == OsmPrimitiveType.RELATION) {
                    selectiontemporal.add(op);
                    selection.clear();
                    //selection.removeAll(selection);
                    selection = selectiontemporal;
                }
            }
//            for (Way waysel : selectionways) {
//                waysel.setKeys(new Hashtable<String, String>());
//            }
            for (String key : tc.getKeys()) {
                String value = null;
                commands.add(new ChangePropertyCommand(selectionways, key, value));
            }
        }

        for (String key : tc.getKeys()) {
            String value = tc.getValues(key).iterator().next();
            value = "".equals(value) ? null : value;
            commands.add(new ChangePropertyCommand(selection, key, value));
        }

        if (!commands.isEmpty()) {
            String title1 = trn("Pasting {0} tag", "Pasting {0} tags", tc.getKeys().size(), tc.getKeys().size());
            String title2 = trn("to {0} object", "to {0} objects", selection.size(), selection.size());
            return new SequenceCommand(title1 + " " + title2, commands);
        }
        return null;
    }

    protected Double findArea(Way w) {
        Node lastN = null;
        double wayArea = 0.0;
        Double firstSegLength = null;
        boolean isCircle = true;
        for (Node n : w.getNodes()) {
            if (lastN != null && lastN.isLatLonKnown() && n.isLatLonKnown()) {
                final double segLength = lastN.greatCircleDistance(n);
                if (firstSegLength == null) {
                    firstSegLength = segLength;
                }
                if (isCircle && Math.abs(firstSegLength - segLength) > 0.000001) {
                    isCircle = false;
                }
                wayArea += (calcX(n) * calcY(lastN))
                         - (calcY(n) * calcX(lastN));
            }
            lastN = n;
        }

        if (lastN != null && lastN == w.getNodes().iterator().next()) {
            wayArea = Math.abs(wayArea / 2);
        } else {
            wayArea = 0;
        }
        return wayArea;
    }

    @Deprecated
    public static double calcX(LatLon p1) {
        return calcX((ILatLon) p1);
    }

    public static double calcX(ILatLon p1) {
        return p1.lat() * Math.PI * 6_367_000 / 180;
    }

    @Deprecated
    public static double calcY(LatLon p1) {
        return calcY((ILatLon) p1);
    }

    public static double calcY(ILatLon p1) {
        return p1.lon() * (Math.PI * 6_367_000 / 180) * Math.cos(p1.lat() * Math.PI / 180);
    }
}
