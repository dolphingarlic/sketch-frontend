package streamit.frontend.parallelEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import streamit.frontend.controlflow.CFG;
import streamit.frontend.controlflow.CFGBuilder;
import streamit.frontend.controlflow.CFGNode;
import streamit.frontend.controlflow.CFGNode.EdgePair;
import streamit.frontend.nodes.ExprConstInt;
import streamit.frontend.nodes.ExprVar;
import streamit.frontend.nodes.StmtAssign;
import streamit.frontend.nodes.StmtPloop;
import streamit.frontend.nodes.StmtVarDecl;
import streamit.frontend.passes.VariableDeclarationMover;

public class CFGforPloop extends CFGBuilder {

	Set<String> locals = new HashSet<String>();
	Map<String, StmtVarDecl> localDecls = new HashMap<String, StmtVarDecl>();
	
	public static CFG cleanCFG(CFG cfg){
		Map<CFGNode, List<EdgePair>> edges = new HashMap<CFGNode, List<EdgePair>>();
	    List<CFGNode> nodes = new ArrayList<CFGNode>();
		Map<CFGNode, CFGNode> equiv = new HashMap<CFGNode, CFGNode>();
	    for(Iterator<CFGNode> it = cfg.getNodes().iterator(); it.hasNext(); ){
	    	CFGNode n = it.next();
	    	if( n.isEmpty() && cfg.getSuccessors(n).size() == 1 ){
	    		EdgePair ep = cfg.getSuccessors(n).get(0);
	    		assert ep.label == null : "The nodes must be connected by an unconditional edge";
	    		equiv.put(n, ep.node);
	    	}
	    }
	    
	    CFGNode entry = cfg.getEntry();	    
	    while(equiv.containsKey(entry)){
	    	entry = equiv.get(entry);
		}
	    
	    nodes.add(entry);
	    
	    for(Iterator<CFGNode> it = cfg.getNodes().iterator(); it.hasNext(); ){
	    	CFGNode n = it.next();
	    	if(!equiv.containsKey(n)){
	    		if(n != entry){
	    			nodes.add(n);
	    		}
	    		for(Iterator<EdgePair> suc = cfg.getSuccessors(n).iterator(); suc.hasNext(); ){
	    			EdgePair sep = suc.next();
	    			CFGNode sn = sep.node;
	    			while(equiv.containsKey(sn)){
	    				sn = equiv.get(sn);
	    			}
	    			List<EdgePair> target;
	    		    if (edges.containsKey(n))
	    		            target = edges.get(n);
    		        else
    		        {
    		            target = new ArrayList<EdgePair>();
    		            edges.put(n, target);
    		        }
    		        target.add(new EdgePair(sn, sep.label));
	    		}
	    	}
	    }
	    
	    CFG newCFG = new CFG(nodes, entry , cfg.getExit() , edges);
	    newCFG.setNodeIDs();	    
		return newCFG;
	}
	
	
	/**
	 * 
	 * Create a CFG for the parallel program where each node corresponds to an atomic step
	 * in the execution.
	 * 
	 * The locals array will contain those local variables that span multiple blocks, and therefore have to be communicated
	 * through the interface of the rest function.
	 * 
	 * @param ploop
	 * @param locals
	 * @return
	 */
	public static CFG buildCFG(StmtPloop ploop, Set<StmtVarDecl>/*out*/ locals)
    {
        CFGforPloop builder = new CFGforPloop();
        CFGNodePair pair = (CFGNodePair)ploop.getBody().accept(builder); 
        CFG rv =cleanCFG(new CFG(builder.nodes, pair.start, pair.end, builder.edges));
        builder.locals.add(ploop.getLoopVarName());
        builder.localDecls.put(ploop.getLoopVarName(), ploop.getLoopVarDecl());
        CFGSimplifier sym = new CFGSimplifier(builder.locals);
        System.out.println("**** was " + rv.size() );
        rv = sym.moo(rv);
        rv = sym.mergeConsecutiveLocals(rv);
        rv = sym.mergeBranches(rv);
        rv = sym.cleanLocalState(rv, builder.localDecls, ploop.getLoopVarDecl());
        locals.addAll(builder.localDecls.values());
        System.out.println("**** became " + rv.size() );
        rv.setNodeIDs();
        return rv; 
    }
	
	public Object visitStmtVarDecl(StmtVarDecl svd){
		
		 CFGNode entry = null;
		 CFGNode last = null;
	     for(int i=0; i<svd.getNumVars(); ++i){
	    	 String name = svd.getName(i);
	    	 locals.add(name);
	    	 localDecls.put(name, new StmtVarDecl(svd.getCx(), svd.getType(i), name, ExprConstInt.zero));
	    	 if(svd.getInit(i) != null){
	    		 CFGNode tmp = new CFGNode( new StmtAssign(svd.getCx(), new ExprVar(null, name), svd.getInit(i)));
    			 this.nodes.add(tmp);
	    		 if(entry == null){	    			 
	    			 entry = tmp;
	    			 last = tmp;
	    		 }else{
	    			 addEdge(last, tmp, null);
	    			 last = tmp;
	    		 }
	    	 }
	     }
	     if(entry == null){
	    	 CFGNode node = new CFGNode(svd, true);
	         nodes.add(node);
	         return new CFGNodePair(node, node);
	     }else{
	    	 return new CFGNodePair(entry, last);
	     }
	}
	
	protected CFGforPloop(){
		super();
		
	}
	
	
}
