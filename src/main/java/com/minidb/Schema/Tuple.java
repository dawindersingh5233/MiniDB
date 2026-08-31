package com.minidb.Schema;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Tuple {
    private byte[] data;
    private final Schema schema;

    public Tuple(List<Value> values, Schema schema) {
        this.schema = schema;
        this.data = serialize(values, schema);
    }

    public Tuple(byte[] rawData, Schema schema) {
        this.data = rawData;
        this.schema = schema;
    }

    //Getters
    public byte[] getData() {
        return data;
    }

    public int getSize() {
        return data.length;
    }

    public Value getValue(int columnIndex) {
        Column col = schema.getColumn(columnIndex);
        ByteBuffer buf = ByteBuffer.wrap(data);

        if (!schema.isVariableLengthColumn(columnIndex)) {
            int offset = col.getFixedOffset();

            switch (col.getType()) {
                case INTEGER:
                    return Value.valueOf(buf.getInt(offset));
                case BOOLEAN:
                    return Value.valueOf(buf.get(offset) != 0);
            }
        } else {
            int varOffset = locateVariableColumn(columnIndex);
            int len = buf.getInt(varOffset);

            byte[] strBytes = new byte[len];
            buf.position(varOffset + 4);
            buf.get(strBytes);

            return Value.valueOf(new String(strBytes, StandardCharsets.UTF_8));
        }

        throw new IllegalStateException();
    }

    public Value getValue(String columnName) {
        return getValue(schema.getColumnIndex(columnName));
    }

    public Value getPrimaryKeyValue() {
        int pkIdx = schema.getPrimaryKeyIndex();

        if (pkIdx == -1) {
            throw new IllegalStateException("Schema has no primary key");
        }

        return getValue(pkIdx);
    }

    private int locateVariableColumn(int columnIndex) {
        // iterate columns before columnIndex that are variable-length,
        // accumulating their (4-byte length + data) sizes, starting right
        // after schema.getFixedPartLength()

        int offset = schema.getFixedPartLength();

        for (int i = 0; i < columnIndex; i++) {
            if (schema.isVariableLengthColumn(i)) {
                int len = ByteBuffer.wrap(data).getInt(offset);
                offset += 4 + len;
            }
        }

        return offset;
    }

    private static byte[] serialize(List<Value> values, Schema schema) {
        int totalLength = schema.getFixedPartLength();

        byte[][] varData = new byte[schema.getColumnCount()][];

        for (int i = 0; i < schema.getColumnCount(); i++) {
            if (schema.isVariableLengthColumn(i)) {
                byte[] arr = values.get(i).asString().getBytes(StandardCharsets.UTF_8);
                varData[i] = arr;

                totalLength += 4;
                totalLength += arr.length;
            }
        }

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        int varOffset = schema.getFixedPartLength();

        for (int i = 0; i < schema.getColumnCount(); i++) {
            if (schema.isVariableLengthColumn(i)) {
                byte[] arr = varData[i];

                buffer.putInt(varOffset, arr.length);
                varOffset += 4;

                buffer.put(varOffset, arr);
                varOffset += arr.length;
            } else {
                Column column = schema.getColumn(i);

                switch (column.getType()) {
                    case INTEGER:
                        buffer.putInt(column.getFixedOffset(), values.get(i).asInt());
                        break;
                    case BOOLEAN:
                        buffer.put(column.getFixedOffset(), (byte) (values.get(i).asBoolean() ? 1 : 0));
                        break;
                }
            }
        }

        return buffer.array();
    }
}