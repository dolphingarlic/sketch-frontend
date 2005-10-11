package streamit.frontend.tosbit;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

class MethodState{
	private HashMap vars;	//<String, varState>	
	private MapStack varTranslator;
	private Stack vstack;
	private ChangeStack changeTracker;
	private int pushcount;
	
    public static void Assert(boolean t, String s){
    	if(!t){
    		System.err.println(s);    		
    		System.exit(1);
    	}
    }
    
    /**
     * 
     * WARNING: You need to call varGetLHSName after calling varDeclare
     * in order to complete the var declaration process, and
     * if the variable is an array, also call makeArray.
     * 
     */
    void varDeclare(String var){
    	// System.out.println("DECLARED " + var);
		String newname = var + "_" + pushcount +"L" + varTranslator.curVT.size();
		varTranslator.curVT.put(var, newname);
    }
    
    void pushLevel(){
    	++pushcount;
    	MapStack tmp = varTranslator;
    	varTranslator = new MapStack();
    	varTranslator.kid = tmp;    	    	    	
    }
    
    void popLevel(){
    	Iterator it = varTranslator.curVT.values().iterator();
    	while(it.hasNext()){
    		String nm = (String) it.next();
    		//System.out.println("Unseting " + nm);
    		this.vars.remove(nm);
    		if(this.changeTracker != null){
    			changeTracker.currTracker.remove(nm);
    		}
    	}
    	varTranslator = varTranslator.kid;
    }
    
    String transName(String nm){       	
    	String otpt = varTranslator.transName(nm);
    	// System.out.println(nm + " = " +  otpt);
    	return  otpt;
    }
    
	int idx;
	MethodState(){
		// System.out.println("New Method State for new method.");
		vars = new HashMap();			
		vstack = new Stack();		
		changeTracker = null;
		varTranslator = new MapStack();
	}
	

	void pushChangeTracker(){	
		if(changeTracker == null){
			changeTracker = new ChangeStack();
		}else{
			ChangeStack tmp = changeTracker;
			changeTracker = new ChangeStack();
			changeTracker.kid  = tmp;
		}
		Iterator it =vars.values().iterator();	
		while(it.hasNext()){						
			varState vs = (varState) it.next();
			vs.pushRHS();
		}
	}
	
	ChangeStack popChangeTracker(){
		ChangeStack tmp = changeTracker;
		changeTracker = changeTracker.kid;
		Iterator it = vars.values().iterator();
		while(it.hasNext()){
			varState vs = (varState) it.next();
			if(!vs.popRHS()){
				Assert(!vs.hasVal(), "If I am at the lowest level, it means I am new, and therefore I can't have a value. This is a BUG.");
			}			
		}
		return tmp;
	}
	
	private String checkAndUnset(Map.Entry me, String cond){
		String result;
		//This is to be called when a mapEntry changed in one branch but not
		//in the other, or if there is no other.
		String nm = ((String)me.getKey());
		if( ((varValue)me.getValue()).hasValue ){					
			if(this.UTvarHasValue( nm ) ){
				if(this.UTvarValue( nm ) == ((varValue)me.getValue()).getValue() ){
					//In this case, our optimism pays off and we don't have to do anything,
					//because even though the value changed, in the end it didn't really change.
					//result = this.varGetLHSName( nm ) + " = " +  this.varGetRHSName( nm ) + ";";
					result = "";
				}else{
					//In this case, we find that we need to unset this guy because it changed.
					 result = " = " +
						cond + "? " + ((varValue)me.getValue()).getValue() + " : " + this.UTvarValue( nm ) + "; \n";
					 result = this.UTvarGetLHSName( nm ) + result;
					this.UTunsetVarValue( nm );
				}
			}else{
				//And ditto for this case.
				result = " = " +
					cond + "? " + ((varValue)me.getValue()).getValue() + " : " + this.UTvarGetRHSName( nm ) + "; \n";
				result = this.UTvarGetLHSName( nm ) + result;
				
				this.UTunsetVarValue( nm );
			}
		}else{
			//in this case, the value was modified in the branch, but it was modified to top,
			//so we have to unset it, and we need to have a ? for it to select the right value at runtime.	
			String v1 = null;
			if(((varValue)me.getValue()).hasValue){
				v1 = " " + ((varValue)me.getValue()).getValue();
			}else{
				v1 = this.UTvarGetRHSName(nm ,  ((varValue)me.getValue()).lastLHS);
			}
			result = " = " + 
				cond + "? " + v1 + " : " + this.UTvarGetRHSName( nm )  + "; \n";
			result = this.UTvarGetLHSName( nm ) + result;
			this.UTunsetVarValue((String)me.getKey());
		}
		return result;
	}
	
	public void makeArray(String var, int size){
		var = this.transName(var);
		UTmakeArray(var, size);
	}
	public void UTmakeArray(String var, int size){
		varState i = (varState) vars.get(var);
		i.setIsarray(true, size);
	}
	
	public int checkArray(String var){
		var = this.transName(var);
		return UTcheckArray(var);
	}
	public int UTcheckArray(String var){
		varState i = (varState) vars.get(var);
		if( i.isarray())
			return i.getArrSize();
		else
			return -1;
	}
	
	public void unsetVarValue(String var){
		var = this.transName(var);
		UTunsetVarValue(var);
	}
	
	public void UTunsetVarValue(String var){
		
		varState i = (varState) vars.get(var);
		Assert(i != null , "This can't happen, if it does, i'ts a BUG");
		if(changeTracker != null){					
			changeTracker.unsetVarValue(var, i);						
		}else{
			i.hasval = false;
		}
		
	}
	
	String procChangeTrackers(ChangeStack ms1, ChangeStack ms2, String cond){	
		String result = "";
		Iterator it = ms1.currTracker.entrySet().iterator();
		while(it.hasNext()){
			Map.Entry me = (Map.Entry) it.next();	
			String nm = (String)me.getKey();
			if(ms2.currTracker.containsKey( nm )){
				//This means the me.getKey() was modified on both branches.
				if(ms2.currTracker.get( nm ).equals(me.getValue())){
					//This means there is an intersection and the values match, and they both have values.
					// Let's see if we can get away without this statement: result += this.varGetLHSName( nm ) + " = " +  ((varValue)me.getValue()).getValue() + "; \n";
					this.UTsetVarValue((String)me.getKey(), ((varValue)me.getValue()).getValue());					
				}else{
					//This means there is an intersection but either the values don't match, or they are both top.
					//Then we need to make that key equal to top, and we can make it top
					//All the way through, since if it is top at this join point, it will
					//Be top at all other subsequent join points.
					varValue nv = (varValue) ms2.currTracker.get( nm );
					String v1 = null, v2=null;
					if(((varValue)me.getValue()).hasValue){
						v1 = " " + ((varValue)me.getValue()).getValue();
					}else{
						v1 = this.UTvarGetRHSName(nm ,  ((varValue)me.getValue()).lastLHS);
					}
					if(nv.hasValue){
						v2 = " " + nv.getValue();
					}else{
						v2 = this.UTvarGetRHSName(nm,  nv.lastLHS);
					}
					result += this.UTvarGetLHSName( nm ) + " = " + 
						cond + "? " + v1 + " : " + v2  + "; \n";
					this.UTunsetVarValue((String)me.getKey());
				}
				ms2.currTracker.remove(me.getKey());
			}else{
				//In this case, it means me.getKey() was in ms1 but not in ms2,
				//So on one branch it got modified, but on the other one it didn't.
				//If we wanted to be really conservative, we could 
				//say "Oh, it changed on one branch and not on another, so it's top"
				//However, we will be more precise and see if it really changed, or if it's value is still the same.
				//If it's value is still the same, the it didn't really change.
				result += checkAndUnset(me, cond);
			}
		}
		//Now, at this point, we have removed from ms2 all the items
		//that were also in ms1. So all the ones that are left in ms2
		//Are the ones that are in ms2 alone. 
		//Once again, if we wanted to be conservative, we would just
		//unset them all, but we'll see if we can get away with being nice
		//and only unset them if they are actually different from what they were originally.
		//This is checked, just as before, by checkAndUnset(...);
		Iterator it2 = ms2.currTracker.entrySet().iterator();
		while(it2.hasNext()){
			Map.Entry me = (Map.Entry) it2.next();
			result += checkAndUnset(me, "(!" + cond + ")");
		}
		return result;
	}


	String procChangeTrackers(ChangeStack ms1, String cond){
		String result = ""; 
		Iterator it = ms1.currTracker.entrySet().iterator();
		while(it.hasNext()){
			Map.Entry me = (Map.Entry) it.next();
			result += checkAndUnset(me, cond);					
		}
		return result;
	}
	
	
	private boolean localVarHasValue(String var){
		return vars.containsKey(var) && ((varState)vars.get(var)).hasVal();
	}
	
	private boolean UTvarHasValue(String var){
		if( changeTracker == null )
			return localVarHasValue(var);
		else
			return localVarHasValue(var) ||  changeTracker.varHasValue(var);
	}
	
	boolean varHasValue(String var){
		var = this.transName(var);
		return UTvarHasValue(var);
	}
	
	int varValue(String var){
		var = this.transName(var);
		return UTvarValue(var);		
	}
	
	int UTvarValue(String var){
		
		varState i = (varState) vars.get(var);		
		if(changeTracker == null){			
			Assert(i.hasVal(), "The value of " + var + " is input dependent, but it's not supposed to be.\n");
			return i.getVal();
		}else{
			if( changeTracker.varHasValue(var) ){
				return changeTracker.varValue(var);
			}else{
				Assert(i.hasVal(), "The value of " + var + " is input dependent, but it's not supposed to be.\n");
				return i.getVal();
			}						
		}
	}
	
	void setVarValue(String var, int v){
		var = this.transName(var);
		UTsetVarValue(var,v);
	}
	
	void UTsetVarValue(String var, int v){		
		varState tv = (varState) vars.get(var);
		Assert(tv != null, " This should never happen, because before seting the value of "+ var + ", you should have requested a LHS name. Or, alternatively, if this is a ++ increment, then you can't increment if it doesn't have an initial value, in which case tv would also not be null.");
		if(changeTracker != null){
			if(tv == null){
				//This branch will never be taken.
				vars.put(var, new varState(var));
			}
			changeTracker.setVarValue(var, v);
		}else{
			if(tv == null){
				//This branch will never be taken.
				vars.put(var, new varState(var, v));
			}else{
				tv.setVal(v);
			}
		}
	}
	
	String varGetRHSName(String var){		
		var = this.transName(var);
		return UTvarGetRHSName(var);
	}
	
	
	
	String UTvarGetRHSName(String var){				
		if(this.UTvarHasValue(var)){
			return "" + this.UTvarValue(var) ;
		}else{
			varState tv = (varState) vars.get(var);
			Assert(tv != null, "You are using variable " + var + " before initializing it.");
			return tv.getRHSName();
		}
	}
	
	String UTvarGetRHSName(String var, int tmprhs){		
		varState tv = (varState) vars.get(var);
		Assert(tv != null, "You are using variable " + var + " before initializing it.");
		return tv.getRHSName(tmprhs);
	}
	
	String varGetLHSName(String var){
		var = this.transName(var);
		return UTvarGetLHSName(var);
	}
	
	
	String UTvarGetLHSName(String var){		
		varState tv = (varState) vars.get(var);
		if(tv == null){
			tv = new varState(var);
			vars.put(var, tv);
		}
		String nm = tv.getLHSName();
		
		if(changeTracker != null){
			changeTracker.declVarLHS(var, tv);
		}
		return nm;			
	}
	
	void pushVStack(Integer val){
		//We always push, but we push null when there is no value.
		vstack.push(val);
	}
	Integer popVStack(){
		return (Integer) vstack.pop();
	}
}