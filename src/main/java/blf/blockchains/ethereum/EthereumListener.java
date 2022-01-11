package blf.blockchains.ethereum;

import blf.blockchains.ethereum.instructions.EthereumBlockFilterInstruction;
import blf.blockchains.ethereum.instructions.EthereumConnectInstruction;
import blf.blockchains.ethereum.instructions.EthereumConnectIpcInstruction;
import blf.blockchains.ethereum.state.EthereumProgramState;
import blf.configuration.*;
import blf.core.exceptions.ExceptionHandler;
import blf.grammar.BcqlParser;
import blf.parsing.VariableExistenceListener;
import blf.util.TypeUtils;
import org.antlr.v4.runtime.tree.ParseTree;
import java.math.BigInteger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EthereumListener extends BaseBlockchainListener {
    private static final Logger LOGGER = Logger.getLogger(EthereumListener.class.getName());

    public EthereumListener(VariableExistenceListener analyzer) {
        super(analyzer);

        this.state = new EthereumProgramState();

        analyzer.setBlockchainVariables(this.state.getBlockchainVariables());
    }

    @Override
    public void exitConnection(BcqlParser.ConnectionContext ctx) {
        final EthereumProgramState ethereumProgramState = (EthereumProgramState) state;
        final BcqlParser.LiteralContext literal = ctx.literal();
        final String literalText = ctx.literal().getText();

        if (literal.STRING_LITERAL() == null) {
            ExceptionHandler.getInstance()
                .handleException("Ethereum SET CONNECTION parameter should be a String.", new NullPointerException());

            return;
        }

        if (this.composer.instructionListsStack.isEmpty()) {
            ExceptionHandler.getInstance().handleException("The Stack of instructions lists is empty.", new Exception());

            return;
        }

        final String connectionInputParameter = TypeUtils.parseStringLiteral(literalText);

        if (ctx.KEY_IPC() != null) {
            ethereumProgramState.setConnectionIpcPath(connectionInputParameter);
            this.composer.instructionListsStack.peek().add(new EthereumConnectIpcInstruction());
        } else {
            ethereumProgramState.setConnectionUrl(connectionInputParameter);
            this.composer.instructionListsStack.peek().add(new EthereumConnectInstruction());
        }
    }

    @Override
    public void enterBlockFilter(BcqlParser.BlockFilterContext ctx) {
        LOGGER.info("Prepare block filter build");
        this.composer.prepareBlockRangeBuild();
    }

    private void buildBlockFilter(BcqlParser.BlockFilterContext ctx) {
        LOGGER.info("Build block filter");

        BlockNumberSpecification from = this.getBlockNumberSpecification(ctx.from);
        BlockNumberSpecification to = this.getBlockNumberSpecification(ctx.to);

        if (this.composer.states.peek() != SpecificationComposer.FactoryState.BLOCK_RANGE_FILTER) {
            ExceptionHandler.getInstance()
                .handleException("Cannot build a block filter, when construction of %s has not been finished.", new Exception());

            return;
        }

        if (from == null) {
            ExceptionHandler.getInstance().handleException("The FROM BlockNumberSpecification is null.", new NullPointerException());

            return;
        }

        if (to == null) {
            ExceptionHandler.getInstance().handleException("The TO BlockNumberSpecification is null.", new NullPointerException());

            return;
        }

        if (this.composer.instructionListsStack.isEmpty()) {
            ExceptionHandler.getInstance().handleException("The Stack of instructions lists is empty.", new Exception());

            return;
        }

        final EthereumBlockFilterInstruction blockRange = new EthereumBlockFilterInstruction(
            from.getValueAccessor(),
            to.getStopCriterion(),
            this.composer.instructionListsStack.peek()
        );

        this.composer.instructionListsStack.pop();
        this.composer.instructionListsStack.peek().add(blockRange);

        this.composer.states.pop();
    }

    private BlockNumberSpecification getBlockNumberSpecification(BcqlParser.BlockNumberContext ctx) {

        if (ctx.valueExpression() != null) {
            ValueAccessorSpecification number = this.getValueAccessor(ctx.valueExpression());

            return BlockNumberSpecification.ofBlockNumber(number);
        }

        if (ctx.KEY_CURRENT() != null) {
            LOGGER.info("Key current is not null");
            return BlockNumberSpecification.ofCurrent((EthereumProgramState) this.state);
        }

        if (ctx.KEY_EARLIEST() != null) {
            return BlockNumberSpecification.ofEarliest();
        }

        if (ctx.KEY_CONTINUOUS() != null) {
            return BlockNumberSpecification.ofContinuous();
        }

        ExceptionHandler.getInstance()
            .handleException("Block number specification failed: unsupported variable declaration.", new Exception());

        return null;
    }

    @Override
    public void enterTransactionFilter(BcqlParser.TransactionFilterContext ctx) {
        LOGGER.info("Prepare transaction filter build");
        this.composer.prepareTransactionFilterBuild();
    }

    private void buildTransactionFilter(BcqlParser.TransactionFilterContext ctx) {
        LOGGER.info("Build Transaction Filter");
        final AddressListSpecification senders = this.getAddressListSpecification(ctx.senders);
        final AddressListSpecification recipients = this.getAddressListSpecification(ctx.recipients);
        this.composer.buildTransactionFilter(senders, recipients);
    }

    @Override
    public void enterLogEntryFilter(BcqlParser.LogEntryFilterContext ctx) {
        LOGGER.info("Prepare log entry filter build");
        LOGGER.info(ctx.addressList().getText());
        String str = ctx.addressList().getText();
        this.state.getValueStore().setValue("log.address",str.substring(1, str.length() - 1));
        this.composer.prepareLogEntryFilterBuild();
    }

    private void buildLogEntryFilter(BcqlParser.LogEntryFilterContext ctx) {
        LOGGER.info("Build log entry filter");
        String str = ctx.addressList().getText();
        LOGGER.info(str.substring(1, str.length() - 1));
        final AddressListSpecification contracts = this.getAddressListSpecification(ctx.addressList());
        final LogEntrySignatureSpecification signature = this.getLogEntrySignature(ctx.logEntrySignature());
        this.composer.buildLogEntryFilter(contracts, signature);
    }

    private AddressListSpecification getAddressListSpecification(BcqlParser.AddressListContext ctx) {

        if (ctx.BYTES_LITERAL() != null) {
            return AddressListSpecification.ofAddresses(ctx.BYTES_LITERAL().stream().map(ParseTree::getText).collect(Collectors.toList()));
        }

        if (ctx.KEY_ANY() != null) {
            return AddressListSpecification.ofAny();
        }

        if (ctx.variableName() != null) {
            return AddressListSpecification.ofVariableName(ctx.variableName().getText());
        }

        return AddressListSpecification.ofEmpty();
    }

    private LogEntrySignatureSpecification getLogEntrySignature(BcqlParser.LogEntrySignatureContext ctx) {
        final LinkedList<ParameterSpecification> parameters = new LinkedList<>();
        for (BcqlParser.LogEntryParameterContext paramCtx : ctx.logEntryParameter()) {
            parameters.add(
                ParameterSpecification.of(paramCtx.variableName().getText(), paramCtx.solType().getText(), paramCtx.KEY_INDEXED() != null)
            );
        }
        return LogEntrySignatureSpecification.of(ctx.methodName.getText(), parameters);
    }

    @Override
    public void exitScope(BcqlParser.ScopeContext ctx) {
        super.exitScope(ctx);

        final String filterTypeNotSupportedMsg = String.format("Filter type '%s' not supported.", ctx.getText());

        final BcqlParser.FilterContext filterCtx = ctx.filter();

        final BcqlParser.LogEntryFilterContext logEntryFilterCtx = filterCtx.logEntryFilter();
        final BcqlParser.BlockFilterContext blockFilterCtx = filterCtx.blockFilter();
        final BcqlParser.TransactionFilterContext transactionFilterCtx = filterCtx.transactionFilter();
        final BcqlParser.SmartContractFilterContext smartContractFilterCtx = filterCtx.smartContractFilter();

        // already handled by exitScope method in super
        if (filterCtx.genericFilter() != null) {
            return;
        }

        if (logEntryFilterCtx != null) {
            this.buildLogEntryFilter(logEntryFilterCtx);

            return;
        }

        if (blockFilterCtx != null) {
            this.buildBlockFilter(blockFilterCtx);

            return;
        }

        if (transactionFilterCtx != null) {
            this.buildTransactionFilter(transactionFilterCtx);

            return;
        }

        if (smartContractFilterCtx != null) {
            this.buildSmartContractFilter(smartContractFilterCtx);

            return;
        }

        ExceptionHandler.getInstance().handleException(filterTypeNotSupportedMsg, new Exception());
    }

    @Override
    public void enterSmartContractFilter(BcqlParser.SmartContractFilterContext ctx) {
        this.composer.prepareSmartContractFilterBuild();
    }

    private void buildSmartContractFilter(BcqlParser.SmartContractFilterContext ctx) {
        final ValueAccessorSpecification contractAddress = this.getValueAccessor(ctx.valueExpression());

        final List<SmartContractQuerySpecification> queries = new ArrayList<>();
        for (BcqlParser.SmartContractQueryContext scQuery : ctx.smartContractQuery()) {
            if (scQuery.publicFunctionQuery() != null) {
                queries.add(this.handlePublicFunctionQuery(scQuery.publicFunctionQuery()));
            } else if (scQuery.publicVariableQuery() != null) {
                queries.add(this.handlePublicVariableQuery(scQuery.publicVariableQuery()));
            } else {
                throw new UnsupportedOperationException();
            }
        }

        this.composer.buildSmartContractFilter(SmartContractFilterSpecification.of(contractAddress, queries));
    }

    private SmartContractQuerySpecification handlePublicFunctionQuery(BcqlParser.PublicFunctionQueryContext ctx) {
        final List<ParameterSpecification> outputParams = ctx.smartContractParameter()
            .stream()
            .map(this::createParameterSpecification)
            .collect(Collectors.toList());

        final List<TypedValueAccessorSpecification> inputParameters = new ArrayList<>();
        for (BcqlParser.SmartContractQueryParameterContext paramCtx : ctx.smartContractQueryParameter()) {
            inputParameters.add(this.createTypedValueAccessor(paramCtx));
        }

        return SmartContractQuerySpecification.ofMemberFunction(ctx.methodName.getText(), inputParameters, outputParams);
    }

    private TypedValueAccessorSpecification createTypedValueAccessor(BcqlParser.SmartContractQueryParameterContext ctx) {

        if (ctx.variableName() != null) {
            final String varName = ctx.variableName().getText();

            return TypedValueAccessorSpecification.of(
                this.variableAnalyzer.getVariableType(varName),
                ValueAccessorSpecification.ofVariable(varName, this.state.getBlockchainVariables())
            );
        }

        if (ctx.solType() != null) {

            return TypedValueAccessorSpecification.of(ctx.solType().getText(), this.getLiteral(ctx.literal()));
        }

        ExceptionHandler.getInstance()
            .handleException(
                "Creation of typed value accessor failed: Unsupported way of defining typed value accessors.",
                new Exception()
            );

        return null;
    }

    private SmartContractQuerySpecification handlePublicVariableQuery(BcqlParser.PublicVariableQueryContext ctx) {
        return SmartContractQuerySpecification.ofMemberVariable(createParameterSpecification(ctx.smartContractParameter()));
    }

    private ParameterSpecification createParameterSpecification(BcqlParser.SmartContractParameterContext ctx) {
        return ParameterSpecification.of(ctx.variableName().getText(), ctx.solType().getText());
    }

}
