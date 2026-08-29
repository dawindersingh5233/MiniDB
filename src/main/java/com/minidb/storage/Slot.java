package com.minidb.storage;

public class Slot{
    private short offset;
    private short length;

    public Slot(short offset, short length){
        this.offset = offset;
        this.length = length;
    }

    public short getOffset(){
        return this.offset;
    }

    public short getLength(){
        return this.length;
    }

    public void setOffset(short offset){
        this.offset = offset;
    }

    public void setLength(short length){
        this.length = length;
    }

    public boolean isTombstone(){
        return this.length == -1;
    }

    public void setAsTombstone(){
        this.length = -1;
    }
}
