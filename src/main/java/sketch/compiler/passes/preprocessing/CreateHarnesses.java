package sketch.compiler.passes.preprocessing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sketch.compiler.ast.core.FENode;
import sketch.compiler.ast.core.FEReplacer;
import sketch.compiler.ast.core.Function;
import sketch.compiler.ast.core.Package;
import sketch.compiler.ast.core.Parameter;
import sketch.compiler.ast.core.TempVarGen;
import sketch.compiler.ast.core.exprs.*;
import sketch.compiler.ast.core.stmts.*;
import sketch.compiler.ast.core.typs.StructDef;
import sketch.compiler.ast.core.typs.StructDef.StructFieldEnt;
import sketch.compiler.ast.core.typs.Type;
import sketch.compiler.ast.core.typs.TypeArray;
import sketch.compiler.ast.core.typs.TypePrimitive;
import sketch.compiler.ast.core.typs.TypeStructRef;
import sketch.util.exceptions.ExceptionAtNode;
import sketch.util.exceptions.UnrecognizedVariableException;

class VarReplacer extends FEReplacer {
    Map<String, String> varMap;

    public VarReplacer(Map<String, String> varMap) {
        this.varMap = varMap;
    }

    @Override
    public Object visitExprVar(ExprVar exp) {
        String name = exp.getName();
        if (varMap.containsKey(name)) {
            return new ExprVar(exp, varMap.get(name));
        }
        return exp;
    }
}

public class CreateHarnesses extends FEReplacer {
    Map<String, String> produceFuns = new HashMap<String, String>();
    Map<String, String> checkFuns = new HashMap<String, String>();
    TempVarGen vargen;

    public CreateHarnesses(TempVarGen varGen) {
        this.vargen = varGen;
    }

    @Override
    public Object visitPackage(Package pkg) {
        nres.setPackage(pkg);

        List<Function> newFuns = pkg.getFuncs();
        List<StmtSpAssert> newSpAsserts = new ArrayList<StmtSpAssert>();
        for (StmtSpAssert sa : pkg.getSpAsserts()) {
            List<String> inputParams = new ArrayList<String>();
            List<Type> inputTypes = new ArrayList<Type>();
            List<Statement> body = new ArrayList<Statement>();
            Map<String, List<Expression>> outputsTracker =
                    new HashMap<String, List<Expression>>();
            Map<String, String> varRenameTracker = new HashMap<String, String>();

            Map<String, Expression> varBindings = sa.getVarBindings();

            for (String var : varBindings.keySet()) {
                Expression exp = varBindings.get(var);
                if (exp instanceof ExprFunCall) {
                    List<Expression> outExprs = new ArrayList<Expression>();
                    List<Type> outTypes = processExprFunCall((ExprFunCall) exp, inputParams, inputTypes, body,
 varRenameTracker, varBindings,
                                    outExprs);
                    outputsTracker.put(var, outExprs);
                    Type varType = outTypes.get(outTypes.size() - 1);
                    VarReplacer vr = new VarReplacer(varRenameTracker);
                    StmtVarDecl stmt = new StmtVarDecl(sa, varType, var, exp.doExpr(vr));
                    body.add(stmt);
                } else {
                    assert (false); // Not yet supported
                }
            }

            List<Statement> mainBody = new ArrayList<Statement>();

            for (Expression assert_expr : sa.getAssertExprs()) {
                List<Expression> lhs_out = new ArrayList<Expression>();
                List<Expression> rhs_out = new ArrayList<Expression>();
                List<Type> out_type = new ArrayList<Type>();
                boolean assertAllOuts = false;
                if (assert_expr instanceof ExprBinary) {
                    ExprBinary binExp = (ExprBinary) assert_expr;
                    if (binExp.getOp() == ExprBinary.BINOP_EQ ||
                            binExp.getOp() == ExprBinary.BINOP_TEQ)
                    {
                        Expression lhs = binExp.getLeft();
                        if (lhs instanceof ExprVar) {
                            String name = ((ExprVar) lhs).getName();
                            lhs_out = outputsTracker.get(name);
                            lhs = sa.getExprForVar(name);
                            if (lhs == null)
                                throw new ExceptionAtNode(assert_expr,
                                        "Unknown variable " + ((ExprVar) lhs).getName());
                        } else if (lhs instanceof ExprFunCall) {
                            out_type =
                                    processExprFunCall((ExprFunCall) lhs, inputParams,
                                            inputTypes, mainBody, varRenameTracker,
                                            varBindings, lhs_out);
                        }
                        Expression rhs = binExp.getRight();
                        if (rhs instanceof ExprVar) {
                            String name = ((ExprVar) rhs).getName();
                            rhs_out = outputsTracker.get(name);
                            rhs = sa.getExprForVar(name);
                            if (rhs == null)
                                throw new ExceptionAtNode(assert_expr,
                                        "Unknown variable " + ((ExprVar) rhs).getName());
                        } else if (rhs instanceof ExprFunCall) {
                            out_type =
                                    processExprFunCall((ExprFunCall) rhs, inputParams,
                                            inputTypes, mainBody, varRenameTracker,
                                            varBindings, rhs_out);
                        }

                        if (lhs instanceof ExprFunCall && rhs instanceof ExprFunCall) {
                            // Things to check
                            // 1. All function names should correspond to valid functions
                            // in the package -
                            // TODO support different packages
                            // 2. f1 and f2 should operate on same type of inputs and
                            // produce same type of
                            // outputs


                            System.out.println("Considering the following invariant " +
                                    rhs.toString() + " == " + lhs.toString());

                            assertAllOuts = true;
                            newSpAsserts.add(new StmtSpAssert(assert_expr.getContext(),
                                    (ExprFunCall) rhs, (ExprFunCall) lhs));

                        }

                    }
                }
                VarReplacer vr = new VarReplacer(varRenameTracker);
                mainBody.add(new StmtAssert(assert_expr, assert_expr.doExpr(vr), false));

                if (assertAllOuts) {
                    if (lhs_out.size() != rhs_out.size()) {
                        throw new ExceptionAtNode(assert_expr, "Output sizes don't match");
                    }
                    assert (lhs_out.size() == out_type.size() - 1);
                    if (lhs_out.size() > 0) {
                        for (int i = 0; i < lhs_out.size(); i++) {
                            assertEqual(lhs_out.get(i), rhs_out.get(i), out_type.get(i),
                                    mainBody);
                        }
                    }
                }
            }
            Expression preCond = sa.getPreCond();
            if (preCond != null) {
                StmtIfThen sif =
                        new StmtIfThen(sa, preCond, new StmtBlock(mainBody), null);
                body.add(sif);
            } else {
                body.addAll(mainBody);
            }

            Function.FunctionCreator fc =
                    Function.creator(sa.getContext(), vargen.nextVar("main"),
                            Function.FcnType.Harness);
            fc.returnType(TypePrimitive.voidtype);

            List<Parameter> params = new ArrayList<Parameter>();

            for (int i = 0; i < inputTypes.size(); i++) {
                params.add(new Parameter(sa, inputTypes.get(i), inputParams.get(i)));
            }

            for (int i = 0; i < inputTypes.size(); i++) {
                if (inputTypes.get(i).isStruct()) {
                    String fname =
                            createCheckInputFun(sa, (TypeStructRef) inputTypes.get(i),
                                    newFuns);
                    List<Expression> pm = new ArrayList<Expression>();
                    pm.add(new ExprVar(sa, inputParams.get(i)));
                    body.add(0, new StmtIfThen(sa, new ExprFunCall(sa, fname, pm),
                            new StmtEmpty(sa), new StmtReturn(sa, null)));

                } else if (inputTypes.get(i).isArray()) {
                    TypeArray arrType = (TypeArray) (inputTypes.get(i));
                    Type base = arrType.getBase();
                    if (base.isStruct()) {
                        String fname =
                                createCheckInputFun(sa, (TypeStructRef) base,
                                        newFuns);
                        Expression len = arrType.getLength();
                        String idx = vargen.nextVar("i");
                        ExprVar idx_var = new ExprVar(sa, idx);
                        StmtVarDecl init = new StmtVarDecl(sa, TypePrimitive.inttype, idx, ExprConstInt.zero);
                        Expression cond = new ExprBinary(sa, ExprBinary.BINOP_LT, idx_var, len);
                        StmtAssign incr = new StmtAssign(sa, idx_var, new ExprBinary(sa, ExprBinary.BINOP_ADD, idx_var, ExprConstInt.one));
                        List<Expression> pm = new ArrayList<Expression>();
                        pm.add(new ExprArrayRange(sa, new ExprVar(sa, inputParams.get(i)), idx_var));
                        Statement forBody =
                                new StmtBlock(new StmtIfThen(sa, new ExprFunCall(sa,
                                        fname, pm), new StmtEmpty(sa), new StmtReturn(sa,
                                        null)));
                        StmtFor forSt = new StmtFor(sa, init, cond, incr, forBody, true);
                        body.add(0, forSt);
                    }
                }
            }

            fc.params(params);

            fc.body(new StmtBlock(body));

            Function harness = fc.create();
            harness.setPkg(pkg.getName());
            newFuns.add(harness);

        }

        return new Package(pkg, pkg.getName(), pkg.getStructs(), pkg.getVars(), newFuns,
                newSpAsserts);
    }

    private void assertEqual(Expression e1, Expression e2, Type type, List<Statement> body)
    {
        if (type.isStruct()) {
            Expression cond = new ExprBinary(ExprBinary.BINOP_EQ, e1, new ExprNullPtr());
            Statement ifS = new StmtAssert(new ExprBinary( ExprBinary.BINOP_EQ, e2, new ExprNullPtr()), false);
            Statement elseS = new StmtAssert(new ExprBinary(ExprBinary.BINOP_TEQ, e1, e2), false);
            body.add(new StmtIfThen(e1, cond, ifS, elseS));
        } else if (type.isArray()) {
            TypeArray arrType = (TypeArray) type;
            Type base = arrType.getBase();

            Expression len = arrType.getLength();
            String idx = vargen.nextVar("i");
            ExprVar idx_var = new ExprVar(e1, idx);
            StmtVarDecl init =
                    new StmtVarDecl(e1, TypePrimitive.inttype, idx, ExprConstInt.zero);
            Expression cond = new ExprBinary(e1, ExprBinary.BINOP_LT, idx_var, len);
            StmtAssign incr =
                    new StmtAssign(e1, idx_var, new ExprBinary(e1, ExprBinary.BINOP_ADD,
                            idx_var, ExprConstInt.one));
            Expression lhs = new ExprArrayRange(e1, e1, idx_var);
            Expression rhs = new ExprArrayRange(e1, e2, idx_var);
            Statement forBody;
            if (base.isStruct()) {
                Expression ifcond =
                        new ExprBinary(ExprBinary.BINOP_EQ, lhs, new ExprNullPtr());
                Statement ifS = new StmtAssert(new ExprBinary( ExprBinary.BINOP_EQ, rhs, new ExprNullPtr()), false);
                Statement elseS = new StmtAssert(new ExprBinary(ExprBinary.BINOP_TEQ, lhs, rhs), false);
                forBody = new StmtBlock(new StmtIfThen(e1, ifcond, ifS, elseS));
            } else {
                forBody =
                        new StmtAssert(new ExprBinary(ExprBinary.BINOP_EQ, lhs, rhs),
                                false);
            }
            StmtFor forSt = new StmtFor(e1, init, cond, incr, forBody, true);
            body.add(forSt);

        } else {
            body.add(new StmtAssert(new ExprBinary(ExprBinary.BINOP_EQ, e1, e2), false));
        }
    }

    private List<Type> processExprFunCall(ExprFunCall exp, List<String> inputParams,
            List<Type> inputTypes, List<Statement> body,
            Map<String, String> varRenameTracker, Map<String, Expression> varBindings,
            List<Expression> outExprs)
    {
        List<Type> outTypes = new ArrayList<Type>();
        Function fun;
        try {
            fun = nres.getFun(exp.getName(), exp);
        } catch (UnrecognizedVariableException e) {
            throw new ExceptionAtNode(exp, "unknown function " + exp.getName());
        }

        List<Expression> fparams = exp.getParams();
        List<Parameter> fActParams = fun.getParams();
        if (fparams.size() != fActParams.size())
            throw new ExceptionAtNode(exp, "Wrong number of parameters " + exp.getName());

        if (!fparams.isEmpty()) {
            if (fparams.get(0) instanceof ExprFunCall) {
                ExprFunCall f2 = (ExprFunCall) fparams.get(0);
                List<Expression> f2params = f2.getParams();
                Function fun2;
                try {
                    fun2 = nres.getFun(f2.getName(), f2);
                } catch (UnrecognizedVariableException e) {
                    throw new ExceptionAtNode(f2, "unknown function " + f2.getName());
                }

                List<Parameter> f2ActParams = fun2.getParams();
                if (f2params.size() != f2ActParams.size())
                    throw new ExceptionAtNode(exp, "Wrong number of parameters " +
                            f2.getName());
                for (int i = 0; i < f2ActParams.size(); i++) {
                    Parameter p = f2ActParams.get(i);
                    if (p.isParameterInput()) {
                        Expression param = f2params.get(i);
                        if (param instanceof ExprVar) {
                            String var = ((ExprVar) param).getName();
                            if (!varBindings.containsKey(var) &&
                                    !inputParams.contains(var))
                            {
                                inputParams.add(var);
                                inputTypes.add(p.getType());
                            }

                        }
                    }
                    if (p.isParameterReference()) {
                        Expression param = f2params.get(i);
                        if (param instanceof ExprVar) {
                            String old_name = ((ExprVar) param).getName();
                            String new_name = vargen.nextVar(old_name);
                            varRenameTracker.put(old_name, new_name);
                            StmtVarDecl varD =
                                    new StmtVarDecl(exp, p.getType(), new_name, param);
                            body.add(varD);
                            outExprs.add(new ExprVar(exp, new_name));

                        } else {
                            assert (false);
                        }
                    }
                }

                for (int i = 1; i < fActParams.size(); i++) {
                    Parameter p = fActParams.get(i);
                    if (p.isParameterInput()) {
                        Expression param = fparams.get(i);
                        if (param instanceof ExprVar) {
                            String var = ((ExprVar) param).getName();
                            if (!varBindings.containsKey(var) &&
                                    !inputParams.contains(var))
                            {
                                inputParams.add(var);
                                inputTypes.add(p.getType());
                            }

                        }
                    }
                    if (p.isParameterReference()) {
                        Expression param = fparams.get(i);
                        if (param instanceof ExprVar) {
                            String old_name = ((ExprVar) param).getName();
                            String new_name = vargen.nextVar(old_name);
                            varRenameTracker.put(old_name, new_name);
                            StmtVarDecl varD =
                                    new StmtVarDecl(exp, p.getType(), new_name, param);
                            body.add(varD);
                            outExprs.add(new ExprVar(exp, new_name));

                        } else {
                            assert (false);
                        }
                    }
                    if (p.isParameterOutput()) {
                        outTypes.add(p.getType());
                    }
                }

            } else {

                for (int i = 0; i < fActParams.size(); i++) {
                    Parameter p = fActParams.get(i);
                    if (p.isParameterInput()) {
                        Expression param = fparams.get(i);
                        if (param instanceof ExprVar) {
                            String var = ((ExprVar) param).getName();
                            if (!varBindings.containsKey(var) &&
                                    !inputParams.contains(var))
                            {
                                inputParams.add(var);
                                inputTypes.add(p.getType());
                            }

                        }
                    }
                    if (p.isParameterReference()) {
                        Expression param = fparams.get(i);
                        if (param instanceof ExprVar) {
                            String old_name = ((ExprVar) param).getName();
                            String new_name = vargen.nextVar(old_name);
                            varRenameTracker.put(old_name, new_name);
                            StmtVarDecl varD =
                                    new StmtVarDecl(exp, p.getType(), new_name, param);
                            body.add(varD);
                            outExprs.add(new ExprVar(exp, new_name));

                        } else {
                            assert (false);
                        }
                    }
                    if (p.isParameterOutput()) {
                        outTypes.add(p.getType());
                    }
                }
            }

        }

        outTypes.add(fun.getReturnType());
        return outTypes;

    }

    private Function createHarness(StmtSpAssert sa, List<ExprVar> inparams,
            List<Type> intypes, List<Type> outtypes, List<Function> newFuns)
    {
        Function.FunctionCreator fc =
                Function.creator(sa.getContext(), vargen.nextVar("main"),
                        Function.FcnType.Harness);
        fc.returnType(TypePrimitive.voidtype);

        List<Parameter> params = new ArrayList<Parameter>();

        for (int i = 0; i < intypes.size(); i++) {
            params.add(new Parameter(sa, intypes.get(i), inparams.get(i).getName()));
        }

        fc.params(params);

        List<Statement> body = new ArrayList<Statement>();

        for (int i = 0; i < intypes.size(); i++) {
            if (intypes.get(i).isStruct()) {
                String fname =
                        createCheckInputFun(sa, (TypeStructRef) intypes.get(i), newFuns);
                List<Expression> pm = new ArrayList<Expression>();
                pm.add(new ExprVar(sa, inparams.get(i).getName()));

                body.add(new StmtIfThen(sa, new ExprFunCall(sa, fname, pm),
                        new StmtEmpty(sa), new StmtReturn(sa, null)));

            }
        }

        String newvar = vargen.nextVar();
        body.add(new StmtVarDecl(sa, outtypes.get(outtypes.size() - 1), newvar,
                sa.getSecondFun()));

        Type t = outtypes.get(outtypes.size() - 1);
        if (t.isStruct()) {
            ExprBinary cond =
                    new ExprBinary(sa, ExprBinary.BINOP_NEQ, new ExprVar(sa, newvar),
                            new ExprNullPtr());
            StmtAssert assertStmt =
                    new StmtAssert(sa, new ExprBinary(sa, ExprBinary.BINOP_TEQ,
                            new ExprVar(sa, newvar), sa.getFirstFun()), false);
            StmtIfThen sif = new StmtIfThen(sa, cond, new StmtBlock(assertStmt), null);

            body.add(sif);
        } else {
            StmtAssert assertStmt =
                    new StmtAssert(sa, new ExprBinary(sa, ExprBinary.BINOP_EQ,
                            new ExprVar(sa, newvar), sa.getFirstFun()), false);
            body.add(assertStmt);
        }


        fc.body(new StmtBlock(body));

        return fc.create();
    }

    private String createCheckInputFun(FENode ctx, TypeStructRef t, List<Function> newFuns)
    {
        if (checkFuns.containsKey(t.getName())) {
            return checkFuns.get(t.getName());
        }
        String fname = vargen.nextVar("check" + "_" + t.getName());
        checkFuns.put(t.getName(), fname);
        Function.FunctionCreator fc =
                Function.creator(ctx, fname, Function.FcnType.Static);
        fc.returnType(TypePrimitive.bittype);
        String var = vargen.nextVar("var");
        List<Parameter> params = new ArrayList<Parameter>();
        params.add(new Parameter(ctx, t, var));
        fc.params(params);
        List<Statement> body = new ArrayList<Statement>();
        ExprVar inp = new ExprVar(ctx, var);
        ExprBinary cond =
                new ExprBinary(ctx, ExprBinary.BINOP_EQ, inp,
                        new ExprNullPtr());
        StmtIfThen nullCaseIf =
                new StmtIfThen(ctx, cond, new StmtReturn(ctx, ExprConstInt.one), null);
        body.add(nullCaseIf);

        List<String> orderedCases = getCasesInOrder(t.getName());
        StmtSwitch swt = new StmtSwitch(ctx, inp);
        for (String c : orderedCases) {
            StructDef ts = nres.getStruct(c);
            Expression cur = ExprConstInt.one;
            for (StructFieldEnt fe : ts.getFieldEntriesInOrder()) {
                Type ft = fe.getType();
                if (ft.isStruct()) {
                    String name = createCheckInputFun(ctx, (TypeStructRef) ft, newFuns);
                    List<Expression> pm = new ArrayList<Expression>();
                    pm.add(new ExprField(ctx, inp, fe.getName()));

                    cur =
                            new ExprBinary(ctx, ExprBinary.BINOP_AND, new ExprFunCall(
                                    ctx, name, pm), cur);
                }
            }
            swt.addCaseBlock(c.split("@")[0], new StmtReturn(ctx, cur));
        }
        swt.addCaseBlock("default", new StmtReturn(ctx, ExprConstInt.zero));
        body.add(swt);
        body.add(new StmtReturn(ctx, ExprConstInt.one));

        fc.body(new StmtBlock(body));
        fc.pkg(nres.curPkg().getName());
        newFuns.add(fc.create());

        return fname;
    }

    private String createFunction(TypeStructRef t, FENode ctx, List<Function> newFuns) {
        if (produceFuns.containsKey(t.getName())) {
            return produceFuns.get(t.getName());
        }
        String fname = vargen.nextVar("produce" + "_" + t.getName());
        produceFuns.put(t.getName(), fname);
        Function.FunctionCreator fc =
                Function.creator(ctx, fname, Function.FcnType.Static);
        fc.returnType(t);
        String arr = vargen.nextVar("arr");
        String bnd = vargen.nextVar("bnd");
        String idx = vargen.nextVar("idx");
        List<Parameter> params = new ArrayList<Parameter>();
        params.add(new Parameter(ctx, new TypeArray(TypePrimitive.inttype,
                ExprConstInt.createConstant(30)), // TODO: eliminate this magic number
                arr));

        params.add(new Parameter(ctx, TypePrimitive.inttype, bnd));
        params.add(new Parameter(ctx, TypePrimitive.inttype, idx, Parameter.REF));

        fc.params(params);

        List<Statement> body = new ArrayList<Statement>();
        ExprBinary cond =
                new ExprBinary(ctx, ExprBinary.BINOP_LE, new ExprVar(ctx, bnd),
                        ExprConstInt.zero);
        StmtIfThen baseCaseIf =
                new StmtIfThen(ctx, cond, new StmtReturn(ctx, new ExprNullPtr()), null);
        body.add(baseCaseIf);
        List<String> orderedCases = getCasesInOrder(t.getName());

        if (orderedCases.size() == 0) {
            body.add(new StmtReturn(ctx, new ExprNullPtr()));
        } else {

            Statement cur =
                    genCase(orderedCases.get(orderedCases.size() - 1), arr, bnd, idx,
                            ctx, newFuns);

            for (int i = orderedCases.size() - 2; i >= 0; i--) {
                Expression cnd =
                        new ExprBinary(ctx, ExprBinary.BINOP_EQ, new ExprArrayRange(
                                new ExprVar(ctx, arr), new ExprVar(ctx, idx)),
                                ExprConstInt.createConstant(i));
                Statement ifPart =
                        genCase(orderedCases.get(i), arr, bnd, idx, ctx, newFuns);
                cur = new StmtIfThen(ctx, cnd, ifPart, cur);
            }
            body.add(cur);
        }

        fc.body(new StmtBlock(body));
        fc.pkg(nres.curPkg().getName());
        newFuns.add(fc.create());

        return fname;

    }

    private Statement genCase(String name, String arr, String bnd, String idx,
            FENode ctx, List<Function> newFuns)
    {
        List<Statement> stmts = new ArrayList<Statement>();
        // First increment the idx
        stmts.add(new StmtExpr(ctx, new ExprUnary(ctx, ExprUnary.UNOP_POSTINC,
                new ExprVar(ctx, idx))));

        List<ExprNamedParam> params = new ArrayList<ExprNamedParam>();
        // TODO: should actually create params for all fields in this struct and its
        // parents
        StructDef ts = nres.getStruct(name);

        for (StructFieldEnt e : ts.getFieldEntriesInOrder()) {
            Expression arg = null;
            Type t = e.getType();
            Expression arr_acc = new ExprArrayRange(new ExprVar(ctx,arr), new ExprUnary(ctx, ExprUnary.UNOP_POSTINC, new ExprVar(ctx, idx)));
            if (t.promotesTo(TypePrimitive.bittype, nres)) {
                arg =
                        new ExprBinary(ctx, ExprBinary.BINOP_EQ, arr_acc,
                                ExprConstInt.zero);
            } else if (t.promotesTo(TypePrimitive.inttype, nres)) {
                arg = arr_acc;
            } else if (t.isStruct()) {
                String funName = createFunction((TypeStructRef) t, ctx, newFuns);
                List<Expression> pm = new ArrayList<Expression>();
                pm.add(new ExprVar(ctx, arr));
                pm.add(new ExprBinary(ctx, ExprBinary.BINOP_SUB, new ExprVar(ctx, bnd),
                        ExprConstInt.one));
                pm.add(new ExprVar(ctx, idx));
                arg = new ExprFunCall(ctx, funName, pm);
            } else {
                assert (false); // TODO: also deal with array fields
            }
            params.add(new ExprNamedParam(ctx, e.getName(), arg));
        }

        ExprNew node = new ExprNew(ctx, new TypeStructRef(name, false), params, false);

        stmts.add(new StmtReturn(ctx, node));
        return new StmtBlock(stmts);
    }

    private List<String> getCasesInOrder(String name) {
        Map<Integer, List<String>> allCases = new HashMap<Integer, List<String>>();
        TypeStructRef type = new TypeStructRef(name, false);

        LinkedList<String> queue = new LinkedList<String>();
        queue.add(name);
        while (!queue.isEmpty()) {
            String n = queue.removeFirst();
            List<String> children = nres.getStructChildren(n);
            if (children.isEmpty()) {
                StructDef ts = nres.getStruct(n);
                int recCount = 0;
                for (StructFieldEnt f : ts.getFieldEntriesInOrder()) {
                    if (f.getType().promotesTo(type, nres))
                        recCount++;
                }
                if (allCases.containsKey(recCount)) {
                    allCases.get(recCount).add(n);
                } else {
                    List<String> cases = new ArrayList<String>();
                    cases.add(n);
                    allCases.put(recCount, cases);
                }

            } else {
                queue.addAll(children);
            }
        }
        Set<Integer> counts = allCases.keySet();
        List<String> orderedCases = new ArrayList<String>();
        for (int c : counts) {
            orderedCases.addAll(allCases.get(c));
        }
        return orderedCases;

    }
}
