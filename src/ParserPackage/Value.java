package ParserPackage;

public class Value {
    private Object value;
    private Class<?> type;

    public Value(Object value) {
        setValue(value);
    }

    public Value() {}

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        if (value == null) value = NULL_VALUE;
        this.value = value;
        this.type = value.getClass();
    }

    public Class<?> getType() {
        return type;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    private static class Null {
        @Override
        public String toString() {
            return "null";
        }
    }

    private static Null NULL_VALUE = new Null();

    public static Value NULL = new Value(NULL_VALUE);
}
