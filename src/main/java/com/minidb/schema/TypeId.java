package com.minidb.schema;

public enum TypeId {
    INTEGER(4, true),
    BOOLEAN(1, true),
    VARCHAR(-1, false);

    private final int size;
    private final boolean fixedLength;

    TypeId(int size, boolean fixedLength){
        this.size = size;
        this.fixedLength = fixedLength;
    }

    public int getSize(){
        return this.size;
    }

    public boolean isFixedLength(){
        return fixedLength;
    }
}
