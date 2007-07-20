package streamit.frontend.experimental.nodesToSB;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import streamit.frontend.experimental.MethodState;
import streamit.frontend.experimental.abstractValue;
import streamit.frontend.experimental.varState;
import streamit.frontend.nodes.ExprStar;
import streamit.frontend.nodes.FENode;
import streamit.frontend.nodes.Function;
import streamit.frontend.nodes.Parameter;
import streamit.frontend.nodes.Type;
import streamit.frontend.nodes.TypeArray;
import streamit.frontend.nodes.TypePrimitive;
import streamit.frontend.tosbit.ValueOracle;

public class NtsbVtype extends IntVtype {
	public PrintStream out;
	private ValueOracle oracle;	
	
	
	public NtsbVtype(ValueOracle oracle, PrintStream out){
		this.oracle = oracle;
		this.out = out;		
	}
	
	public abstractValue STAR(FENode node){		
		if(node instanceof ExprStar){
			ExprStar star = (ExprStar) node;
			
			Type t = star.getType();
			int ssz = 1;
			List<abstractValue> avlist = null;
			if(t instanceof TypeArray){
				Integer iv = ((TypeArray)t).getLength().getIValue();
				assert iv != null;
				ssz = iv;
				avlist = new ArrayList<abstractValue>(ssz);
			}
			String isFixed = star.isFixed()? " *" : "";
			NtsbValue nv = null;
			for(int i=0; i<ssz; ++i){				
				String cvar = oracle.addBinding(star.getDepObject(i));
				String rval = "";
				if(star.getSize() > 1)
					rval =  "<" + cvar + "  " + star.getSize() + isFixed+ "> ";
				else
					rval =  "<" + cvar +  "> ";
				nv = new NtsbValue(rval);
				if(avlist != null) avlist.add(nv);
			}
			
			if(avlist != null) return new NtsbValue(avlist);			
			return nv;
		}
		String cvar = oracle.addBinding(node);
		return new NtsbValue("<" + cvar +  ">");
	}
	
	public abstractValue BOTTOM(){
		return new NtsbValue();
	}
	
	public abstractValue BOTTOM(String label){
		return new NtsbValue(label);
	}
	
	public abstractValue BOTTOM(Type t){
		if( t instanceof TypePrimitive ){
			return BOTTOM();
		}
		assert false;
		return null;
	}
	public abstractValue CONST(int v){
		return new NtsbValue(v); 
	}
	
	public abstractValue CONST(boolean v){
		return new NtsbValue(v); 
	}
	
	public abstractValue ARR(List<abstractValue> vals){
		return new NtsbValue(vals);		
	}
	
	
	public void Assert(abstractValue val, String msg){
		 out.print ("assert (" + val + ") : \"" + msg + "\" ;\n");
	}
	
	public varState cleanState(String var, Type t, MethodState mstate){
		return new NtsbState(var, t, this);
	}
	
	public void funcall(Function fun, List<abstractValue> avlist, List<abstractValue> outSlist){
		Iterator<abstractValue> actualParams = avlist.iterator();
		String name = fun.getName();
		String plist = "";
		while( actualParams.hasNext() ){
			abstractValue param = actualParams.next();
			plist += param;
			plist += " ";			
		}
		
		Iterator<Parameter> formalParams = fun.getParams().iterator();
    	while(formalParams.hasNext()){
    		Parameter param = formalParams.next();    	
    		if( param.isParameterOutput()){
    			if( fun.isUninterp() ){
    			outSlist.add(BOTTOM(name+ "_" + param.getName() + "[" + param.getType() + "]( "+ plist +"  )"));
    			}else{
    				outSlist.add(BOTTOM(name + "[" + param.getType() + "]( "+ plist +"  )"));
    			}
    		}
    	}
    	if(!fun.isUninterp()){
    		assert outSlist.size() == 1 : "If a function is to be dynamically inlined, it ought to have only one return value.";
    	}
    	
	}
}
