package com.minidb.storage;

import com.minidb.schema.Tuple;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public class TableHeap {
    private int fsmPageId = 1;
    private BufferPoolManager bufferPool;
    private FreeSpaceMap fsm;

    public TableHeap(){
        this.bufferPool = BufferPoolManager.getInstance();

        Page fsmPage = this.bufferPool.fetchPage(fsmPageId, PageType.META);
        this.fsm = new FreeSpaceMap(fsmPage.getByteBuffer());
    }

    public RecordId insert(ByteBuffer record){
        int pageId = this.fsm.findPageWithFreeSpace(record.capacity());

        if(pageId >= 0){
            Page page = this.bufferPool.fetchPage(pageId, PageType.DATA);
            SlottedPage slottedPage = new SlottedPage(page.getByteBuffer());

            //TODO: handle the case where page is null
            if(page != null){
               short slotNo = slottedPage.insertRecord(record);

               if(slotNo >= 0){
                    this.bufferPool.unpinPage(pageId, true);

                    int pageSize = slottedPage.getFreeSpacePointer();
                    int headerSize = SlottedPage.pageHeaderSize + (slottedPage.getSlotCount() * 4);
                    int availableSpace = pageSize - headerSize;

                    this.fsm.updateFreeSpace(pageId, toPercentage(availableSpace));

                    return new RecordId(pageId, slotNo);
               }
            }
        }else{
            int newPageId = this.bufferPool.allocateNewPage(PageType.DATA);
            Page page = this.bufferPool.fetchPage(newPageId, PageType.DATA);
            SlottedPage slottedPage = new SlottedPage(page.getByteBuffer());

            //TODO: handle the case where page is null
            if(page != null){
                short slotNo = slottedPage.insertRecord(record);

                if(slotNo >= 0){
                    this.bufferPool.unpinPage(pageId, true);

                    int pageSize = slottedPage.getFreeSpacePointer();
                    int headerSize = SlottedPage.pageHeaderSize + (slottedPage.getSlotCount() * 4);
                    int availableSpace = pageSize - headerSize;

                    this.fsm.updateFreeSpace(pageId, toPercentage(availableSpace));

                    return new RecordId(pageId, slotNo);
                }
            }
        }

        return null;
    }

    public void update(RecordId recordId, ByteBuffer record){
        Page page = this.bufferPool.fetchPage(recordId.getPageId(), PageType.DATA);
        SlottedPage slottedPage = new SlottedPage(page.getByteBuffer());

        slottedPage.updateRecord(recordId.getSlotNo(), record);

        this.bufferPool.unpinPage(recordId.getPageId(), true);
    }

    public void delete(RecordId recordId){
        int pageId = recordId.getPageId();
        short slotNo = recordId.getSlotNo();

        Page page = this.bufferPool.fetchPage(pageId, PageType.DATA);
        SlottedPage slottedPage = new SlottedPage(page.getByteBuffer());
        slottedPage.deleteRecord(slotNo);

        this.bufferPool.unpinPage(pageId, true);
    }

    public byte toPercentage(int availableSpace){
        byte perc = (byte) (availableSpace / (4096 / 100));
        return perc;
    }
}
