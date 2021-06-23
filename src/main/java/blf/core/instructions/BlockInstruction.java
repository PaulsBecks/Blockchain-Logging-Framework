package blf.core.instructions;

import blf.core.exceptions.ExceptionHandler;
import blf.core.state.ProgramState;
import blf.core.writers.DataWriters;

import java.math.BigInteger;
import java.util.List;
import java.util.logging.Logger;

public class BlockInstruction extends Instruction {
    private static final Logger LOGGER = Logger.getLogger(BlockInstruction.class.getName());

    protected BlockInstruction(final List<Instruction> nestedInstructions) {
        super(nestedInstructions);
    }

    @Override
    public void executeNestedInstructions(final ProgramState programState) {
        BigInteger currentBlockNumber = (BigInteger) programState.getBlockchainVariables()
            .currentBlockNumberAccessor()
            .getValue(programState);

        if (currentBlockNumber == null) {
            ExceptionHandler.getInstance().handleException("Current block number is null.", new NullPointerException());

            return;
        }

        LOGGER.info("Execute nested instructions for block number: " + currentBlockNumber.toString());

        final DataWriters dataWriters = programState.getWriters();

        dataWriters.startNewBlock(currentBlockNumber);

        super.executeNestedInstructions(programState);

        dataWriters.writeBlock();
    }
}
