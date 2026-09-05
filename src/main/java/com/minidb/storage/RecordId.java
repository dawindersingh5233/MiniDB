package com.minidb.storage;

public class RecordId {
    private int pageId;
    private short slotNo;

    public RecordId(int pageId, short slotNo){
        this.pageId = pageId;
        this.slotNo = slotNo;
    }

    public int getPageId() {
        return pageId;
    }

    public short getSlotNo() {
        return slotNo;
    }

    public void setPageId(int pageId) {
        this.pageId = pageId;
    }

    public void setSlotNo(short slotNo) {
        this.slotNo = slotNo;
    }
}
