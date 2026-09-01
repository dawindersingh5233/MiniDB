package com.minidb.schema;

public class Value {
    private final TypeId type;
    private final Object data;

    private Value(TypeId type, Object data) {
        this.type = type;
        this.data = data;
    }

    public static Value valueOf(int v) {
        return new Value(TypeId.INTEGER, v);
    }

    public static Value valueOf(String v) {
        return new Value(TypeId.VARCHAR, v);
    }

    public static Value valueOf(boolean v) {
        return new Value(TypeId.BOOLEAN, v);
    }

    public TypeId getType() {
        return type;
    }

    public int asInt() {
        return (Integer) data;
    }

    public String asString() {
        return (String) data;
    }

    public boolean asBoolean() {
        return (Boolean) data;
    }

    public int compareTo(Value other) {
        switch (type) {
            case INTEGER:
                return Integer.compare(asInt(), other.asInt());

            case VARCHAR:
                return asString().compareTo(other.asString());

            case BOOLEAN:
                return Boolean.compare(asBoolean(), other.asBoolean());

            default:
                throw new UnsupportedOperationException();
        }
    }
}