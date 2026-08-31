package com.minidb.Schema;

public class Column {
    private final String name;
    private final TypeId type;
    private final int length;
    private int offset;

    public Column(String name, TypeId type){
        this(name, type, type.isFixedLength() ? type.getSize(): -1);
    }

    public Column(String name, TypeId type, int length){
        this.name = name;
        this.type = type;
        this.length = length;
    }

    public String getName() {
        return name;
    }

    public TypeId getType() {
        return type;
    }

    public int getLength() {
        return length;
    }

    public int getFixedOffset() {
        return offset;
    }

    void setFixedOffset(int offset) {
        this.offset = offset;
    }
}
