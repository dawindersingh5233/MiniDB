package com.minidb.storage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InternalNode extends Page{
    private int parentPageId;

    public static final short PARENT_PAGE_ID_OFFSET = 13;
    public static final short NODE_ENTRY_OFFSET = 30;

    private List<Integer> keys;
    private List<Integer> values;

    public InternalNode(){
        setPageType(PageType.INDEX_INTERNAL);
        setCheckSum(0);
        setSlotCount((short) 0);
        setFreeSpacePointer((short) 30);

        this.keys = new ArrayList<>();
        this.values = new ArrayList<>();
    }

    public InternalNode(ByteBuffer buffer){
        super(buffer);

        int parentPageId = buffer.getInt(PARENT_PAGE_ID_OFFSET);
        setParentPageId(parentPageId);

        this.keys = new ArrayList<>();
        this.values = new ArrayList<>();

        fillNodeEntries(buffer);
    }

    //Accessors
    public int getParentPageId(){
        return this.parentPageId;
    }

    public short getKeyCount(){
        return getSlotCount();
    }

    public List<Integer> getChildren(){
        return this.values;
    }

    public int getKeyAtPos(int pos){
        return this.keys.get(pos);
    }

    public int indexOf(int leafPageId){
        return this.values.indexOf(leafPageId);
    }

    public int getChildAtPos(int index){
        return this.values.get(index);
    }

    public int getChildCount(){
        return this.values.size();
    }

    //Mutators
    public void setKeyAtPos(int index, int key){
        this.keys.set(index, key);
    }

    public void incrementKeyCount(){
        incrementSlotCount();
    }

    public void decrementKeyCount(){
        decrementSlotCount();
    }

    public void setParentPageId(int pageId){
        this.parentPageId = pageId;
        putInt(PARENT_PAGE_ID_OFFSET, pageId);
    }

    //Internal Node related methods
    public void fillNodeEntries(ByteBuffer buffer){
        int keyCount = getKeyCount();
        short offset = NODE_ENTRY_OFFSET;

        int pageId = getInt(offset);
        offset += 4;
        this.values.add(pageId);

        for(int i = 0; i < keyCount; i++){
            int key = getInt(offset);
            offset += 4;

            pageId = getInt(offset);
            offset += 4;

            this.keys.add(key);
            this.values.add(pageId);
        }
    }

    public void addNewEntry(int key, int leftPageId, int rightPageId){
        if(containsKey(key)){
            return;
        }

        values.add(0, leftPageId);
        putInt(NODE_ENTRY_OFFSET, leftPageId);
        setFreeSpacePointer((short) (NODE_ENTRY_OFFSET + 4));

        addNewEntry(key, rightPageId);
    }

    public void addNewEntry(int key, int pageId){
        if(containsKey(key)){
            return;
        }

        int keyPosition = Collections.binarySearch(this.keys, key);
        int insertPosition = -(keyPosition + 1);

        this.keys.add(insertPosition, key);
        this.values.add(insertPosition + 1, pageId);

        short entrySize = 8;
        short offset = (short) (NODE_ENTRY_OFFSET + 4 + (entrySize * insertPosition));
        int keyCount = getKeyCount();

        //Copy Node entry at insert position in some temporary location
        int tempKey = getInt(offset);
        offset += 4;
        int tempValue = getInt(offset);
        offset += 4;

        for(int i = insertPosition + 1; i < keyCount; i++){
            //Get the node entry at current position
            int currKey = getInt(offset);
            int currValue = getInt((short) (offset + 4));

            //put previous node entry in current position
            putInt(offset, tempKey);
            offset += 4;
            putInt(offset, tempValue);
            offset += 4;

            //copy current node entry to to temporary location
            tempKey = currKey;
            tempValue = currValue;
        }

        //Appending the remaining node entry in the page
        putInt(offset, tempKey);
        offset += 4;
        putInt(offset, tempValue);

        offset += 4;
        setFreeSpacePointer(offset);

        //Storing the give key-value at appropriate location
        offset = (short) (NODE_ENTRY_OFFSET + 4 + (entrySize * insertPosition));
        putInt(offset, key);
        offset += 4;
        putInt(offset, pageId);

        incrementKeyCount();
    }

    //Add the entry at first position with left page id
    public void prependNewEntry(int pageId, int key){
        if(containsKey(key)){
            return;
        }

        this.keys.add(0, key);
        this.values.add(0, pageId);

        int keyCount = getKeyCount();
        short entrySize = 8;
        short offset = (short) (NODE_ENTRY_OFFSET + 4 + (entrySize * (keyCount + 1)));

        short fromOffset = (short) (offset - entrySize - 4);
        short toOffset = (short) (offset - 4);
        for(int i = 0; i < keyCount; i++){
            int currPageId = getInt(fromOffset);
            fromOffset -= 4;

            int currKey = getInt(fromOffset);
            fromOffset -= 4;

            putInt(toOffset, currPageId);
            toOffset -= 4;
            putInt(toOffset, currKey);
            toOffset -= 4;
        }

        int initPageId = getInt(fromOffset);
        putInt(toOffset, initPageId);

        toOffset -= 4;
        putInt(toOffset, key);
        toOffset -= 4;
        putInt(toOffset, pageId);

        setFreeSpacePointer(offset);

        incrementKeyCount();
    }

    public boolean containsKey(int key){
        return Collections.binarySearch(this.keys, key) >= 0;
    }

    public int getChildPageId(int key){
        int pos = Collections.binarySearch(this.keys, key);

        if(pos >= 0){
            return this.values.get(pos + 1);
        }

        int insertPos = -(pos + 1);
        return this.values.get(insertPos);
    }

    public void deleteNodeEntry(int key){
        if(!containsKey(key)){
            return;
        }

        int keyPosition = Collections.binarySearch(this.keys, key);
        this.keys.remove(keyPosition);
        this.values.remove(keyPosition + 1);

        short entrySize = 8;
        short offset = (short) (NODE_ENTRY_OFFSET + 4 + (entrySize * keyPosition));
        int keyCount = getKeyCount();

        for(int i = keyPosition + 1; i < keyCount; i++){
            short tempOffset = (short) (offset + 8);

            int currkey = getInt(tempOffset);
            tempOffset += 4;
            int currPageId = getInt(tempOffset);

            putInt(offset, currkey);
            offset += 4;
            putInt(offset, currPageId);
            offset += 4;
        }

        setFreeSpacePointer(offset);
        decrementKeyCount();
    }

    public void deleteNodeEntryLeft(int key){
        if(!containsKey(key)){
            return;
        }

        this.keys.remove(0);
        this.values.remove(0);

        int keyCount = getKeyCount();
        short entrySize = 8;
        short toOffset = (short) (NODE_ENTRY_OFFSET);
        short fromOffset = (short) (NODE_ENTRY_OFFSET + entrySize);

        for(int i = 1; i < keyCount; i++){
            int currPageId = getInt(fromOffset);
            fromOffset += 4;

            int currKey = getInt(fromOffset);
            fromOffset += 4;

            putInt(toOffset, currPageId);
            toOffset += 4;
            putInt(toOffset, currKey);
            toOffset += 4;
        }

        setFreeSpacePointer(toOffset);
        decrementKeyCount();
    }

    public void appendNewKey(int key){
        this.keys.add(key);

        short offset = getFreeSpacePointer();
        putInt(offset, key);
        offset += 4;

        setFreeSpacePointer(offset);
        incrementKeyCount();
    }

    public void appendChild(int pageId){
        this.values.add(pageId);

        short offset = getFreeSpacePointer();
        putInt(offset, pageId);
        offset += 4;

        setFreeSpacePointer(offset);
    }
}
