# Auto-tools

Plugin for JOSM which help on common issues

## Installation

* Open "Preferences" in your JOSM editor
* Click "Update plugins"
* Select "Plugins" and check "auto_tools"
* Confirm with "OK" and restart JOSM

## Actions

### Combine LA import buildings

http://labuildingsimport.com

*Still on testing need to approval*

**How is it working?**

- Get the selection ways.
- Combine the tags *([see labuilding ticket for the tag dicussion](https://github.com/osmlab/labuildings/blob/master/IMPORTING.md))*
- Combine the buildings

**How to use**

* In the **Auto tools** menu, click in **Combine LA buildings**

![combinebuildings](https://cloud.githubusercontent.com/assets/1152236/14869337/66790fc0-0c98-11e6-8084-66b7b4892bb3.gif)

### Knife Tool

This plugin makes easy splitting a way. 
Simplify the workflow of Split Action, since the plugin creates a node and split the way at this node, then the most probable part to be tagged will be selected.

**How is it working?**

* Select "Knife tool" from "Auto Tools" menu, the cursor will change to draw mode.
* Click at the point to split (When in an intersection, specify which way will be splitted by selecting the it)
* The most probable segment to be tagged will be selected automatically.

**Shortcut**

* Knife tool: T 

![knife_tool](https://cloud.githubusercontent.com/assets/10425629/15514799/72838bec-21b0-11e6-9fd4-9b4bed4da0f7.gif)
