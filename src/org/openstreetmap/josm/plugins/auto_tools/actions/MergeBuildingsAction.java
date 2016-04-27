package org.openstreetmap.josm.plugins.auto_tools.actions;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JOptionPane;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JoinAreasAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.TagCollection;
import org.openstreetmap.josm.data.osm.Way;
import static org.openstreetmap.josm.gui.mappaint.mapcss.ExpressionFactory.Functions.tr;
import static org.openstreetmap.josm.tools.I18n.trn;
import org.openstreetmap.josm.tools.Shortcut;

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
        LinkedList<Way> ways = new LinkedList<Way>(Main.main.getCurrentDataSet().getSelectedWays());
        if (ways.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Vacio");
        } else {
            //Filtrar los Tags, y sacar un promeriod de ellos
            Map<String, String> atrributes = new HashMap();
            for (OsmPrimitive osm : ways) {
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
                if ((entry.getKey().equals("start_date") || entry.getKey().equals("building:units")) && entry.getValue().contains(";")) {
                    tag = new Tag(entry.getKey(), null);
                } else if ((entry.getKey().equals("height") || entry.getKey().equals("ele")) && entry.getValue().contains(";")) {
                    String[] stringArray = entry.getValue().split(";");
                    Double max = Double.parseDouble(stringArray[0]);
                    for (int index = 1; index < stringArray.length; index++) {
                        Double h = Double.parseDouble(stringArray[index]);
                        if (h > max) {
                            max = h;
                        }
                    }
                    tag = new Tag(entry.getKey(), max.toString());
                } else {
                    tag = new Tag(entry.getKey(), entry.getValue());
                }
                tagCollection.add(tag);
            }
            Main.main.undoRedo.add(new SequenceCommand(tr("revert tags"), MergeAllTags(ways, tagCollection)));
            JosmAction build = new JoinAreasAction();
            build.actionPerformed(e);
        }
    }

    protected Command MergeAllTags(Collection<? extends OsmPrimitive> selection, TagCollection tc) {
        List<Command> commands = new ArrayList<Command>();
        for (String key : tc.getKeys()) {
            String value = tc.getValues(key).iterator().next();
            value = value.equals("") ? null : value;
            commands.add(new ChangePropertyCommand(selection, key, value));
        }
        if (!commands.isEmpty()) {
            String title1 = trn("Pasting {0} tag", "Pasting {0} tags", tc.getKeys().size(), tc.getKeys().size());
            String title2 = trn("to {0} primitive", "to {0} primtives", selection.size(), selection.size());
            return new SequenceCommand(
                    title1 + " " + title2,
                    commands
            );
        }
        return null;
    }
}
