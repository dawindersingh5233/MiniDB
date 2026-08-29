package com.minidb.storage;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Frame {
    private Page page;
    private int pinCount;
    private boolean isDirty;

    public Frame(Page page, int pinCount, boolean isDirty){
        this.page = page;
        this.pinCount = pinCount;
        this.isDirty = isDirty;
    }

    // Accessors and mutators
    public Page getPage() {
        return page;
    }

    public int getPinCount() {
        return pinCount;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setPage(Page page) {
        this.page = page;
    }

    public void setPinCount(int pinCount) {
        this.pinCount = pinCount;
    }

    public void incrementPinCount(){
        this.pinCount += 1;
    }

    public void decrementPinCount(){
        if(this.pinCount >= 1){
            this.pinCount -= 1;
        }
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }
}
