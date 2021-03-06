package abstractPattern.primitive;

import Matlab.Utils.IReport;
import Matlab.Utils.Message;
import Matlab.Utils.Report;
import abstractPattern.Modifier;
import abstractPattern.Primitive;
import abstractPattern.modifier.Dimension;
import abstractPattern.modifier.IsType;
import abstractPattern.modifier.Within;
import abstractPattern.signature.Signature;
import abstractPattern.type.WeaveType;
import ast.*;
import natlab.toolkits.analysis.varorfun.VFAnalysis;
import natlab.toolkits.analysis.varorfun.VFDatum;
import transformer.util.AccessMode;
import transformer.util.IsPossibleJointPointResult;
import transformer.util.RuntimeInfo;

import java.util.HashMap;
import java.util.Map;

public class Set extends Primitive{
    private PatternSet astNodes = null;

    private String variableName = null;
    private Signature signature = null;

    public Set(PatternSet set) {
        this.astNodes = set;
        /* --- refactor --- */
        assert this.astNodes.getIdentifier() != null;
        if (this.astNodes.getFullSignature() == null) this.astNodes.setFullSignature(new FullSignature());
        /* ---------------- */
        this.variableName = this.astNodes.getIdentifier().getID();
        this.signature = new Signature(this.astNodes.getFullSignature());
    }

    public String getVariableName() {
        return variableName;
    }

    public Signature getSignature() {
        return signature;
    }

    @Override
    public boolean isValid() {
        if (this.variableName.equals("..")) return false;
        if (!this.signature.isValid()) return false;
        return true;
    }

    @Override
    public IReport getValidationReport(String pFilepath) {
        Report report = new Report();
        for (Message message : this.signature.getValidationReport(pFilepath)) report.Add(message);
        if (this.variableName.equals("..")) {
            report.AddError(
                    pFilepath,
                    this.astNodes.getIdentifier().getStartLine(),
                    this.astNodes.getIdentifier().getStartColumn(),
                    "wildcard [..] is not a valid matcher in set pattern for variable name, use [*] instead"
            );
        }
        return report;
    }

    @Override
    public Class<? extends ASTNode> getASTPatternClass() {
        return PatternSet.class;
    }

    @Override
    public String toString() {
        String setStr = String.format("set(%s : %s)", this.variableName, this.signature.toString());
        if (this.isModified()) {
            String appendingStr = "";
            for (int iter = 0; iter < this.getModifiers().size(); iter++) {
                appendingStr = appendingStr + this.getModifiers().get(iter);
                if (iter + 1 < this.getModifiers().size()) appendingStr = appendingStr + " & ";
            }
            return String.format("(%s & %s)", setStr, appendingStr);
        } else {
            return setStr;
        }
    }

    @Override
    public ASTNode getASTExpr() {
        return this.astNodes;
    }

    @Override
    public boolean isProperlyModified() {
        for (Modifier modifier : this.getBadicModifierSet()) {
            if (modifier instanceof Dimension)  continue;
            if (modifier instanceof IsType)     continue;
            if (modifier instanceof Within)     continue;
            /* control flow should not reach here */
            throw new AssertionError();
        }
        return true;
    }

    @Override
    public IReport getModifierValidationReport(String pFilepath) {
        Report report = new Report();

        for (Modifier modifier : this.getBadicModifierSet()) {
            if (modifier instanceof Dimension) {
                if (!this.signature.getDimension().needValidation()) continue;
                report.AddWarning(
                        pFilepath,
                        this.astNodes.getStartLine(),
                        this.astNodes.getStartColumn(),
                        String.format(
                                "apply dimension (%s[%d : %d]) pattern to such pattern, may result in no match",
                                modifier.toString(),
                                modifier.getASTExpr().getStartLine(),
                                modifier.getASTExpr().getStartColumn()
                        )
                );
                continue;
            }
            if (modifier instanceof IsType) {
                if (!this.signature.getType().needValidation()) continue;
                report.AddWarning(
                        pFilepath,
                        this.astNodes.getStartLine(),
                        this.astNodes.getStartColumn(),
                        String.format(
                                "apply type (%s[%d : %d]) pattern to such pattern, may result in no match",
                                modifier.toString(),
                                modifier.getASTExpr().getStartLine(),
                                modifier.getASTExpr().getStartColumn()
                        )
                );
                continue;
            }
            if (modifier instanceof Within) continue;
            /* control flow should not reach here */
            throw new AssertionError();
        }

        return report;
    }


    @Override
    public Map<WeaveType, Boolean> getWeaveInfo() {
        Map<WeaveType, Boolean> weaveTypeBooleanMap = new HashMap<>();
        weaveTypeBooleanMap.put(WeaveType.Before, true);
        weaveTypeBooleanMap.put(WeaveType.After,  true);
        weaveTypeBooleanMap.put(WeaveType.Around, true);
        return weaveTypeBooleanMap;
    }

    private IsPossibleJointPointResult isPossibleJointPointNameExpr(NameExpr astNode, RuntimeInfo runtimeInfo) {
        /* check if this indeed a variable reading (Query Kind Analysis) */
        try {
            Name variableIdentifier = ((NameExpr) astNode).getName();
            VFDatum analysisResult = runtimeInfo.kindAnalysis.getResult(variableIdentifier);
            boolean possibleVariable = false;
            if (analysisResult.isVariable()) possibleVariable = true; /* Kind analysis resolve as variable*/
            if (analysisResult.isID()) possibleVariable = true; /* Kind analysis cannot resolve -> assume true */
            if (!possibleVariable) { /* false return */
                IsPossibleJointPointResult result = new IsPossibleJointPointResult();
                result.reset();
                return result;
            }
        } catch (NullPointerException exception) {
            /* such exception is caused by in proper kindAnalysis */
            /* code revision required, if control flow reach here */
            throw new AssertionError();
        }
        /* check if this indeed a variable reading */
        if (runtimeInfo.accessMode != AccessMode.Write) {
            IsPossibleJointPointResult result = new IsPossibleJointPointResult();
            result.reset();
            return result;
        }
        /* variable name check */
        String actualVariableName = ((NameExpr) astNode).getName().getID();
        if (!this.getVariableName().equals("*") && !this.getVariableName().equals(actualVariableName)) {
            IsPossibleJointPointResult result = new IsPossibleJointPointResult();
            result.reset();
            return result;
        }

        /* claim such pattern is possibly matched joint point */
        IsPossibleJointPointResult result = new IsPossibleJointPointResult();
        result.reset();
        result.isSets = true;
        return result;
    }

    private IsPossibleJointPointResult isPossibleJointPointParametrizedExpr(ParameterizedExpr astNode, RuntimeInfo runtimeInfo) {
        /* structure check */
        if (!(astNode.getTarget() instanceof NameExpr)) {
            IsPossibleJointPointResult result = new IsPossibleJointPointResult();
            result.reset();
            return result;
        }
        /* check if this is indeed a variable reading (Query Kind Analysis) */
        try {
            Name targetName = ((NameExpr) astNode.getTarget()).getName();
            VFAnalysis kindAnalysis = runtimeInfo.kindAnalysis;
            VFDatum kindAnalysisResult = kindAnalysis.getResult(targetName);
            boolean possibleGet = false;
            if (kindAnalysisResult.isVariable()) possibleGet = true;
            if (kindAnalysisResult.isID()) possibleGet = true;
            if (!possibleGet) {
                IsPossibleJointPointResult result = new IsPossibleJointPointResult();
                result.reset();
                return result;
            }
        } catch (NullPointerException exception) {
            /* such exception is caused by in proper kindAnalysis */
            /* code revision required, if control flow reach here */
            throw new AssertionError();
        }
        /* check if this indeed a variable reading */
        if (runtimeInfo.accessMode != AccessMode.Write) {
            IsPossibleJointPointResult result = new IsPossibleJointPointResult();
            result.reset();
            return result;
        }
        /* variable name check */
        String actualVariableName = ((NameExpr) astNode.getTarget()).getName().getID();
        if (!this.getVariableName().equals("*") && !this.getVariableName().equals(actualVariableName)) {
            IsPossibleJointPointResult result = new IsPossibleJointPointResult();
            result.reset();
            return result;
        }

        /* claim such pattern is possibly matched joint point */
        IsPossibleJointPointResult result = new IsPossibleJointPointResult();
        result.reset();
        result.isSets = true;
        return result;
    }

    private IsPossibleJointPointResult isPossibleJointPointCellIndexExpr(CellIndexExpr astNode, RuntimeInfo runtimeInfo) {
        /* structure check */
        if (!(astNode.getTarget() instanceof NameExpr)) {
            IsPossibleJointPointResult result = new IsPossibleJointPointResult();
            result.reset();
            return result;
        }
        /* check if this is indeed a variable reading (Query Kind Analysis) */
        try {
            Name targetName = ((NameExpr) astNode.getTarget()).getName();
            VFAnalysis kindAnalysis = runtimeInfo.kindAnalysis;
            VFDatum kindAnalysisResult = kindAnalysis.getResult(targetName);
            boolean possibleGet = false;
            if (kindAnalysisResult.isVariable()) possibleGet = true;
            if (kindAnalysisResult.isID()) possibleGet = true;
            if (!possibleGet) {
                IsPossibleJointPointResult result = new IsPossibleJointPointResult();
                result.reset();
                return result;
            }
        } catch (NullPointerException exception) {
            /* such exception is caused by in proper kindAnalysis */
            /* code revision required, if control flow reach here */
            throw new AssertionError();
        }
        /* check if this indeed a variable reading */
        if (runtimeInfo.accessMode != AccessMode.Write) {
            IsPossibleJointPointResult result = new IsPossibleJointPointResult();
            result.reset();
            return result;
        }
        /* variable name check */
        String actualVariableName = ((NameExpr) astNode.getTarget()).getName().getID();
        if (!this.getVariableName().equals("*") && !this.getVariableName().equals(actualVariableName)) {
            IsPossibleJointPointResult result = new IsPossibleJointPointResult();
            result.reset();
            return result;
        }

        /* claim such pattern is possibly matched joint point */
        IsPossibleJointPointResult result = new IsPossibleJointPointResult();
        result.reset();
        result.isSets = true;
        return result;
    }

    private IsPossibleJointPointResult isPossibleJointPointDotExpr(DotExpr astNode, RuntimeInfo runtimeInfo) {
        /* structure check */
        if (!(astNode.getTarget() instanceof  NameExpr)) {
            IsPossibleJointPointResult result = new IsPossibleJointPointResult();
            result.reset();
            return result;
        }
        /* check if this is indeed a variable reading (Query Kind Analysis) */
        try {
            Name targetName = ((NameExpr) astNode.getTarget()).getName();
            VFAnalysis kindAnalysis = runtimeInfo.kindAnalysis;
            VFDatum kindAnalysisResult = kindAnalysis.getResult(targetName);
            boolean possibleGet = false;
            if (kindAnalysisResult.isVariable()) possibleGet = true;
            if (kindAnalysisResult.isID()) possibleGet = true;
            if (!possibleGet) {
                IsPossibleJointPointResult result = new IsPossibleJointPointResult();
                result.reset();
                return result;
            }
        } catch (NullPointerException exception) {
            /* such exception is caused by in proper kindAnalysis */
            /* code revision required, if control flow reach here */
            throw new AssertionError();
        }
        /* check if this indeed a variable reading */
        if (runtimeInfo.accessMode != AccessMode.Write) {
            IsPossibleJointPointResult result = new IsPossibleJointPointResult();
            result.reset();
            return result;
        }
        /* variable name check */
        String actualVariableName = ((NameExpr) astNode.getTarget()).getName().getID();
        if (!this.getVariableName().equals("*") && !this.getVariableName().equals(actualVariableName)) {
            IsPossibleJointPointResult result = new IsPossibleJointPointResult();
            result.reset();
            return result;
        }

        /* claim such pattern is possibly matched joint point */
        IsPossibleJointPointResult result = new IsPossibleJointPointResult();
        result.reset();
        result.isSets = true;
        return result;
    }

    @Override
    public IsPossibleJointPointResult isPossibleJointPoint(ASTNode astNode, RuntimeInfo runtimeInfo) {
        if (astNode instanceof NameExpr) {
            return isPossibleJointPointNameExpr((NameExpr) astNode, runtimeInfo);
        }
        if (astNode instanceof ParameterizedExpr) {
            return isPossibleJointPointParametrizedExpr((ParameterizedExpr) astNode, runtimeInfo);
        }
        if (astNode instanceof CellIndexExpr) {
            return isPossibleJointPointCellIndexExpr((CellIndexExpr) astNode, runtimeInfo);
        }
        if (astNode instanceof DotExpr) {
            return isPossibleJointPointDotExpr((DotExpr) astNode, runtimeInfo);
        }

        /* impossible to find a match */
        IsPossibleJointPointResult result = new IsPossibleJointPointResult();
        result.reset();
        return result;
    }
}
