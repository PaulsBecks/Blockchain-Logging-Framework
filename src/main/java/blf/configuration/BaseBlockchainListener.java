package blf.configuration;

import blf.core.state.ProgramState;
import blf.core.Program;
import blf.core.instructions.SetOutputFolderInstruction;
import blf.grammar.BcqlBaseListener;
import blf.grammar.BcqlParser;
import blf.parsing.InterpreterUtils;
import blf.parsing.VariableExistenceListener;
import blf.util.TypeUtils;

import java.util.*;
import java.util.function.BiFunction;
import java.util.logging.Logger;

/**
 * The abstract BaseBlockchainListener class implements blockchain unspecific callback functions, which are triggered
 * when a parse tree walker enters or exits corresponding parse tree nodes. These callback functions handle how the
 * program should process the input of the manifest file.
 *
 * It extends the BcqlBaseListener class, which is an empty listener template generated by ANTLR4 for the grammar.
 */

public abstract class BaseBlockchainListener extends BcqlBaseListener {
    private static final Logger LOGGER = Logger.getLogger(BaseBlockchainListener.class.getName());

    protected Program program;
    protected ProgramState state;
    protected BuildException error;
    protected final VariableExistenceListener variableAnalyzer;
    protected final SpecificationComposer composer = new SpecificationComposer();
    protected final Deque<Object> genericFilterPredicates = new ArrayDeque<>();

    public ProgramState getState() {
        return this.state;
    }

    public Program getProgram() {
        return this.program;
    }

    public BuildException getError() {
        return this.error;
    }

    public boolean containsError() {
        return this.error != null;
    }

    protected BaseBlockchainListener(VariableExistenceListener analyzer) {
        this.variableAnalyzer = analyzer;
    }

    @Override
    public void enterBlockchain(BcqlParser.BlockchainContext ctx) {
        LOGGER.info("Prepare program build");
        this.error = null;
        try {
            this.composer.prepareProgramBuild();
        } catch (BuildException e) {
            LOGGER.severe(String.format("Preparation of program build failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    @Override
    public void enterOutputFolder(BcqlParser.OutputFolderContext ctx) {
        final BcqlParser.LiteralContext literal = ctx.literal();
        final String literalText = literal.getText();

        if (literal.STRING_LITERAL() == null) {
            LOGGER.severe("SET OUTPUT FOLDER parameter should be a String");
            System.exit(1);
        }

        this.state.outputFolderPath = TypeUtils.parseStringLiteral(literalText);

        this.composer.addInstruction(new SetOutputFolderInstruction());
    }

    @Override
    public void exitDocument(BcqlParser.DocumentContext ctx) {
        LOGGER.info("Build program");
        try {
            this.program = this.composer.buildProgram();
        } catch (BuildException e) {
            LOGGER.severe(String.format("Building program failed: %s", e.getMessage()));
            System.exit(1);
        } finally {
            this.genericFilterPredicates.clear();
        }
    }

    @Override
    public void exitEmitStatementLog(BcqlParser.EmitStatementLogContext ctx) {
        this.handleEmitStatementLog(ctx);
    }

    private void handleEmitStatementLog(BcqlParser.EmitStatementLogContext ctx) {
        final List<ValueAccessorSpecification> accessors = new LinkedList<>();
        try {
            for (BcqlParser.ValueExpressionContext valEx : ctx.valueExpression()) {
                accessors.add(this.getValueAccessor(valEx));
            }
            final LogLineExportSpecification spec = LogLineExportSpecification.ofValues(accessors);
            this.composer.addInstruction(spec);
        } catch (BuildException e) {
            LOGGER.severe(String.format("Handling of emit statement for Log files failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    @Override
    public void exitEmitStatementCsv(BcqlParser.EmitStatementCsvContext ctx) {
        this.handleEmitStatementCsv(ctx);
    }

    private void handleEmitStatementCsv(BcqlParser.EmitStatementCsvContext ctx) {
        LinkedList<CsvColumnSpecification> columns = new LinkedList<>();
        try {
            for (BcqlParser.NamedEmitVariableContext varCtx : ctx.namedEmitVariable()) {
                final String name = varCtx.valueExpression().variableName() == null
                    ? varCtx.variableName().getText()
                    : varCtx.valueExpression().variableName().getText();
                final ValueAccessorSpecification accessor = this.getValueAccessor(varCtx.valueExpression());
                columns.add(CsvColumnSpecification.of(name, accessor));
            }
            this.composer.addInstruction(CsvExportSpecification.of(this.getValueAccessor(ctx.tableName), columns));
        } catch (BuildException e) {
            LOGGER.severe(String.format("Handling of emit statement for CSV files failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    @Override
    public void exitMethodStatement(BcqlParser.MethodStatementContext ctx) {
        this.addMethodCall(ctx.methodInvocation(), null);
    }

    @Override
    public void exitVariableAssignmentStatement(BcqlParser.VariableAssignmentStatementContext ctx) {
        this.addVariableAssignment(ctx.variableName(), ctx.statementExpression());
    }

    @Override
    public void exitVariableDeclarationStatement(BcqlParser.VariableDeclarationStatementContext ctx) {
        this.addVariableAssignment(ctx.variableName(), ctx.statementExpression());
    }

    private void addVariableAssignment(BcqlParser.VariableNameContext varCtx, BcqlParser.StatementExpressionContext stmtCtx) {
        try {
            String varName = varCtx.getText();
            final ValueMutatorSpecification mutator = ValueMutatorSpecification.ofVariableName(varName);
            if (stmtCtx.valueExpression() != null) {
                this.addValueAssignment(mutator, stmtCtx.valueExpression());
            } else if (stmtCtx.methodInvocation() != null) {
                this.addMethodCall(stmtCtx.methodInvocation(), mutator);
            } else {
                throw new UnsupportedOperationException("This type of value definition is not supported.");
            }
        } catch (UnsupportedOperationException e) {
            LOGGER.severe(String.format("Adding variable assignment failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    private void addValueAssignment(ValueMutatorSpecification mutator, BcqlParser.ValueExpressionContext ctx) {
        try {
            final ValueAccessorSpecification accessor = this.getValueAccessor(ctx);
            final ValueAssignmentSpecification assignment = ValueAssignmentSpecification.of(mutator, accessor);
            this.composer.addInstruction(assignment);
        } catch (BuildException e) {
            LOGGER.severe(String.format("Adding value assignment failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    private void addMethodCall(BcqlParser.MethodInvocationContext ctx, ValueMutatorSpecification mutator) {
        final List<String> parameterTypes = new ArrayList<>();
        final List<ValueAccessorSpecification> accessors = new ArrayList<>();
        try {
            for (BcqlParser.ValueExpressionContext valCtx : ctx.valueExpression()) {
                parameterTypes.add(InterpreterUtils.determineType(valCtx, this.variableAnalyzer));
                accessors.add(this.getValueAccessor(valCtx));
            }

            final MethodSpecification method = MethodSpecification.of(ctx.methodName.getText(), parameterTypes);
            final MethodCallSpecification call = MethodCallSpecification.of(method, mutator, accessors);
            this.composer.addInstruction(call);
        } catch (BuildException e) {
            LOGGER.severe(String.format("Adding method call failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    @Override
    public void exitEmitStatementXesTrace(BcqlParser.EmitStatementXesTraceContext ctx) {
        this.handleEmitStatementXesTrace(ctx);
    }

    private void handleEmitStatementXesTrace(BcqlParser.EmitStatementXesTraceContext ctx) {
        try {
            final ValueAccessorSpecification pid = this.getXesId(ctx.pid);
            final ValueAccessorSpecification piid = this.getXesId(ctx.piid);
            final List<XesParameterSpecification> parameters = this.getXesParameters(ctx.xesEmitVariable());
            this.composer.addInstruction(XesExportSpecification.ofTraceExport(pid, piid, parameters));
        } catch (BuildException e) {
            LOGGER.severe(String.format("Handling of emit statement for XES trace files failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    @Override
    public void exitEmitStatementXesEvent(BcqlParser.EmitStatementXesEventContext ctx) {
        this.handleEmitStatementXesEvent(ctx);
    }

    private void handleEmitStatementXesEvent(BcqlParser.EmitStatementXesEventContext ctx) {
        try {
            final ValueAccessorSpecification pid = this.getXesId(ctx.pid);
            final ValueAccessorSpecification piid = this.getXesId(ctx.piid);
            final ValueAccessorSpecification eid = this.getXesId(ctx.eid);
            final List<XesParameterSpecification> parameters = this.getXesParameters(ctx.xesEmitVariable());
            this.composer.addInstruction(XesExportSpecification.ofEventExport(pid, piid, eid, parameters));
        } catch (BuildException e) {
            LOGGER.severe(String.format("Handling of emit statement for XES event files failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    private ValueAccessorSpecification getXesId(BcqlParser.ValueExpressionContext ctx) {
        return ctx == null ? null : this.getValueAccessor(ctx);
    }

    private List<XesParameterSpecification> getXesParameters(List<BcqlParser.XesEmitVariableContext> variables) {
        final LinkedList<XesParameterSpecification> parameters = new LinkedList<>();
        try {
            for (BcqlParser.XesEmitVariableContext varCtx : variables) {
                final String name = varCtx.variableName() == null
                    ? varCtx.valueExpression().variableName().getText()
                    : varCtx.variableName().getText();
                final ValueAccessorSpecification accessor = this.getValueAccessor(varCtx.valueExpression());
                LOGGER.info(varCtx.getText());

                XesParameterSpecification parameter;
                switch (varCtx.xesTypes().getText()) {
                    case "xs:string":
                        parameter = XesParameterSpecification.ofStringParameter(name, accessor);
                        break;
                    case "xs:date":
                        parameter = XesParameterSpecification.ofDateParameter(name, accessor);
                        break;
                    case "xs:int":
                        parameter = XesParameterSpecification.ofIntegerParameter(name, accessor);
                        break;
                    case "xs:float":
                        parameter = XesParameterSpecification.ofFloatParameter(name, accessor);
                        break;
                    case "xs:boolean":
                        parameter = XesParameterSpecification.ofBooleanParameter(name, accessor);
                        break;
                    default:
                        throw new BuildException(String.format("Xes type '%s' not supported", varCtx.xesTypes().getText()));
                }
                parameters.add(parameter);
            }

            return parameters;
        } catch (BuildException e) {
            LOGGER.severe(String.format("Getter of XES parameters failed: %s", e.getMessage()));
            System.exit(1);
            return null;
        }
    }

    // Utils

    public ValueAccessorSpecification getValueAccessor(BcqlParser.ValueExpressionContext ctx) {
        try {
            if (ctx.variableName() != null) {
                return ValueAccessorSpecification.ofVariable(ctx.getText(), this.state.getBlockchainVariables());
            } else if (ctx.literal() != null) {
                return this.getLiteral(ctx.literal());
            } else {
                throw new UnsupportedOperationException("This value accessor specification is not supported.");
            }
        } catch (UnsupportedOperationException e) {
            LOGGER.severe(String.format("Getter of value accessor failed: %s", e.getMessage()));
            System.exit(1);
            return null;
        }
    }

    public ValueAccessorSpecification getLiteral(BcqlParser.LiteralContext ctx) {
        String type = this.determineLiteralType(ctx);
        return this.getLiteral(type, ctx.getText());
    }

    private String determineLiteralType(BcqlParser.LiteralContext ctx) {
        try {
            String type = null;
            if (ctx.BOOLEAN_LITERAL() != null) {
                type = TypeUtils.BOOL_TYPE_KEYWORD;
            } else if (ctx.BYTES_LITERAL() != null) {
                type = TypeUtils.BYTES_TYPE_KEYWORD;
            } else if (ctx.INT_LITERAL() != null) {
                type = TypeUtils.INT_TYPE_KEYWORD;
            } else if (ctx.STRING_LITERAL() != null) {
                type = TypeUtils.STRING_TYPE_KEYWORD;
            } else if (ctx.arrayLiteral() != null) {
                if (ctx.arrayLiteral().booleanArrayLiteral() != null) {
                    type = TypeUtils.toArrayType(TypeUtils.BOOL_TYPE_KEYWORD);
                } else if (ctx.arrayLiteral().bytesArrayLiteral() != null) {
                    type = TypeUtils.toArrayType(TypeUtils.BYTES_TYPE_KEYWORD);
                } else if (ctx.arrayLiteral().intArrayLiteral() != null) {
                    type = TypeUtils.toArrayType(TypeUtils.INT_TYPE_KEYWORD);
                } else if (ctx.arrayLiteral().stringArrayLiteral() != null) {
                    type = TypeUtils.toArrayType(TypeUtils.STRING_TYPE_KEYWORD);
                }
            }

            if (type == null) {
                throw new BuildException(String.format("Cannot determine type for literal %s.", ctx.getText()));
            }
            return type;
        } catch (BuildException e) {
            LOGGER.severe(String.format("Determination of literal type failed: %s", e.getMessage()));
            System.exit(1);
            return null;
        }

    }

    public ValueAccessorSpecification getLiteral(String type, String literal) {
        try {
            if (TypeUtils.isArrayType(type)) {
                if (TypeUtils.isArrayType(type, TypeUtils.ADDRESS_TYPE_KEYWORD)) {
                    return ValueAccessorSpecification.addressArrayLiteral(literal);
                } else if (TypeUtils.isArrayType(type, TypeUtils.BOOL_TYPE_KEYWORD)) {
                    return ValueAccessorSpecification.booleanArrayLiteral(literal);
                } else if (TypeUtils.isArrayType(type, TypeUtils.BYTES_TYPE_KEYWORD)) {
                    return ValueAccessorSpecification.bytesArrayLiteral(literal);
                } else if (TypeUtils.isArrayType(type, TypeUtils.INT_TYPE_KEYWORD)) {
                    return ValueAccessorSpecification.integerArrayLiteral(literal);
                } else if (TypeUtils.isArrayType(type, TypeUtils.STRING_TYPE_KEYWORD)) {
                    return ValueAccessorSpecification.stringArrayLiteral(literal);
                } else {
                    throw new BuildException(String.format("Unsupported type: '%s'.", type));
                }
            } else {
                if (TypeUtils.isAddressType(type)) {
                    return ValueAccessorSpecification.addressLiteral(literal);
                } else if (TypeUtils.isBooleanType(type)) {
                    return ValueAccessorSpecification.booleanLiteral(literal);
                } else if (TypeUtils.isBytesType(type)) {
                    return ValueAccessorSpecification.bytesLiteral(literal);
                } else if (TypeUtils.isIntegerType(type)) {
                    return ValueAccessorSpecification.integerLiteral(literal);
                } else if (TypeUtils.isStringType(type)) {
                    return ValueAccessorSpecification.stringLiteral(literal);
                } else {
                    throw new BuildException(String.format("Unsupported type: '%s'.", type));
                }
            }
        } catch (BuildException e) {
            LOGGER.severe(String.format("Getter of literal failed: %s", e.getMessage()));
            System.exit(1);
            return null;
        }
    }

    @Override
    public void exitScope(BcqlParser.ScopeContext ctx) {
        handleScopeBuild(ctx.filter());
    }

    private void handleScopeBuild(BcqlParser.FilterContext ctx) {
        if (ctx.genericFilter() != null) {
            this.buildGenericFilter();
        }
    }

    @Override
    public void exitConditionalOrExpression(BcqlParser.ConditionalOrExpressionContext ctx) {
        try {
            if (ctx.conditionalOrExpression() != null) {
                this.createBinaryConditionalExpression(GenericFilterPredicateSpecification::or);
            }
        } catch (BuildException e) {
            LOGGER.severe(String.format("Handling of conditional OR expression failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    @Override
    public void exitConditionalAndExpression(BcqlParser.ConditionalAndExpressionContext ctx) {
        try {
            if (ctx.conditionalAndExpression() != null) {
                this.createBinaryConditionalExpression(GenericFilterPredicateSpecification::and);
            }
        } catch (BuildException e) {
            LOGGER.severe(String.format("Handling of conditional AND expression failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    private void createBinaryConditionalExpression(
        BiFunction<
            GenericFilterPredicateSpecification,
            GenericFilterPredicateSpecification,
            GenericFilterPredicateSpecification> constructor
    ) throws BuildException {
        if (this.genericFilterPredicates.isEmpty()
            || !(this.genericFilterPredicates.peek() instanceof GenericFilterPredicateSpecification)) {
            throw new BuildException("Parse tree error: binary boolean expression requires boolean predicates.");
        }
        final GenericFilterPredicateSpecification predicate1 = (GenericFilterPredicateSpecification) this.genericFilterPredicates.pop();

        if (this.genericFilterPredicates.isEmpty()
            || !(this.genericFilterPredicates.peek() instanceof GenericFilterPredicateSpecification)) {
            throw new BuildException("Parse tree error: binary boolean expression requires boolean predicates.");
        }
        final GenericFilterPredicateSpecification predicate2 = (GenericFilterPredicateSpecification) this.genericFilterPredicates.pop();

        this.genericFilterPredicates.push(constructor.apply(predicate1, predicate2));
    }

    @Override
    public void exitConditionalComparisonExpression(BcqlParser.ConditionalComparisonExpressionContext ctx) {
        try {
            if (ctx.comparators() == null) {
                return;
            }

            if (this.genericFilterPredicates.size() < 2) {
                throw new BuildException("Parse tree does not contain enough expressions.");
            }

            final Object value2 = this.genericFilterPredicates.pop();
            if (!(value2 instanceof ValueAccessorSpecification)) {
                throw new BuildException("Can only compare values, but not boolean expressions.");
            }

            final Object value1 = this.genericFilterPredicates.pop();
            if (!(value1 instanceof ValueAccessorSpecification)) {
                throw new BuildException("Can only compare values, but not boolean expressions.");
            }

            final ValueAccessorSpecification spec1 = (ValueAccessorSpecification) value1;
            final ValueAccessorSpecification spec2 = (ValueAccessorSpecification) value2;

            GenericFilterPredicateSpecification predicate;
            switch (ctx.comparators().getText().toLowerCase()) {
                case "==":
                    predicate = GenericFilterPredicateSpecification.equals(spec1, spec2);
                    break;
                case "!=":
                    predicate = GenericFilterPredicateSpecification.notEquals(spec1, spec2);
                    break;
                case ">=":
                    predicate = GenericFilterPredicateSpecification.greaterThanAndEquals(spec1, spec2);
                    break;
                case ">":
                    predicate = GenericFilterPredicateSpecification.greaterThan(spec1, spec2);
                    break;
                case "<":
                    predicate = GenericFilterPredicateSpecification.smallerThan(spec1, spec2);
                    break;
                case "<=":
                    predicate = GenericFilterPredicateSpecification.smallerThanAndEquals(spec1, spec2);
                    break;
                case "in":
                    predicate = GenericFilterPredicateSpecification.in(spec1, spec2);
                    break;
                default:
                    throw new BuildException(String.format("Comparator %s not supported.", ctx.comparators().getText()));
            }
            this.genericFilterPredicates.push(predicate);
        } catch (BuildException e) {
            LOGGER.severe(String.format("Handling of conditional comparison expression failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    @Override
    public void exitConditionalNotExpression(BcqlParser.ConditionalNotExpressionContext ctx) {
        try {
            if (ctx.KEY_NOT() == null) {
                return;
            }

            Object valueExpression = this.genericFilterPredicates.pop();
            if (valueExpression instanceof ValueAccessorSpecification) {
                valueExpression = GenericFilterPredicateSpecification.ofBooleanAccessor((ValueAccessorSpecification) valueExpression);
            }

            if (!(valueExpression instanceof GenericFilterPredicateSpecification)) {
                throw new BuildException(
                    String.format("GenericFilterPredicateSpecification required, but was %s.", valueExpression.getClass())
                );
            }
            this.genericFilterPredicates.push(
                GenericFilterPredicateSpecification.not((GenericFilterPredicateSpecification) valueExpression)
            );
        } catch (BuildException e) {
            LOGGER.severe(String.format("Handling of conditional NOT expression failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    @Override
    public void exitConditionalPrimaryExpression(BcqlParser.ConditionalPrimaryExpressionContext ctx) {
        if (ctx.valueExpression() != null) {
            this.genericFilterPredicates.push(this.getValueAccessor(ctx.valueExpression()));
        }
    }

    @Override
    public void enterGenericFilter(BcqlParser.GenericFilterContext ctx) {
        LOGGER.info("Prepare generic filter build");
        try {
            this.composer.prepareGenericFilterBuild();
        } catch (BuildException e) {
            LOGGER.severe(String.format("Preparation of generic filter build failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    private void buildGenericFilter() {
        LOGGER.info("Build generic filter");
        try {
            if (this.genericFilterPredicates.size() != 1) {
                throw new BuildException("Error in boolean expression tree.");
            }

            final Object predicate = this.genericFilterPredicates.pop();
            if (predicate instanceof GenericFilterPredicateSpecification) {
                this.composer.buildGenericFilter((GenericFilterPredicateSpecification) predicate);
            } else if (predicate instanceof ValueAccessorSpecification) {
                final GenericFilterPredicateSpecification filterSpec = GenericFilterPredicateSpecification.ofBooleanAccessor(
                    (ValueAccessorSpecification) predicate
                );
                this.composer.buildGenericFilter(filterSpec);
            } else {
                final String message = String.format(
                    "Unsupported type for specification of generic filter predicates: %s",
                    predicate.getClass()
                );
                throw new BuildException(message);
            }
        } catch (BuildException e) {
            LOGGER.severe(String.format("Building generic filter failed: %s", e.getMessage()));
            System.exit(1);
        }
    }

    // #endregion Utils
}
