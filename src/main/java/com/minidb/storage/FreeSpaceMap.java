package com.minidb.storage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//TODO: Update class to suppose extending map to a new Page once full
public class FreeSpaceMap extends Page{
    private short headerSize = 13;
    private Map<Integer, Byte> freeSpaceMap;
    private Map<Integer, Short> slotMap;

    public FreeSpaceMap(){
        setPageId(2);
        setPageType(PageType.META);
        setCheckSum(0);
        setSlotCount((short) 0);
        setFreeSpacePointer(headerSize);

        this.freeSpaceMap = new HashMap<>();
        this.slotMap = new HashMap<>();
    }

    public FreeSpaceMap(ByteBuffer buffer){
        super(buffer);

        this.freeSpaceMap = new HashMap<>();
        this.slotMap = new HashMap<>();

        fillFreeSpaceMap(buffer);
    }

    //Accessors
    public short getPageCount(){
        return getSlotCount();
    }

    //Mutators
    public void incrementPageCount(){
        incrementSlotCount();
    }

    public void decrementPageCount(){
        decrementSlotCount();
    }

    public void fillFreeSpaceMap(ByteBuffer buffer){
        int slotCount = getPageCount();
        short offset = headerSize;

        for(short i = 0; i < slotCount; i++){
            int pageId = buffer.getInt(offset);
            offset += 4;
            byte availableSpace = buffer.get(offset);
            offset += 1;

            this.slotMap.put(pageId, i);
            this.freeSpaceMap.put(pageId, availableSpace);
        }
    }

    public int findPageWithFreeSpace(int requiredBytes) {
        int pageId = -1;
        int safetyMargin = 50; //In bytes;

        for(Map.Entry<Integer, Byte> entry: this.freeSpaceMap.entrySet()){
            int availableSpace = (4096 / 100) * entry.getValue();

            if(requiredBytes + safetyMargin < availableSpace){
                pageId = entry.getKey();
                break;
            }
        }

        return pageId;
    }

    public void addNewPageEntry(int pageId, byte availableSpace){
        short pageCount = getPageCount();
        short offset = getFreeSpacePointer();

        putInt(offset, pageId);
        offset += 4;
        putByte(offset, availableSpace);

        setFreeSpacePointer(offset);

        this.slotMap.put(pageId, pageCount);
        incrementPageCount();

        this.freeSpaceMap.put(pageId, availableSpace);
    }

    public void updateFreeSpace(int pageId, byte freeSpace) {
        short offset = headerSize;
        short entrySize = 5;
        short slotNo = this.slotMap.get(pageId);

        offset = (short) (offset + (slotNo * entrySize)); //jump to slot offset
        offset += 4; //skip pageId

        putByte(offset, freeSpace);

        this.freeSpaceMap.put(pageId, freeSpace);
    }
}
