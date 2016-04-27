package org.openstreetmap.josm.plugins.auto_tools.actions;

import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import javax.swing.JOptionPane;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JoinAreasAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;

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
            }
         
            JosmAction build = new JoinAreasAction();

            build.actionPerformed(e);

        }

    }

}
