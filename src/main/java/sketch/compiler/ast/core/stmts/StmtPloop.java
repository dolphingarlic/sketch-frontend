package streamit.frontend.nodes;

public class StmtPloop extends Statement {
	private StmtVarDecl loopVar;
	   private Expression iter;
	    private Statement body;
	    
	    /** Creates a new loop. */
	    public StmtPloop(FEContext context, String vname, Expression iter, Statement body)
	    {
	        super(context);
	        this.iter = iter;
	        this.body = body;
	        this.loopVar = new StmtVarDecl(context, TypePrimitive.inttype, vname, null);
	    }
	    
	    public StmtPloop(FEContext context, StmtVarDecl loopVar, Expression iter, Statement body)
	    {
	        super(context);
	        this.iter = iter;
	        this.body = body;
	        this.loopVar = loopVar;
	    }
	    
	    /** Return the number of iterations. */
	    public Expression getIter()
	    {
	        return iter;
	    }
	    
	    /** Return the loop body of this. */
	    public Statement getBody()
	    {
	        return body;
	    }
	    
	    public String getLoopVarName(){
	    	return loopVar.getName(0);	    	
	    }
	    
	    public StmtVarDecl getLoopVarDecl(){
	    	return loopVar;
	    }	    
	    
	    /** Accept a front-end visitor. */
	    public Object accept(FEVisitor v)
	    {
	        return v.visitStmtPloop(this);
	    }
	    
	    public String toString()
	    {
	    	return "ploop("+ loopVar + " < " + iter+")...";
	    }

}