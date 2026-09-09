package com.minidb.storage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LeafNode extends Page{
    private int parentPageId;
    private int nextPageId;
    private int prevPageId;

    public static final short PARENT_PAGE_ID_OFFSET = 1;
    public static final short NEXT_PAGE_ID_OFFSET = 1;
    public static final short PREV_PAGE_ID_OFFSET = 1;
    public static final short NODE_ENTRY_OFFSET = 30;

    private List<Integer> keys;
    private List<RecordId> values;

    public LeafNode(){
        setPageType(PageType.INDEX_LEAF);
        setCheckSum(0);
        setSlotCount((short) 0);
        setFreeSpacePointer((short) 30);
        setParentPageId(0);
        setNextPageId(0);
        setPageId(0);

        this.keys = new ArrayList<>();
        this.values = new ArrayList<>();
    }

    public LeafNode(ByteBuffer buffer){
        super(buffer);
        int parentPageId = buffer.getInt(PARENT_PAGE_ID_OFFSET);
        int nextPageId = buffer.getInt(NEXT_PAGE_ID_OFFSET);
        int prevPageId = buffer.getInt(PREV_PAGE_ID_OFFSET);

        setParentPageId(parentPageId);
        setNextPageId(nextPageId);
        setPrevPageId(prevPageId);

        fillNodeEntries(buffer);
    }

    //Accessors
    public int getParentPageId() {
        return parentPageId;
    }

    public int getNextPageId() {
        return nextPageId;
    }

    public int getPrevPageId() {
        return prevPageId;
    }

    public short getKeyCount(){
        return getSlotCount();
    }

    public int getKeyAtPos(int pos){
        return this.keys.get(pos);
    }

    //Mutators
    public void incrementKeyCount(){
        incrementSlotCount();
    }

    public void decrementKeyCount(){
        decrementSlotCount();
    }

    public void setParentPageId(int parentPageId) {
        this.parentPageId = parentPageId;
        putInt(PARENT_PAGE_ID_OFFSET, parentPageId);
    }

    public void setNextPageId(int nextPageId) {
        this.nextPageId = nextPageId;
        putInt(NEXT_PAGE_ID_OFFSET, nextPageId);
    }

    public void setPrevPageId(int prevPageId) {
        this.prevPageId = prevPageId;
        putInt(PREV_PAGE_ID_OFFSET, prevPageId);
    }

    //Leaf Node specific methods
    public void fillNodeEntries(ByteBuffer buffer){
        int keyCount = getKeyCount();
        short offset = NODE_ENTRY_OFFSET;

        for(int i = 0; i < keyCount; i++){
            int key = getInt(offset);
            offset += 4;

            int pageId = getInt(offset);
            offset += 4;

            short slotNo = getShort(offset);
            offset += 2;

            this.keys.add(key);
            this.values.add(new RecordId(pageId, slotNo));
        }
    }

    public void addNewEntry(int key, RecordId recordId){
        if(containsKey(key)){
            return;
        }

        addNewEntry(key, recordId.getPageId(), recordId.getSlotNo());
    }

    public void addNewEntry(int key, int pageId, short slotNo){
        if(containsKey(key)){
            return;
        }

        int keyPosition = Collections.binarySearch(this.keys, key);
        int insertPosition = -(keyPosition + 1);

        this.keys.add(insertPosition, key);
        this.values.add(insertPosition, new RecordId(pageId, slotNo));

        short entrySize = 10;
        short offset = (short) (NODE_ENTRY_OFFSET + (entrySize * insertPosition));
        int keyCount = getKeyCount();

        //Copy Node entry at insert position in some temporary location
        int tempKey = getInt(offset);
        offset += 4;
        int tempPageId = getInt(offset);
        offset += 4;
        short tempSlotNo = getShort(offset);
        offset += 2;

        for(int i = insertPosition + 1; i < keyCount; i++){
            //Get the node entry at current position
            short tempOffset = offset;

            int currKey = getInt(tempOffset);
            tempOffset += 4;
            int currPageId = getInt(tempOffset);
            tempOffset += 4;
            short currSlotNo = getShort(tempOffset);

            //put previous node entry in current position
            putInt(offset, tempKey);
            offset += 4;
            putInt(offset, tempPageId);
            offset += 4;
            putShort(offset, tempSlotNo);
            offset += 2;

            //copy current node entry to to temporary location
            tempKey = currKey;
            tempPageId = currPageId;
            tempSlotNo = currSlotNo;
        }

        //Appending the remaining node entry in the page
        putInt(offset, tempKey);
        offset += 4;
        putInt(offset, tempPageId);
        offset += 4;
        putShort(offset, tempSlotNo);
        offset += 2;

        setFreeSpacePointer(offset);

        //Storing the give key-value at appropriate location
        offset = (short) (NODE_ENTRY_OFFSET + (entrySize * insertPosition));
        putInt(offset, key);
        offset += 4;
        putInt(offset, pageId);
        offset += 4;
        putShort(offset, slotNo);

        incrementKeyCount();
    }

    public boolean containsKey(int key){
        return Collections.binarySearch(this.keys, key) >= 0;
    }

    public RecordId getRecordId(int key){
        if(!containsKey(key)){
            return null;
        }

        int position = Collections.binarySearch(this.keys, key);
        return this.values.get(position);
    }

    public void deleteNodeEntry(int key){
        if(!containsKey(key)){
            return;
        }

        int keyPosition = Collections.binarySearch(this.keys, key);
        this.keys.remove(keyPosition);
        this.values.remove(keyPosition);

        short entrySize = 10;
        short offset = (short) (NODE_ENTRY_OFFSET + (entrySize * keyPosition));
        int keyCount = getKeyCount();

        for(int i = keyPosition + 1; i < keyCount; i++){
            short tempOffset = (short) (offset + entrySize);

            int currKey = getInt(offset);
            tempOffset += 4;
            int currPageId = getInt(tempOffset);
            tempOffset += 4;
            short currSlotNo = getShort(tempOffset);

            putInt(offset, currKey);
            offset += 4;
            putInt(offset, currPageId);
            offset += 4;
            putShort(offset, currSlotNo);
            offset += 2;
        }

        setFreeSpacePointer(offset);
        decrementKeyCount();
    }
}
