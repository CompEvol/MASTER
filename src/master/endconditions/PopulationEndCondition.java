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
package master.endconditions;

import master.model.Population;
import master.model.PopulationState;
import beast.core.BEASTObject;
import beast.core.Input;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;

/**
 * Population end condition which is met when (depending on the construction)
 * a population exceeds or dips below some threshold threshold.
 * May be either a rejection or a truncation condition.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class PopulationEndCondition extends BEASTObject {
    
    public Input<List<Population>> populationInput = new Input<List<Population>>(
            "population",
            "Population whose size to base end condition on.",
            new ArrayList<Population>());
    
    public Input<Double> thresholdInput = new Input<Double>(
            "threshold",
            "Population size threshold at which condition is met.",
            Input.Validate.REQUIRED);
    
    public Input<Boolean> exceedCondInput = new Input<Boolean>(
            "exceedCondition",
            "Whether condition is size>=threshold. False implies <=threshold.",
            Input.Validate.REQUIRED);
    
    public Input<Boolean> rejectionInput = new Input<Boolean>(
            "isRejection",
            "Whether condition causes trajectory rejection. (Default false.)",
            false);
    
    List<Population> pops;
    double threshold;
    boolean exceed, rejection;
    
    public PopulationEndCondition() { }
    
    @Override
    public void initAndValidate() {
        pops = populationInput.get();
        threshold = thresholdInput.get();
        exceed = exceedCondInput.get();
        rejection = rejectionInput.get();
        
        if (pops.isEmpty())
            throw new IllegalArgumentException("Need at least one population "
                    + "for population end condition.");
    }
    
    /**
     * Create a new multi-population end condition which is met when sum of
     * populations provided exceeds or dips below threshold, depending on
     * value of exceedCond.
     * 
     * @param threshold Threshold at which condition is met.
     * @param exceedCond True creates condition met when sum> >= threshold
     * @param rejection True creates a rejection end condition
     * @param pops Varargs array of populations to sum over.
     */
    public PopulationEndCondition(double threshold, boolean exceedCond, boolean rejection, Population ... pops) {
        this.pops = Lists.newArrayList(pops);
        this.threshold = threshold;
        this.exceed = exceedCond;
        this.rejection = rejection;

    }

    public boolean isMet(PopulationState currentState) {
        double size = 0;
        for (Population pop : pops)
            size += currentState.get(pop);
        
        if (exceed)
            return size >= threshold;
        else
            return size <= threshold;
    }

    public boolean isRejection() {
        return rejection;
    }

    public String getConditionDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Condition is met when ");
        for (int i=0; i<pops.size(); i++) {
            if (i>0)
                sb.append(" + ");            
            sb.append(pops.get(i));
        }

        if (exceed)
            sb.append(" >= ");
        else
            sb.append(" <= ");
        
        sb.append(threshold);
        
        return sb.toString();
    }
    
}