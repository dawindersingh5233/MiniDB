package com.minidb.storage;

import java.nio.ByteBuffer;
import java.util.List;

public class SlottedPage extends Page{
    private SlotDirectory slotDir;

    public SlottedPage(){
        this.slotDir = new SlotDirectory();
    }

    //Add the given record/row in byte buffer and return its slot id
    public short insertRecord(ByteBuffer data){
        int headerSize = 14;
        int slotDirSize = 4 * getSlotCount();
        int totalFreeSpace = getFreeSpacePointer() - (headerSize + slotDirSize);
        int requiredSpace = 4 + data.capacity();

        if(requiredSpace <= totalFreeSpace){
            short dataLength = (short) data.capacity();
            short offset = (short) (getFreeSpacePointer() - dataLength);

            buffer.position(offset);
            buffer.put(data);

            //Here we are assuming that first field is key and is of type int
            int key = data.getInt(0);
            short slotId = 0;

            if(getSlotCount() == 0){
                this.slotDir.addSlot(offset, dataLength);
                updateSlotDirectory(offset, dataLength, -1);
            }else{
                int pos = findSlotPosition(key);

                if(pos != -1){
                    this.slotDir.addSlotAtPos(pos, offset, dataLength);
                    slotId = (short) pos;
                }else{
                    this.slotDir.addSlot(offset, dataLength);
                }

                updateSlotDirectory(offset, dataLength, pos);
            }

            //update the freeSpacePointer
            setFreeSpacePointer(offset);

            return slotId;
        }

        // We return -1 when we don't have enought space in the page
        // Caller must handle this case appropriately
        return -1;
    }

    // Put the slot(offset, length) at the right position in Byte buffer
    public void updateSlotDirectory(short dataOffset, short dataLength, int pos){
        short offset = 14;

        if(getSlotCount() == 0){
            putShort(offset, dataOffset);
            offset += 2;
            putShort(offset, dataLength);

            incrementSlotCount();
        }else{
            if(pos == -1){
                int slotDirSize = getSlotCount() * 4;
                offset = (short) (offset + slotDirSize);

                putShort(offset, dataOffset);
                offset += 2;
                putShort(offset, dataLength);

                incrementSlotCount();
            }else{
                short headerSize = 14;
                short slotSize = 4;
                offset = (short) (headerSize + (slotSize * pos));

                //Store the Slot at pos in temp location
                short tempOffset = getShort(offset);
                offset = (short) (offset + 2);

                short tempLength = getShort(offset);
                offset = (short) (offset + 2);

                //Shift slots by one pos to the right
                for(int i = pos + 1; i < getSlotCount(); i++) {
                    short currOffset = getShort(offset);
                    short currLength = getShort((short) (offset + 2));

                    putShort(offset, tempOffset);
                    offset = (short) (offset + 2);
                    putShort(offset, tempLength);
                    offset = (short) (offset + 2);

                    tempOffset = currOffset;
                    tempLength = currLength;
                }

                //Add the new slot at the right position
                offset = (short) (headerSize + (slotSize * pos));
                putShort(offset, dataOffset);
                offset = (short) (offset + 2);
                putShort(offset, dataLength);

                incrementSlotCount();
            }
        }
    }

    // Find the index in the slot directory where the given key belongs
    public int findSlotPosition(int key){
        List<Slot> slots = this.slotDir.getSlots();
        int low = 0;
        int high = slots.size() - 1;
        int result = -1;

        while(low <= high){
            int mid = low + (high - low) / 2;
            Slot currSlot = slots.get(mid);

            //Here were are assuming that first field of record is key of type int
            int currKey = getInt(currSlot.getOffset());

            if(currKey >= key){
                result = mid;
                high = mid - 1;
            }else{
                low = mid + 1;
            }
        }

        return result;
    }

    //Get data record linked with specific slot
    public ByteBuffer getRecord(short slotId){
        Slot slot = this.slotDir.get(slotId);

        ByteBuffer view = getByteBuffer();
        view.position(slot.getOffset());
        view.limit(slot.getOffset() + slot.getLength());

        ByteBuffer record = view.slice();

        return record;
    }

    // sets the given slotId length to -1
    // Further steps are handled during compact()
    public void deleteRecord(short slotId){
        this.slotDir.setAsTombstone(slotId);
    }

    public boolean updateRecord(short slotId, ByteBuffer newData){
        Slot slot = this.slotDir.get(slotId);
        short oldRecordLength = slot.getLength();
        short newRecordLength = (short) newData.capacity();

        //update in place
        if(newRecordLength <= oldRecordLength){
            //reset prev data
            resetRecordData(slot.getOffset(), slot.getLength());

            //add new record data
            buffer.position(slot.getOffset());
            buffer.put(newData);

            //update slot length in slot directory
            updateSlotLength(slotId, newRecordLength);
        }else{
            //TODO: here we have to handle the case where new record might not fit in available space
            //Since new record size is larger than prev
            //we can delete prev and insert new record
            deleteRecord(slotId);
            insertRecord(newData);
        }

        return true;
    }

    public void resetRecordData(short offset, short length){
        ByteBuffer data = ByteBuffer.allocate(length);

        buffer.position(offset);
        buffer.put(data);
    }

    //Update the slot offset in buffer
    public void updateSlotOffset(short slotId, short newOffset){
        Slot slot = this.slotDir.get(slotId);
        slot.setOffset(newOffset);

        short slotOffset = (short) (14 + (slotId * 4));
        putShort(slotOffset, newOffset);
    }

    //Update the slot length in buffer
    public void updateSlotLength(short slotId, short newLength){
        Slot slot = this.slotDir.get(slotId);
        slot.setLength(newLength);

        short slotOffset = (short) (14 + (slotId * 4) + 2);
        putShort(slotOffset, newLength);
    }

    public void compact(){
        SlottedPage newPage = new SlottedPage();

        //Fill page header data
        newPage.setPageId(getPageId());
        newPage.setPageType(getPageType());
        newPage.setCheckSum(getCheckSum());
        newPage.setSlotCount((short) 0);
        //TODO: will this be 4096 or 4095 because index starts at 0
        newPage.setFreeSpacePointer((short) 4096);

        // Fill slot directory and add valid records
        List<Slot> slots = this.slotDir.getSlots();
        for(int i = 0; i < slots.size(); i++){
            Slot slot = slots.get(i);

            //Using insertRecord() will also update the header values and slot dir
            if(!slot.isTombstone()){
               ByteBuffer record = getRecord((short) i) ;
               newPage.insertRecord(record);
            }
        }
    }
}

