package org.openstreetmap.josm.plugins.auto_tools.actions;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JOptionPane;
import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JoinAreasAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.TagCollection;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import static org.openstreetmap.josm.gui.mappaint.mapcss.ExpressionFactory.Functions.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

/**
 *
 * @author ruben
 */
public class MergeBuildingsAction extends JosmAction {

    public MergeBuildingsAction(String name) {
        super(name, null, name, null, true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        LinkedList<Way> ways = new LinkedList<Way>(Main.main.getCurrentDataSet().getSelectedWays());

        List<LatLon> latLon = new LinkedList<LatLon>();
        if (ways.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Vacio");
        } else {
            Map atrributes = new HashMap();
            for (OsmPrimitive osm : ways) {
                Set<String> keys = osm.getKeys().keySet();
                for (String key : keys) {
                    if (!atrributes.containsKey(key)) {
                        atrributes.put(key, osm.get(key));
                    } else {
                        String atrr = atrributes.get(key) + ";" + osm.get(key);
                        atrributes.put(key, atrr);
                    }
                }
                latLon.add(osm.getBBox().getCenter());

            }
            for (OsmPrimitive osm : ways) {
                Set<String> keys = osm.getKeys().keySet();
                for (String key : keys) {
                    if (atrributes.containsKey(key)) {
//                        Main.main.undoRedo.add(new ChangePropertyCommand(osm, key, atrributes.get(key).toString()));
                        Main.main.undoRedo.add(new SequenceCommand(tr("revert tags"), MergeTags(osm, atrributes)));
                    }
                }
            }
            JosmAction build = new JoinAreasAction();
            build.actionPerformed(e);
//            build.getCurrentDataSet().getSelectedWays();
        }

    }

    protected List<Command> MergeTags(OsmPrimitive primitive, Map atrributes) {
        LinkedList<Command> cmds = new LinkedList<Command>();
        Set<String> keys = primitive.getKeys().keySet();
        for (String key : keys) {
            cmds.add(new ChangePropertyCommand(primitive, key, atrributes.get(key).toString()));
        }
        return cmds;
    }


}
