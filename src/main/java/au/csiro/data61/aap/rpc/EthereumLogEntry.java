package au.csiro.data61.aap.rpc;

import java.math.BigInteger;
import java.util.stream.Stream;

/**
 * EthereumLog
 */
public abstract class EthereumLogEntry {
    private final EthereumTransaction tx;
    
    protected EthereumLogEntry(EthereumTransaction tx) {
        this.tx = tx;
    }

    public EthereumTransaction getTransaction() {
        return this.tx;
    }

    public String getTransactionHash() {
        return this.tx.getHash();
    }

    public BigInteger getTransactionIndex() {
        return this.tx.getTransactionIndex();
    }

    public String getBlockHash() {
        return this.tx.getBlockHash();
    }

    public BigInteger getBlockNumber() {
        return this.tx.getBlockNumber();
    }

    public abstract String getAddress();
    public abstract String getData();
    public abstract BigInteger getLogIndex();
    public abstract boolean isRemoved();
    public abstract int topicCount() ;
    public abstract String getTopic(int index);
    public abstract Stream<String> topicStream();
    
}