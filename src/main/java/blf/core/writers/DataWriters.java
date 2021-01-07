package blf.core.writers;

import io.reactivex.annotations.NonNull;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Sinks
 */
public class DataWriters {
    private final DataWriter[] writers;
    private final CsvWriter csvWriter;
    private final LogWriter logWriter;
    private final XesWriter xesWriter;

    public DataWriters() {
        this.csvWriter = new CsvWriter();
        this.logWriter = new LogWriter();
        this.xesWriter = new XesWriter();

        this.writers = new DataWriter[] { this.csvWriter, this.logWriter, this.xesWriter };
    }

    public XesWriter getXesWriter() {
        return this.xesWriter;
    }

    public LogWriter getLogWriter() {
        return this.logWriter;
    }

    public CsvWriter getCsvWriter() {
        return this.csvWriter;
    }

    public void setOutputFolder(@NonNull Path folderPath) {
        Arrays.stream(this.writers).forEach(e -> e.setOutputFolder(folderPath));
    }

    public void startNewBlock(BigInteger blockNumber) {
        Arrays.stream(this.writers).forEach(e -> e.startBlock(blockNumber));
    }

    public void writeBlock() throws Throwable {
        for (DataWriter ex : this.writers) {
            ex.endBlock();
        }
    }

    public void writeAllData() throws Throwable {
        for (DataWriter ex : this.writers) {
            ex.endProgram();
        }
    }
}
