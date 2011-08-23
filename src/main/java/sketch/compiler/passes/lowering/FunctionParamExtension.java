package sketch.compiler.passes.lowering;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sketch.compiler.ast.core.FENode;
import sketch.compiler.ast.core.FEReplacer;
import sketch.compiler.ast.core.Function;
import sketch.compiler.ast.core.NameResolver;
import sketch.compiler.ast.core.Parameter;
import sketch.compiler.ast.core.Program;
import sketch.compiler.ast.core.StreamSpec;
import sketch.compiler.ast.core.SymbolTable;
import sketch.compiler.ast.core.TempVarGen;
import sketch.compiler.ast.core.exprs.*;
import sketch.compiler.ast.core.stmts.Statement;
import sketch.compiler.ast.core.stmts.StmtAssign;
import sketch.compiler.ast.core.stmts.StmtBlock;
import sketch.compiler.ast.core.stmts.StmtExpr;
import sketch.compiler.ast.core.stmts.StmtIfThen;
import sketch.compiler.ast.core.stmts.StmtLoop;
import sketch.compiler.ast.core.stmts.StmtReturn;
import sketch.compiler.ast.core.stmts.StmtVarDecl;
import sketch.compiler.ast.core.typs.Type;
import sketch.compiler.ast.core.typs.TypePrimitive;
import sketch.compiler.ast.core.typs.TypeStruct;
import sketch.compiler.ast.core.typs.TypeStructRef;
import sketch.compiler.passes.annotations.CompilerPassDeps;
import sketch.compiler.stencilSK.VarReplacer;
import sketch.util.exceptions.UnrecognizedVariableException;

import static sketch.util.DebugOut.assertFalse;


/**
 * Converts function that return something to functions that take
 * extra output parameters. Also fixes all function calls.
 *
 * @author liviu
 */
@CompilerPassDeps(runsBefore = {}, runsAfter = {})
public class FunctionParamExtension extends SymbolTableVisitor
{

	/**
	 *
	 *
	 *
	 * @author asolar
	 *
	 */
	private class ParameterCopyResolver extends SymbolTableVisitor
	{
		private HashMap<String,Parameter> unmodifiedParams;

		public ParameterCopyResolver()
		{
			super(null);
		}

		private Function addVarCopy(Function func, Parameter param, String newName)
		{
			StmtBlock body=(StmtBlock) func.getBody();
			StmtVarDecl decl=new StmtVarDecl(func,param.getType(),param.getName(),
					new ExprVar(func,newName));
			List<Statement> stmts = new ArrayList<Statement> (body.getStmts().size()+2);
			stmts.add(decl);
			stmts.addAll(body.getStmts());
            return func.creator().body(new StmtBlock(body, stmts)).create();
		}
		@Override
		public Object visitFunction(Function func)
		{
			unmodifiedParams=new HashMap<String,Parameter>();
			for(Iterator<Parameter> iter=func.getParams().iterator();iter.hasNext();) {
				Parameter param=iter.next();
				if(param.isParameterOutput()) continue;
				unmodifiedParams.put(param.getName(),param);
			}
			func=(Function) super.visitFunction(func);
			List<Parameter> parameters=new ArrayList<Parameter>(func.getParams());
			for(int i=0;i<parameters.size();i++) {
				Parameter param=parameters.get(i);
				if(param.isParameterOutput()) continue;
				if(!unmodifiedParams.containsValue(param)) {
					String newName=getNewInCpID(param.getName());
					Parameter newPar=new Parameter(param.getType(),newName,param.getPtype());
					parameters.set(i,newPar);
					func=addVarCopy(func,param,newName);
				}
			}
			return func.creator().params(parameters).create();
		}
		public Object visitStmtAssign(StmtAssign stmt)
		{
			Expression lhs=(Expression) stmt.getLHS().accept(this);
			while (lhs instanceof ExprArrayRange) lhs=((ExprArrayRange)lhs).getBase();
			assert lhs instanceof ExprVar  || lhs instanceof ExprField;
			if( lhs instanceof ExprVar ){
				String lhsName=((ExprVar)lhs).getName();
				unmodifiedParams.remove(lhsName);
			}
			return super.visitStmtAssign(stmt);
		}
		public Object visitStmtVarDecl(StmtVarDecl stmt)
		{
			int n=stmt.getNumVars();
			for(int i=0;i<n;i++) {
				unmodifiedParams.remove(stmt.getName(i));
			}
			return super.visitStmtVarDecl(stmt);
		}
	}

	private int inCpCounter;
	private int outCounter;
	private Function currentFunction;
	private ParameterCopyResolver paramCopyRes;
	public boolean initOutputs=false;
    TempVarGen varGen;
	
	public FunctionParamExtension(boolean io, TempVarGen vargen) {
        this(null, vargen);
        initOutputs = io;
	}

    protected boolean hasFunCall(Expression exp) {
        class checker extends FEReplacer {
            public boolean found = false;

            public Object visitExprFunCall(ExprFunCall ear) {
                found = true;
                return ear;
            }
        }
        ;
        checker ck = new checker();
        exp.accept(ck);
        return ck.found;
    }

    protected Expression doLogicalExpr(ExprBinary eb) {
        Expression left = eb.getLeft(), right = eb.getRight();

        if (!(hasFunCall(left) || hasFunCall(right)))
            return eb;

        boolean isAnd = eb.getOpString().equals("&&") || eb.getOpString().equals("&");

        String resName = varGen.nextVar("_pac_sc");

        addStatement(new StmtVarDecl(eb, TypePrimitive.bittype, resName, null));

        ExprVar res = new ExprVar(eb, resName);
        Expression cond = isAnd ? res : new ExprUnary("!", res);
        List<Statement> blist = new ArrayList<Statement>();
        blist.add(new StmtAssign(res, right));
        StmtBlock nb =
                new StmtBlock(new StmtAssign(res, left), new StmtIfThen(eb, cond,
                        new StmtBlock(blist), null));

        doStatement(nb);

        return res;
    }

    public Object visitExprTernary(ExprTernary exp) {
        Expression a = exp.getA().doExpr(this);
        Expression b = exp.getB();
        Expression c = exp.getC();

        if (!(hasFunCall(b) || hasFunCall(c))) {
            return super.visitExprTernary(exp);
        }

        String resName = varGen.nextVar("_pac_sc");
        Type t = getTypeReal(exp);
        if (t == TypePrimitive.nulltype) {
            t = TypePrimitive.inttype;
        }
        addStatement(new StmtVarDecl(exp, t, resName, null));
        ExprVar res = new ExprVar(exp, resName);

        StmtBlock thenBlock = null;
        {
            List<Statement> oldStatements = newStatements;
            newStatements = new ArrayList<Statement>();

            b = doExpression(b);
            newStatements.add(new StmtAssign(res, b));

            thenBlock = new StmtBlock(exp, newStatements);
            newStatements = oldStatements;
        }

        StmtBlock elseBlock = null;
        if (c != null) {
            List<Statement> oldStatements = newStatements;
            newStatements = new ArrayList<Statement>();

            c = doExpression(c);
            newStatements.add(new StmtAssign(res, c));

            elseBlock = new StmtBlock(exp, newStatements);
            newStatements = oldStatements;
        }

        addStatement(new StmtIfThen(exp, a, thenBlock, elseBlock));

        return res;
    }

    /** We have to conditionally protect function calls. */
    public Object visitExprBinary(ExprBinary eb) {
        String op = eb.getOpString();
        if (op.equals("&&") || op.equals("||"))
            return doLogicalExpr(eb);
        else
            return super.visitExprBinary(eb);
    }

    public FunctionParamExtension(TempVarGen varGen) {
        this(null, varGen);
	}

    public FunctionParamExtension(SymbolTable symtab, TempVarGen varGen) {
		super(symtab);
		paramCopyRes=new ParameterCopyResolver();
        this.varGen = varGen;
	}

	private String getOutParamName() {
		return "_out";
	}

	String rvname = null;
	private String getNewOutID(String nm) {
	    if(rvname != null && nm.contains("_out")){
	        return rvname + "_r_"+(outCounter++);
	    }else{
	        return nm+"_r_"+(outCounter++);
	    }
	}

	public Object visitStmtAssign(StmtAssign sa){
	    if(sa.getLHS() instanceof ExprVar){
	        rvname = sa.getLHS().toString();
	    }
	    Object o = super.visitStmtAssign(sa);
	    rvname = null;
	    return o;
	    
	}
	

	private String getNewInCpID(String oldName) {
		return oldName+"_"+(inCpCounter++);
	}

	private List getOutputParams(Function f) {
		List params=f.getParams();
		for(int i=0;i<params.size();i++)
			if(((Parameter)params.get(i)).getPtype() == Parameter.OUT)
				return params.subList(i,params.size());
		return Collections.EMPTY_LIST;
	}

    public Object visitProgram(Program prog) {
        SymbolTable oldSymTab = symtab;
        symtab = new SymbolTable(symtab);
        nres = new NameResolver();

        List<StreamSpec> oldStreams = new ArrayList<StreamSpec>();
        for (StreamSpec spec : prog.getStreams()) {
            List<Function> funs = new ArrayList<Function>();
            for (Function fun : (List<Function>) spec.getFuncs()) {
                Type retType = fun.getReturnType();
                List<Parameter> params = new ArrayList<Parameter>(fun.getParams());
                if (!retType.equals(TypePrimitive.voidtype)) {
                    params.add(new Parameter(retType, getOutParamName(), Parameter.OUT));
                }
                funs.add(fun.creator().params(params).create());
            }

            StreamSpec tpkg =
                    new StreamSpec(spec, spec.getName(), spec.getStructs(),
                            spec.getVars(), funs);
            nres.populate(tpkg);
            oldStreams.add(tpkg);

        }

        List<StreamSpec> newStreams = new ArrayList<StreamSpec>();
        for (StreamSpec ssOrig : oldStreams) {
            newStreams.add((StreamSpec) ssOrig.accept(this));
        }

        Program o = prog.creator().streams(newStreams).create();

        symtab = oldSymTab;
        return o;
    }



	Set<String> currentRefParams = new HashSet<String>();

	public Object visitFunction(Function func) {
		if(func.isUninterp() ) return func;

		{
			currentRefParams.clear();
			List<Parameter> lp = func.getParams();
			for(Iterator<Parameter> it = lp.iterator(); it.hasNext(); ){
				Parameter p = it.next();
				if(p.isParameterOutput()){
					currentRefParams.add(p.getName());
				}
			}
		}


		currentFunction=func;
			outCounter=0;
			inCpCounter=0;
			func=(Function) super.visitFunction(func);
		currentFunction=null;

		//if(func.getReturnType()==TypePrimitive.voidtype) return func;
        paramCopyRes.setNres(nres);
		func=(Function)func.accept(paramCopyRes);




		List<Statement> stmts=new ArrayList<Statement>(((StmtBlock)func.getBody()).getStmts());
		
		if(initOutputs){

			List<Parameter> lp = func.getParams();
			for(Iterator<Parameter> it = lp.iterator(); it.hasNext(); ){
				Parameter p = it.next();
				if(p.getPtype() == Parameter.OUT){
					Parameter outParam = p;
					String outParamName  = outParam.getName();
					assert outParam.isParameterOutput();

                    Expression defaultValue = func.getReturnType().defaultValue();
					assert defaultValue != null : "[FunctionParamExtension] default value null!";
					if (defaultValue == null) { assertFalse(); }
					
					stmts.add(0, new StmtAssign(new ExprVar(func, outParamName), defaultValue));
				}
			}
		}
        func =
                func.creator().returnType(TypePrimitive.voidtype).body(
                        new StmtBlock(func, stmts)).create();
		return func;
	}



	
	


	@Override
	public Object visitStmtLoop(StmtLoop stmt)
	{
		Statement body=stmt.getBody();
		if(body!=null && !(body instanceof StmtBlock))
			body=new StmtBlock(stmt,Collections.singletonList(body));
		if(body!=stmt.getBody())
			stmt=new StmtLoop(stmt,stmt.getIter(),body);
		return super.visitStmtLoop(stmt);
	}

	public Object visitExprFunCall(ExprFunCall exp) {
		// first let the superclass process the parameters (which may be function calls)
		exp=(ExprFunCall) super.visitExprFunCall(exp);

		// resolve the function being called
		Function fun;
		try{
            fun = nres.getFun(exp.getName(), exp);
		}catch(UnrecognizedVariableException e){
            // FIXME -- restore error noise
            throw e;
            // throw new UnrecognizedVariableException(exp + ": Function name " +
            // e.getMessage() + " not found" );
		}
		// now we create a temp (or several?) to store the result

		List<Expression> args=new ArrayList<Expression>(fun.getParams().size());
		List<Expression> existingArgs=exp.getParams();

		List<Parameter> params=fun.getParams();

		List<Expression> tempVars = new ArrayList<Expression>();
		List<Statement> refAssigns = new ArrayList<Statement>();

		Map<String, Expression> pmap = new HashMap<String, Expression>();
        VarReplacer vrep = new VarReplacer(pmap);
		
		int psz = 0;
		for(int i=0;i<params.size();i++){
			Parameter p = params.get(i);
			int ptype = p.getPtype();
			Expression oldArg= (p.getType() instanceof TypeStruct || p.getType() instanceof TypeStructRef) ? new ExprNullPtr() : getDefaultValue(p.getType()) ;
			if(ptype == Parameter.REF || ptype == Parameter.IN){
				oldArg=(Expression) existingArgs.get(psz);
				++psz;
			}
			
			if(oldArg != null && oldArg instanceof ExprVar || (oldArg instanceof ExprConstInt && !p.isParameterOutput())){
				args.add(oldArg);
				pmap.put(p.getName(), oldArg);
			}else{
				String tempVar = getNewOutID(p.getName());
				Statement decl = new StmtVarDecl(exp, (Type)p.getType().accept(vrep), tempVar, oldArg);
				ExprVar ev =new ExprVar(exp,tempVar);
				args.add(ev);
				pmap.put(p.getName(), ev);
				addStatement(decl);
				if(ptype == Parameter.OUT){
					tempVars.add(ev);
				}
				if(ptype == Parameter.REF){
				    assert ev != null;
					refAssigns.add(new StmtAssign(oldArg, ev  ));
				}
			}
		}

		ExprFunCall newcall=new ExprFunCall(exp,exp.getName(),args);
		addStatement( new StmtExpr(newcall));
		addStatements(refAssigns);

		// replace the original function call with an instance of the temp variable
		// (which stores the return value)
		if(tempVars.size() == 0){
			return null;
		}
		assert tempVars.size()==1; //TODO handle the case when it's >1
		return tempVars.get(0);
	}







	



	@Override
	public Object visitStmtReturn(StmtReturn stmt) {
		FENode cx=stmt;
		List<Statement> oldns = newStatements;
		
		if (stmt.getValue() == null) {
		    // NOTE -- ignore if already processed...
		    return stmt;
		}
		
		this.newStatements = new ArrayList<Statement> ();		
		stmt=(StmtReturn) super.visitStmtReturn(stmt);
		
		List params=getOutputParams(currentFunction);
		for(int i=0;i<params.size();i++) {
			Parameter param=(Parameter) params.get(i);
			String name=param.getName();
			Statement assignRet=new StmtAssign(cx, new ExprVar(cx, name), stmt.getValue(), 0);
			newStatements.add(assignRet);
		}
		newStatements.add(new StmtReturn(stmt, null));
		 
		Statement ret=new StmtBlock(cx,newStatements);
		newStatements = oldns;
		
		return ret;
	}
	

	
	protected Expression getDefaultValue(Type t) {
		Expression defaultValue = null;
		if(t.isStruct()){
			defaultValue = ExprNullPtr.nullPtr;
		} else {
			defaultValue = ExprConstInt.zero;
		}
		
		return defaultValue;
	}

}
