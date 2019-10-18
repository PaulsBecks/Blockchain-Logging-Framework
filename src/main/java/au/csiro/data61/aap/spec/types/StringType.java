package au.csiro.data61.aap.spec.types;

/**
 * StringType
 */
public class StringType extends SolidityType {
    private static final String NAME = "string";

    public StringType() {
        super(AddressType.class, BoolType.class, BytesType.class, FixedType.class, IntegerType.class, StringType.class);
    }

    @Override
    public String getName() {
        return NAME;
    }
}