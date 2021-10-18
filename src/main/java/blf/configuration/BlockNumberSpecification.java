package blf.configuration;

import blf.blockchains.ethereum.state.EthereumProgramState;
import blf.core.interfaces.FilterPredicate;
import blf.core.values.BlockchainVariables;
import blf.core.values.ValueAccessor;
import io.reactivex.annotations.NonNull;

import java.math.BigInteger;

/**
 * BlockNumberSpecification
 */
public class BlockNumberSpecification {
    private final ValueAccessor accessor;
    private final FilterPredicate<BigInteger> stopCriterion;
    private final Type type;

    private BlockNumberSpecification(ValueAccessor accessor, FilterPredicate<BigInteger> stopCriterion, Type type) {
        this.accessor = accessor;
        this.stopCriterion = stopCriterion;
        this.type = type;
    }

    public ValueAccessor getValueAccessor() {
        return this.accessor;
    }

    public FilterPredicate<BigInteger> getStopCriterion() {
        return this.stopCriterion;
    }

    Type getType() {
        return this.type;
    }

    public static BlockNumberSpecification ofBlockNumber(@NonNull ValueAccessorSpecification number) {
        final ValueAccessor accessor = number.getValueAccessor();
        return new BlockNumberSpecification(accessor, createStopCriterion(accessor), Type.NUMBER);
    }

    public static BlockNumberSpecification ofCurrent(EthereumProgramState state) {
        final ValueAccessor accessor = s -> ((EthereumProgramState)s).getReader().getClient().queryBlockNumber();
        return new BlockNumberSpecification(accessor, createStopCriterion(accessor), Type.CURRENT);
    }

    private static FilterPredicate<BigInteger> createStopCriterion(ValueAccessor accessor) {
        final Value endValue = new Value();
        return (state, blockNumber) -> {
            if (endValue.blockNumber == null) {
                endValue.blockNumber = (BigInteger) accessor.getValue(state);
            }

            if (endValue.blockNumber == null) {
                return true;
            }

            return endValue.blockNumber.compareTo(blockNumber) < 0;
        };
    }

    private static class Value {
        private BigInteger blockNumber;
    }

    public static BlockNumberSpecification ofEarliest() {
        final ValueAccessor accessor = ValueAccessorSpecification.integerLiteral(BigInteger.ZERO).getValueAccessor();
        return new BlockNumberSpecification(accessor, null, Type.EARLIEST);
    }

    public static BlockNumberSpecification ofContinuous() {
        return new BlockNumberSpecification(null, (state, blockNumber) -> false, Type.CONTINUOUS);
    }

    enum Type {
        CONTINUOUS,
        CURRENT,
        EARLIEST,
        NUMBER,
        VARIABLE
    }

}
