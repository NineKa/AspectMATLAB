package abstractPattern.modifier;

import Matlab.Utils.IReport;
import abstractPattern.Modifier;
import abstractPattern.analysis.PatternClassifier;
import ast.ASTNode;
import ast.NotExpr;

public class ModifierNot extends Modifier{
    private NotExpr astNodes = null;

    private Modifier operand = null;

    public ModifierNot(NotExpr notExpr) {
        this.astNodes = notExpr;
        this.operand = PatternClassifier.buildModifier(notExpr.getOperand());
    }

    public Modifier getOperand() {
        return operand;
    }

    @Override
    public boolean isValid() {
        return this.operand.isValid();
    }

    @Override
    public IReport getValidationReport(String pFilepath) {
        return this.operand.getValidationReport(pFilepath);
    }

    @Override
    public Class<? extends ASTNode> getASTPatternClass() {
        return NotExpr.class;
    }

    @Override
    public String toString() {
        return String.format(
                "~%s",
                this.operand.toString()
        );
    }
}