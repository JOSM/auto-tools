package org.openstreetmap.josm.plugins.auto_tools;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.KeyEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.auto_tools.actions.MergeBuildingsAction;
import org.openstreetmap.josm.plugins.auto_tools.actions.ReplaceBuilding;
import org.openstreetmap.josm.plugins.auto_tools.actions.SplittingTool;

public class AutoTools extends Plugin {

    public AutoTools(PluginInformation info) {
        super(info);
        final JMenu loadTaskMenu = MainApplication.getMenu()
                .addMenu("Auto Tools", tr("Auto Tools"), KeyEvent.VK_K,
                MainApplication.getMenu().getDefaultMenuPos(), HelpUtil.ht("/Plugin/task")
        );
        loadTaskMenu.add(new JMenuItem(new MergeBuildingsAction()));
        loadTaskMenu.add(new JSeparator());
        loadTaskMenu.add(new JMenuItem(new SplittingTool(MainApplication.getMap())));
        loadTaskMenu.add(new JSeparator());
        loadTaskMenu.add(new JMenuItem(new ReplaceBuilding()));
    }
}
