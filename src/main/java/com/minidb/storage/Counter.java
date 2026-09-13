package com.minidb.storage;

import java.nio.ByteBuffer;

public class Counter extends Page{
    private int dataCounter;
    private int indexCounter;

    private short DATA_COUNTER_OFFSET = 13;
    private short INDEX_COUNTER_OFFSET = 17;

    public Counter(){
        setPageId(3);
        setPageType(PageType.META);
        setCheckSum(0);
        setSlotCount((short) 0);
        setFreeSpacePointer((short) 21);

        putInt(DATA_COUNTER_OFFSET, 1);
        putInt(INDEX_COUNTER_OFFSET, 2);
    }

    public Counter(ByteBuffer buffer){
        super(buffer);

        this.dataCounter = getInt(DATA_COUNTER_OFFSET);
        this.indexCounter = getInt(INDEX_COUNTER_OFFSET);
    }

    public int getDataCounter() {
        return dataCounter;
    }

    public int getIndexCounter() {
        return indexCounter;
    }

    public void setDataCounter(int dataCounter) {
        this.dataCounter = dataCounter;
        putInt(DATA_COUNTER_OFFSET, dataCounter);
    }

    public void setIndexCounter(int indexCounter) {
        this.indexCounter = indexCounter;
        putInt(INDEX_COUNTER_OFFSET, indexCounter);
    }

    public int getAndIncrementData(){
        setDataCounter(getDataCounter() + 1);
        return this.dataCounter - 1;
    }

    public int getAndIncrementIndex(){
        setIndexCounter(getIndexCounter() + 1);
        return this.indexCounter - 1;
    }
}
