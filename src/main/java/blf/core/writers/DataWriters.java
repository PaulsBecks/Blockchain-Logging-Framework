package blf.core.writers;

import blf.configuration.EmissionSettings;

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
    private final HttpWriter httpWriter;

    public DataWriters() {
        this.csvWriter = new CsvWriter();
        this.logWriter = new LogWriter();
        this.xesWriter = new XesWriter();
        this.httpWriter = new HttpWriter();

        this.writers = new DataWriter[] { this.csvWriter, this.logWriter, this.xesWriter, this.httpWriter };
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

    public HttpWriter getHttpWriter() {
        return this.httpWriter;
    }

    public void setOutputFolder(Path folderPath) {
        Arrays.stream(this.writers).forEach(e -> e.setOutputFolder(folderPath));
    }

    public void setEmissionMode(EmissionSettings.EmissionMode emissionMode) {
        for (DataWriter ex : this.writers) {
            ex.setEmissionMode(emissionMode);
        }
    }

    public void startNewBlock(BigInteger blockNumber) {
        Arrays.stream(this.writers).forEach(e -> e.startBlock(blockNumber));
    }

    public void writeBlock() {
        for (DataWriter ex : this.writers) {
            ex.endBlock();
        }
    }

    public void writeAllData() {
        for (DataWriter ex : this.writers) {
            ex.endProgram();
        }
    }
}
