package transformer.expr.literal;

import ast.FPLiteralExpr;
import ast.IntLiteralExpr;
import ast.LiteralExpr;
import ast.StringLiteralExpr;
import transformer.TransformerArgument;
import transformer.expr.ExprTrans;

public abstract class LiteralTrans extends ExprTrans {
    public LiteralTrans(TransformerArgument argument, LiteralExpr literalExpr) {
        super(argument, literalExpr);
    }

    @Override
    public boolean hasFurtherTransform() {
        return this.hasTransformOnCurrentNode();
    }

    public static LiteralTrans buildLiteralTransformer(TransformerArgument argument, LiteralExpr expr) {
        if (expr instanceof FPLiteralExpr) return new FPLiteralTrans(argument, (FPLiteralExpr) expr);
        if (expr instanceof IntLiteralExpr) return new IntLiteralTrans(argument, (IntLiteralExpr) expr);
        if (expr instanceof StringLiteralExpr) return new StringLiteralTrans(argument, (StringLiteralExpr) expr);

        /* control flow should not reach here */
        throw new AssertionError();
    }
}
