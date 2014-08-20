package master.model;

import beast.core.BEASTObject;
import beast.core.Input;
import beast.core.Input.Validate;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.*;
import java.text.ParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import master.model.iterators.AbstractIteration;

/**
 * Class of objects describing the reactions which occur between the various
 * populations in the model. Reactions may involve both scalar and structured
 * populations.
 *
 * @author Tim Vaughan
 *
 */
public class Reaction extends BEASTObject {
    
    public Input<String> nameInput = new Input<String>("reactionName",
            "Name of reaction. (Not used for grouped reactions.)");
    
    public Input<String> rateInput = new Input<String>("rate",
            "Individual reaction rate. (Only used if group rate unset.)");

    public Input<String> reactionStringInput = new Input<String>(
            "value",
            "String description of reaction.", Validate.REQUIRED);
    
    public Input<AbstractIteration> iterationInput = new Input<AbstractIteration>(
            "iteration",
            "Iteration over indices.");

    public String reactionName;
    public Map<Population,Integer> reactCount, prodCount, deltaCount;
    public Map<Population, List<Node>> reactNodes, prodNodes;
    public List<Double> rates, rateTimes;
    public double propensity;
    
    /**
     * Constructor without name.
     */
    public Reaction() { }
    
    /**
     * Constructor with name.
     * 
     * @param reactionGroupName
     */
    public Reaction(String reactionGroupName) {
        this.reactionName = reactionGroupName;
}
    
    @Override
    public void initAndValidate() {
        reactionName = nameInput.get();

        // Parse rate string
        if (rateInput.get() != null) {
            setRateFromString(rateInput.get());
        }
        

    }
    
    /**
     * Obtain list containing this reaction, or the reactions implied
     * by the ranges given.
     * 
     * @param populationTypes
     * @return list of reactions
     */
    public List<Reaction> getAllReactions(List<PopulationType> populationTypes) {
        List<Reaction> reactions = Lists.newArrayList();
        
        if (iterationInput.get() == null) {
            try {
                setSchemaFromString(reactionStringInput.get(), populationTypes);
            } catch (ParseException ex) {
                Logger.getLogger(Reaction.class.getName()).log(Level.SEVERE, null, ex);
            }
            reactions.add(this);
        } else {
            
            String[] varNames = iterationInput.get().getVariableNames();
            for (int[] varVals : iterationInput.get().getVariableValuesList()) {
                String flattenedString = getFlattenedReactionString(
                        varNames, varVals);
                
                // Assemble reaction
                Reaction reaction;
                if (reactionName != null) {
                    if (reactions.size()>0)
                        reaction = new Reaction(reactionName + reactions.size());
                    else
                        reaction = new Reaction(reactionName);
                } else
                    reaction = new Reaction();
                
                reaction.rates = rates;
                reaction.rateTimes = rateTimes;
                
                try {
                    reaction.setSchemaFromString(flattenedString, populationTypes);
                } catch (ParseException ex) {
                    Logger.getLogger(Reaction.class.getName()).log(Level.SEVERE, null, ex);
                }

                reactions.add(reaction);
            }
                
        }
        
        return reactions;
    }

    /**
     * Make required iteration variable replacements in reaction string.
     * 
     * @param varNames
     * @param varVals
     * @return flattened reaction string
     */
    private String getFlattenedReactionString(String[] varNames, int[] varVals) {

        String reactionStr = reactionStringInput.get().replaceAll("  *", "");
        for (int i=0; i< varNames.length; i++) {
            String name = varNames[i];
            int val = varVals[i];
            reactionStr = reactionStr.replace("["+name+"]", "["+val+"]")
                    .replace("["+name+",", "["+val+",")
                    .replace(","+name+"]", ","+val+"]")
                    .replace(","+name+",", ","+val+",");
        }
        
        return reactionStr;
    }
    
    /**
     * Attempt to determine reaction schema from string representation.
     * 
     * @param schemaString string to parse.
     * @param popTypes list of population types present in model.
     * @throws java.text.ParseException 
     */
    public void setSchemaFromString(String schemaString, List<PopulationType> popTypes) throws ParseException {
        
        ReactionStringParser parser = new ReactionStringParser(schemaString, popTypes);
        
        // Assemble node representation of reaction
        
        reactNodes = Maps.newHashMap();
        List<Node> reactNodeList = Lists.newArrayList();
        for (int i=0; i<parser.getReactantPops().size(); i++) {
            Population pop = parser.getReactantPops().get(i);
            Node node = new Node(pop);
            
            if (!reactNodes.containsKey(pop))
                reactNodes.put(pop, new ArrayList<Node>());

            reactNodes.get(pop).add(node);
            reactNodeList.add(node);
        }
        
        prodNodes = Maps.newHashMap();
        for (int i=0; i<parser.getProductPops().size(); i++) {
            Population pop = parser.getProductPops().get(i);
            Node node = new Node(pop);
            
            if (!prodNodes.containsKey(pop))
                prodNodes.put(pop, new ArrayList<Node>());
            
            prodNodes.get(pop).add(node);
            
            int nodeId = parser.getProductIDs().get(i);
            for (int ip=0; ip<parser.getReactantIDs().size(); ip++) {
                if (parser.getReactantIDs().get(ip)==nodeId) {
                    Node parent = reactNodeList.get(ip);
                    parent.addChild(node);
                }
            }
        }
        
        // Calculate individual population counts for non-inheritance
        // trajectory code
        
        reactCount = Maps.newHashMap();
        for (Population pop : reactNodes.keySet())
            reactCount.put(pop, reactNodes.get(pop).size());
        
        prodCount = Maps.newHashMap();
        for (Population pop : prodNodes.keySet())
            prodCount.put(pop, prodNodes.get(pop).size());
        
        // Loosely, calculate deltas=prodLocSchema-reactLocSchema.

        deltaCount = Maps.newHashMap();
            
        for (Population pop : reactCount.keySet()) 
            deltaCount.put(pop, -reactCount.get(pop));
            
        for (Population pop : prodCount.keySet()) {
            if (!deltaCount.containsKey(pop))
                deltaCount.put(pop, prodCount.get(pop));
            else {
                int val = deltaCount.get(pop);
                val += prodCount.get(pop);
                deltaCount.put(pop, val);
            }
        }
    }

    /**
     * Use string of the form "rate1:time1,rate2:time2,..."
     * to set the list of reaction rates associated with
     * this reaction.
     * 
     * @param rateString 
     */
    public void setRateFromString(String rateString) {
        rates = Lists.newArrayList();
        rateTimes = Lists.newArrayList();
        
        boolean isFirst = true;
        for (String ratePairStr : rateInput.get().split(",")) {
            String [] ratePairSplitStr = ratePairStr.trim().split(":");
            if (ratePairSplitStr.length==1) {
                if (!isFirst) {
                    throw new IllegalArgumentException("Only first rate "
                            + "pair in a reaction rate string can omit "
                            + "the time.");
                }
                
                rates.add(Double.valueOf(ratePairSplitStr[0]));
                rateTimes.add(0.0);
            }
            
            if (ratePairSplitStr.length==2) {
                
                double thisRate = Double.valueOf(ratePairSplitStr[0]);
                double thisTime = Double.valueOf(ratePairSplitStr[1]);
                
                // First time >0 implies reaction is off at start of sim
                if (isFirst && thisTime>0.0) {
                    rates.add(0.0);
                    rateTimes.add(0.0);
                }
                
                rates.add(thisRate);
                rateTimes.add(thisTime);
            }
            
            if (rateTimes.size()>1 &&
                    rateTimes.get(rateTimes.size()-1)<rateTimes.get(rateTimes.size()-2))
                throw new IllegalArgumentException("Rate change times must "
                        + "be monotonically increasing.");
            
            if (isFirst)
                isFirst = false;
        }
    }
    
    /**
     * Retrieve rate list for this reaction.
     * 
     * @return rates
     */
    public List<Double> getRates() {
        return rates;
    }
    
    /**
     * Retrieve rate change time list for this reaction.
     * 
     * @return list of rate change times
     */
    public List<Double> getRateTimes() {
        return rateTimes;
    }
    
    /**
     * Obtain rate at particular time.
     * 
     * @param t
     * @return 
     */
    public double getRate(double t) {
        int interval;
        for (interval = 0; interval<rateTimes.size()-1; interval++) {
            if (rateTimes.get(interval+1)>t)
                break;
        } 
        
        return rates.get(interval);
    }
    
    /**
     * Calculate instantaneous reaction rates (propensities) for a given system
     * state.
     *
     * @param state	PopulationState used to calculate propensities.
     * @param t         Time at which propensity is calculated.
     */
    public void calcPropensity(PopulationState state, double t) {
        
        int interval;
        for (interval=0; interval<rates.size()-1; interval++) {
            if (rateTimes.get(interval+1)>t)
                break;
        }
        
        propensity = rates.get(interval);

        for (Population pop : reactCount.keySet()) {
            for (int m = 0; m<reactCount.get(pop); m++)
                propensity *= state.get(pop)-m;
        }
    }
    
    /**
     * Retrieve recently calculated propensity.
     * 
     * @return propensity
     */
    public double getPropensity() {
        return propensity;
    }
    
    /**
     * Set name of reaction.
     * 
     * @param reactionName 
     */
    public void setName(String reactionName) {
        this.reactionName = reactionName;
    }
    
    /**
     * Retrieve name of reaction optionally provided during
     * specification.
     * 
     * @return reaction name
     */
    public String getName() {
        return reactionName;
    }
   
    /*
     * Methods for JSON object mapper
     */
    
    @Override
    @JsonValue
    public String toString() {

        // Construct reaction string
        StringBuilder sb = new StringBuilder();
        if (reactionName != null)
            sb.append(reactionName).append(": ");
        
        if (!reactCount.isEmpty()) {
            boolean first = true;
            for (Population pop : reactCount.keySet()) {
                if (!first)
                    sb.append(" + ");
                else
                    first = false;
                
                if (reactCount.get(pop)>1)
                    sb.append(reactCount.get(pop));
                sb.append(pop.type.name);
                if (!pop.isScalar()) {
                    sb.append("[");
                    int [] loc = pop.getLocation();
                    for (int i=0; i<loc.length; i++) {
                        if (i>0)
                            sb.append(',');
                        sb.append(loc[i]);
                    }
                    sb.append("]");
                }
            }
        } else
            sb.append("0");
        
        sb.append(" -> ");
        
        if (!prodCount.isEmpty()) {
            boolean first = true;
            for (Population pop : prodCount.keySet()) {
                if (!first)
                    sb.append(" + ");
                else
                    first = false;
                
                if (prodCount.get(pop)>1)
                    sb.append(prodCount.get(pop));
                sb.append(pop.type.name);
                if (!pop.isScalar()) {
                    sb.append("[");
                    int [] loc = pop.getLocation();
                    for (int i=0; i<loc.length; i++) {
                        if (i>0)
                            sb.append(',');
                        sb.append(loc[i]);
                    }
                    sb.append("]");
                }
            }
        } else
            sb.append("0");

        return sb.toString();

    }

}