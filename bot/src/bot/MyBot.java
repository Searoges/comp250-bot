/**
 *  @author Connor Sean Rodgers
 */
package bot;
/**
 * ==============================================================================================================================
 * Imports
 * ==============================================================================================================================
 */
import ai.abstraction.AbstractAction;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Harvest;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;
import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.*;

/**
 * 
 * Main Bot Class
 * ==============================================================================================================================
 */
public class MyBot extends AbstractionLayerAI 
{  
// Initalise
// ------------------------------------------------------------------------------------------------------------------------------
	Random r = new Random();
    protected UnitTypeTable utt;
    UnitType Worker;
    UnitType baseType;
    int NumberOfWorkers;
// ------------------------------------------------------------------------------------------------------------------------------
// Unit Types  
// ------------------------------------------------------------------------------------------------------------------------------

    public MyBot(UnitTypeTable a_utt) 
    {
        this(a_utt, new AStarPathFinding());
    }
    
    public void reset(UnitTypeTable a_utt)  
    {
        utt = a_utt;
        if (utt!=null) {
        	Worker = utt.getUnitType("Worker");
            baseType = utt.getUnitType("Base");
        }
    }
    
    public MyBot(UnitTypeTable a_utt, PathFinding a_pf) 
    {
        super(a_pf);
        reset(a_utt);
    }
    
    public void reset()
    {
    	super.reset();
    }    
    public AI clone()
    {
    	return new MyBot(utt, pf);
    } 
// ------------------------------------------------------------------------------------------------------------------------------
// Player Action
// ------------------------------------------------------------------------------------------------------------------------------

    public PlayerAction getAction(int player, GameState gs) 
    {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        PlayerAction pa = new PlayerAction();
        // Bases
        for (Unit unit : pgs.getUnits()) 
        {
            // TODO: issue commands to units
        	
        	if (unit.getType() == baseType && unit.getPlayer() == player && gs.getActionAssignment(unit) == null)
        	{
        		baseBehavior(unit, p, pgs);
        	}
        }
        for(Unit u:pgs.getUnits()) 
        {
            if (u.getType().canAttack && !u.getType().canHarvest && 
                u.getPlayer() == player && 
                gs.getActionAssignment(u)==null) {
                meleeUnitBehavior(u,p,gs);
            }        
        }
        List<Unit> workers = new LinkedList<Unit>();
        for(Unit u:pgs.getUnits()) 
        {
            if (u.getType().canHarvest && 
                u.getPlayer() == player) {
                workers.add(u);
            }        
        }
        workersBehavior(workers,p,gs);
        
        return translateActions(player, gs);
    }
// ------------------------------------------------------------------------------------------------------------------------------
// Base Actions
// ------------------------------------------------------------------------------------------------------------------------------
    public void baseBehavior(Unit u,Player p, PhysicalGameState pgs) 
    {
        if (p.getResources()>=Worker.cost) train(u, Worker);
    }
// ------------------------------------------------------------------------------------------------------------------------------
// Melee Combat
// ------------------------------------------------------------------------------------------------------------------------------
    public void meleeUnitBehavior(Unit u, Player p, GameState gs) 
    {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        int closestDistance = 0;
        for(Unit u2:pgs.getUnits()) {
            if (u2.getPlayer()>=0 && u2.getPlayer()!=p.getID()) { 
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy==null || d<closestDistance) {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
        }
        if (closestEnemy!=null) {
            attack(u,closestEnemy);
        }
    }
 // ------------------------------------------------------------------------------------------------------------------------------
 // Workers Collect and Become Attackers When There Are More Than 4 Idle
 // ------------------------------------------------------------------------------------------------------------------------------
    public void workersBehavior(List<Unit> workers,Player p, GameState gs) 
    {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        int nbases = 0;
        int resourcesUsed = 0;
        Unit harvestWorker = null;
        List<Unit> freeWorkers = new LinkedList<Unit>();
        freeWorkers.addAll(workers);
        
        if (workers.isEmpty()) return;
        
        for(Unit u2:pgs.getUnits()) {
            if (u2.getType() == baseType && 
                u2.getPlayer() == p.getID()) nbases++;
        }
        
        List<Integer> reservedPositions = new LinkedList<Integer>();
        if (nbases==0 && !freeWorkers.isEmpty()) {
            // build a base:
            if (p.getResources()>=baseType.cost + resourcesUsed) {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u,baseType,u.getX(),u.getY(),reservedPositions,p,pgs);
                resourcesUsed+=baseType.cost;
            }
        }
        
        if (freeWorkers.size()>0) harvestWorker = freeWorkers.remove(0);
        
        // harvest with the harvest worker:
        if (harvestWorker!=null) {
            Unit closestBase = null;
            Unit closestResource = null;
            int closestDistance = 0;
            for(Unit u2:pgs.getUnits()) {
                if (u2.getType().isResource) { 
                    int d = Math.abs(u2.getX() - harvestWorker.getX()) + Math.abs(u2.getY() - harvestWorker.getY());
                    if (closestResource==null || d<closestDistance) {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
            closestDistance = 0;
            for(Unit u2:pgs.getUnits()) {
                if (u2.getType().isStockpile && u2.getPlayer()==p.getID()) { 
                    int d = Math.abs(u2.getX() - harvestWorker.getX()) + Math.abs(u2.getY() - harvestWorker.getY());
                    if (closestBase==null || d<closestDistance) {
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
            if (closestResource!=null && closestBase!=null) {
                AbstractAction aa = getAbstractAction(harvestWorker);
                if (aa instanceof Harvest) {
                    Harvest h_aa = (Harvest)aa;
                    if (h_aa.getTarget() != closestResource || h_aa.getBase()!=closestBase) harvest(harvestWorker, closestResource, closestBase);
                } else {
                    harvest(harvestWorker, closestResource, closestBase);
                }
            }
        }
        
        if (freeWorkers.size()>=4)
        {
        	for(Unit u:freeWorkers) meleeUnitBehavior(u, p, gs);
        }
        
        
    }
    
    
    @Override
    public List<ParameterSpecification> getParameters() 
    {
    	List<ParameterSpecification> parameters = new ArrayList<>();
        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new AStarPathFinding()));
        return parameters;
    }
}
