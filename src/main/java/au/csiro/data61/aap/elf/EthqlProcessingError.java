package au.csiro.data61.aap.elf;

import org.antlr.v4.runtime.Token;

/**
 * SpecificationParserErrors
 */
public class EthqlProcessingError {
    private final int line;
    private final int column;
    private final String errorMessage;
    private final Throwable errorCause;
    
    public EthqlProcessingError(Token token, String errorMessage) {
        this(token, errorMessage, null);
    }    

    public EthqlProcessingError(Token token, String errorMessage, Throwable cause) {
        assert token != null;
        assert errorMessage != null && !errorMessage.trim().isEmpty();
        this.line = token.getLine();
        this.column = token.getStartIndex();
        this.errorMessage = errorMessage;
        this.errorCause = cause;
    }

    public EthqlProcessingError(int line, int column, String errorMessage) {
        this(line, column, errorMessage, null);
    }

    public EthqlProcessingError(int line, int column, String errorMessage, Throwable errorCause) {
        assert 0 <= line;
        assert 0 <= column;
        assert errorMessage != null && !errorMessage.trim().isEmpty();
        this.line = line;
        this.column = column;
        this.errorMessage = errorMessage;
        this.errorCause = errorCause;
    }

    public int getLine() {
        return this.line;
    }

    public int getColumn() {
        return this.column;
    }

    public Throwable getErrorCause() {
        return this.errorCause;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    @Override
    public String toString() {
        return String.format("Ln %s, Col %s: %s", this.line, this.column, this.errorMessage);
    }
}