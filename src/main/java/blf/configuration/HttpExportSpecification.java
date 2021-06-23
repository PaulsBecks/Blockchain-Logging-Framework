package blf.configuration;

import java.util.List;
import java.util.stream.Collectors;

import blf.core.instructions.AddCsvRowInstruction;
import blf.core.instructions.AddHttpRequestInstruction;
import io.reactivex.annotations.NonNull;

/**
 * CsvExportSpecification
 */
public class HttpExportSpecification extends InstructionSpecification<AddHttpRequestInstruction> {

    private HttpExportSpecification(AddHttpRequestInstruction instruction) {
        super(instruction);
    }

    public static HttpExportSpecification of(@NonNull ValueAccessorSpecification uri, @NonNull List<CsvColumnSpecification> variables) {

        return new HttpExportSpecification(
            new AddHttpRequestInstruction(
                uri.getValueAccessor(),
                variables.stream().map(CsvColumnSpecification::getColumn).collect(Collectors.toList())
            )
        );
    }
}
