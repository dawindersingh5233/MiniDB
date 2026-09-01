package com.minidb.schema;

import java.util.*;

public class Schema {
    private final List<Column> columns;
    private final Map<String, Integer> columnNameToIndex;
    private final boolean[] isVariableLength;

    private final int primaryKeyIndex;
    private final int fixedPartLength;

    public Schema(List<Column> columns, String primaryKeyColumnName) {
        this.columns = new ArrayList<>(columns);
        this.columnNameToIndex = new HashMap<>();
        this.isVariableLength = new boolean[columns.size()];

        int offset = 0;
        for (int i = 0; i < columns.size(); i++) {
            Column c = columns.get(i);
            columnNameToIndex.put(c.getName(), i);
            isVariableLength[i] = !c.getType().isFixedLength();

            if (c.getType().isFixedLength()) {
                c.setFixedOffset(offset);
                offset += c.getType().getSize();
            }
        }

        this.fixedPartLength = offset;
        this.primaryKeyIndex = columnNameToIndex.getOrDefault(primaryKeyColumnName, -1);
    }

    public int getColumnCount() {
        return columns.size();
    }

    public Column getColumn(int idx) {
        return columns.get(idx);
    }

    public int getColumnIndex(String name) {
        return columnNameToIndex.get(name);
    }

    public int getPrimaryKeyIndex() {
        return primaryKeyIndex;
    }

    public boolean isVariableLengthColumn(int idx) {
        return isVariableLength[idx];
    }

    public int getFixedPartLength() {
        return fixedPartLength;
    }

    public List<Column> getColumns() {
        return Collections.unmodifiableList(columns);
    }
}