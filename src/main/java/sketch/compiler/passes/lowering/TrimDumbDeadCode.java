/*
 * Copyright 2003 by the Massachusetts Institute of Technology.
 *
 * Permission to use, copy, modify, and distribute this
 * software and its documentation for any purpose and without
 * fee is hereby granted, provided that the above copyright
 * notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting
 * documentation, and that the name of M.I.T. not be used in
 * advertising or publicity pertaining to distribution of the
 * software without specific, written prior permission.
 * M.I.T. makes no representations about the suitability of
 * this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 */

package streamit.frontend.passes;

import streamit.frontend.nodes.ExprArray;
import streamit.frontend.nodes.ExprConstBoolean;
import streamit.frontend.nodes.ExprConstChar;
import streamit.frontend.nodes.ExprConstFloat;
import streamit.frontend.nodes.ExprConstInt;
import streamit.frontend.nodes.ExprConstStr;
import streamit.frontend.nodes.ExprField;
import streamit.frontend.nodes.ExprVar;
import streamit.frontend.nodes.Expression;
import streamit.frontend.nodes.FEReplacer;
import streamit.frontend.nodes.StmtExpr;

/**
 * Remove expression statements with no side effects.  In particular,
 * drop expression statements that are purely constants or local
 * variable declarations.  (Don't trim anything with a child
 * expression, that requires effort.)
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id$
 */
public class TrimDumbDeadCode extends FEReplacer
{
    public Object visitStmtExpr(StmtExpr stmt)
    {
        Expression expr = stmt.getExpression();
        // NB: for array and field expressions, we really should look
        // for side-effect-causing children.  At this point in the
        // process it's probably okay, though.
        if (expr instanceof ExprArray ||
            expr instanceof ExprConstBoolean ||
            expr instanceof ExprConstChar ||
            expr instanceof ExprConstFloat ||
            expr instanceof ExprConstInt ||
            expr instanceof ExprConstStr ||
            expr instanceof ExprField ||
            expr instanceof ExprVar)
            return null;
        return stmt;
    }
}
