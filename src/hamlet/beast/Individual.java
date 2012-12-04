/*
 * Copyright (C) 2012 Tim Vaughan <tgvaughan@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package hamlet.beast;

import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Plugin;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Plugin representing an individual member of a population.  Used"
        + "for specifying inheritance in reactions.")
public class Individual extends Plugin {
    
    public Input<Population> populationInput = new Input<Population>(
            "population",
            "Population to which this individual belongs.",
            Validate.REQUIRED);    
    
    public Input<Double> timeInput = new Input<Double>("time",
            "Time at which individual comes into existance. (optional)",
            0.0);
    
    public Input<String> nameInput = new Input<String>("name",
            "Optioal unique node name. Used as node label in "
            + "Newick/NEXUS/BEAST output.");
    
    public Input<List<Individual>> childrenInput = new Input<List<Individual>>(
            "child",
            "An individual which is a child of this one.",
            new ArrayList<Individual>());
    
    hamlet.inheritance.Node node;
    
    public Individual() { }
    
    @Override
    public void initAndValidate() {
        
        node = new hamlet.inheritance.Node(populationInput.get().pop, timeInput.get());
        if (nameInput.get() != null)
            node.setName(nameInput.get());
        
        for (Individual child : childrenInput.get())
            node.addChild(child.node);
    }
}
