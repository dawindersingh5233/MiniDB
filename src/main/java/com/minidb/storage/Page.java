package com.minidb.storage;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Page {
    public static final int PAGE_SIZE = 4096;

    //Page header fields
    private int pageId;
    private byte pageType;
    private int checkSum;
    private short slotCount;
    private short freeSpacePointer;

    public static final short PAGE_ID_OFFSET = 0;
    public static final short PAGE_TYPE_OFFSET = 4;
    public static final short CHECKSUM_OFFSET = 5;
    public static final short SLOT_COUNT_OFFSET = 9;
    public static final short FREE_SPACE_POINTER_OFFSET = 11;

    protected ByteBuffer buffer;
    private ReentrantReadWriteLock rwl;

    public Page(){
        this.buffer = ByteBuffer.allocate(PAGE_SIZE);
        this.rwl = new ReentrantReadWriteLock();

        setCheckSum(0);
        setSlotCount((short) 0);
        setFreeSpacePointer((short) 4096);

    }

    public Page(ByteBuffer buffer){
        this.rwl = new ReentrantReadWriteLock();
        this.setByteBuffer(buffer);
    }

    // read-write-locks
    public void rLock(){
        this.rwl.readLock().lock();
    }

    public void wLock(){
        this.rwl.writeLock().lock();
    }

    public void rUnlock(){
        this.rwl.readLock().unlock();
    }

    public void wUnlock(){
        this.rwl.writeLock().unlock();
    }

    // Page Header accessors
    public int getPageId() {
        return pageId;
    }

    public byte getPageType() {
        return pageType;
    }

    public int getCheckSum() {
        return checkSum;
    }

    public short getSlotCount() {
        return slotCount;
    }

    public short getFreeSpacePointer() {
        return freeSpacePointer;
    }

    public ByteBuffer getByteBuffer(){
        return this.buffer.duplicate();
    }

    // Page Header mutators
    public void setPageId(int pageId) {
        this.pageId = pageId;
        this.putInt(Page.PAGE_ID_OFFSET, pageId);
    }

    public void setPageType(byte pageType) {
        this.pageType = pageType;
        this.putByte(Page.PAGE_TYPE_OFFSET, pageType);
    }

    public void setCheckSum(int checkSum) {
        this.checkSum = checkSum;
        this.putInt(Page.CHECKSUM_OFFSET, checkSum);
    }

    public void setSlotCount(short slotCount) {
        this.slotCount = slotCount;
        this.putShort(Page.SLOT_COUNT_OFFSET, slotCount);
    }

    public void incrementSlotCount(){
        this.slotCount += 1;
        this.putShort(Page.SLOT_COUNT_OFFSET, this.slotCount);
    }

    public void decrementSlotCount(){
        this.slotCount -= 1;
        this.putShort(Page.SLOT_COUNT_OFFSET, this.slotCount);
    }

    public void setFreeSpacePointer(short freeSpacePointer) {
        this.freeSpacePointer = freeSpacePointer;
        this.putShort(Page.FREE_SPACE_POINTER_OFFSET, freeSpacePointer);
    }

    public void setByteBuffer(ByteBuffer buffer){
        this.buffer = buffer;

        int pageId = getInt(Page.PAGE_ID_OFFSET);
        byte pageType = getByte(Page.PAGE_TYPE_OFFSET);
        int checksum = getInt(Page.CHECKSUM_OFFSET);
        short slots = getShort(Page.SLOT_COUNT_OFFSET);
        short freeSpacePointers = getShort(Page.FREE_SPACE_POINTER_OFFSET);

        this.setPageId(pageId);
        this.setPageType(pageType);
        this.setCheckSum(checksum);
        this.setSlotCount(slots);
        this.setFreeSpacePointer(freeSpacePointers);
    }

    // Granular accessors and mutators
    public byte getByte(short offset){
        return this.buffer.get(offset);
    }

    public void putByte(short offset, byte value){
        this.buffer.put(offset, value);
    }

    public short getShort(short offset){
        return this.buffer.getShort(offset);
    }

    public void putShort(short offset, short value){
        this.buffer.putShort(offset, value);
    }

    public int getInt(short offset){
        return this.buffer.getInt(offset);
    }

    public void putInt(short offset, int value){
        this.buffer.putInt(offset, value);
    }

}

