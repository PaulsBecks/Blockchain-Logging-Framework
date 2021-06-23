package blf.core.instructions;

import blf.core.state.ProgramState;
import blf.core.values.ValueAccessor;
import blf.core.writers.CsvColumn;
import blf.core.writers.CsvWriter;
import blf.core.writers.HttpWriter;
import io.reactivex.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * CsvRowCreator
 */
public class AddHttpRequestInstruction extends Instruction {
    private final List<CsvColumn> variables;
    private final ValueAccessor uri;

    public AddHttpRequestInstruction(@NonNull ValueAccessor uri, @NonNull List<CsvColumn> variables) {
        this.variables = new ArrayList<>(variables);
        this.uri = uri;
    }

    @Override
    public void execute(ProgramState state) {
        final HttpWriter writer = state.getWriters().getHttpWriter();
        final String uri = (String) this.uri.getValue(state);
        String jsonString = "{";

        for (int i = 0; i < this.variables.size(); i++) {
            CsvColumn variable = this.variables.get(i);
            final Object value = variable.getAccessor().getValue(state);
            final String name = variable.getName();
            jsonString += "\"" + name + "\":" + "\"" + value + "\"";
            if (i < this.variables.size() - 1) {
                jsonString += ",";
            }
        }

        jsonString += "}";

        writer.addHttpRequest(uri, jsonString);
    }
}
