package com.minidb.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

public class DiskManager {
    private AtomicInteger dataCounter;
    private AtomicInteger indexCounter;

    public DiskManager(){
        this.dataCounter = new AtomicInteger(0);
        this.indexCounter = new AtomicInteger(0);

        //Ensures data file exists
        File dataFile = getPath(PageType.DATA).toFile();
        if (dataFile.getParentFile() != null) {
            dataFile.getParentFile().mkdirs();
        }

        //Ensure index file exists
        File indexFile = getPath(PageType.INDEX_LEAF).toFile();
        if (indexFile.getParentFile() != null) {
            indexFile.getParentFile().mkdirs();
        }

        //Ensure meta files exists
        File metaFile = getPath(PageType.META).toFile();
        if (metaFile.getParentFile() != null) {
            metaFile.getParentFile().mkdirs();
        }

        if(!Files.exists(getPath(PageType.META))){
            init();
        }
    }

    //Allocated Meta pages for re-allocation and FSM
    public void init(){
        //Helps to form reallocation for data pages
        Page page1 = new Page();
        page1.setPageId(-1);
        page1.setPageType(PageType.META);

        //Helps to form reallocation for data pages
        Page page2 = new Page();
        page2.setPageId(-1);
        page2.setPageType(PageType.META);

        //ensuring FSM page exists
        FreeSpaceMap fsm = new FreeSpaceMap();

        try {
            writePage(0, page1);
            writePage(1, page2);
            writePage(2, fsm);
        }catch(Exception e){
            System.out.println("Exception at DiskManager.createMetaPage(): "+e.getMessage());
        }
    }

    //Used to keep track of the deallocated pages
    //Forms a linked list of free pages
    public void createMetaPage() {
        //If meta page has pageId -1, means no dellocated pages available
    }

    public Page readPage(int pageId, byte type) throws IOException {
        Path path = getPath(type);
        Page page = null;

        int pageSize = Page.PAGE_SIZE;
        long offset = pageId * pageSize;

        try(
                RandomAccessFile reader = new RandomAccessFile(path.toFile(), "r");
                FileChannel channel = reader.getChannel();
        ){
            ByteBuffer buffer = ByteBuffer.allocate(pageSize);

            int bytesRead = 0;
            while(bytesRead < pageSize){
                int read = channel.read(buffer, offset + bytesRead);

                if (read == -1) {
                    throw new IOException("Unexpected EOF while reading Page ID: " + pageId);
                }

                bytesRead += read;
            }

            buffer.flip();

            page = new Page(buffer);
        }

        return page;
    }

    public void writePage(int pageId, Page page) throws IOException{
        Path path = getPath(page.getPageType());

        int pageSize = Page.PAGE_SIZE;
        long offset = pageId * pageSize;

        try(
                RandomAccessFile writter = new RandomAccessFile(path.toFile(), "rw");
                FileChannel channel = writter.getChannel();
        ){
            ByteBuffer buffer = page.getByteBuffer();
            buffer.rewind();

            long currOffset = offset;
            while(buffer.hasRemaining()) {
                int bytesWritten = channel.write(buffer, currOffset);
                currOffset += bytesWritten;
            }

            channel.force(false);
        }
    }

    public int allocatePage(byte type) {
        try {
            int id = (type == PageType.DATA) ? 0 : 1;

            Page page0 = readPage(id, PageType.META);
            int firstFreePageId = page0.getPageId();

            if(firstFreePageId != -1){
                Page freePage = readPage(firstFreePageId, type);
                int nextFreePageId = freePage.getPageId();

                page0.setPageId(nextFreePageId);

                //Here we are putting pageId=0 because write method will
                //calculate the offset based on the id that we pass, and
                //since we want to store this page at index 0
                writePage(id, page0);

                return firstFreePageId;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(type == PageType.DATA){
            return this.dataCounter.incrementAndGet();
        }

        return this.indexCounter.incrementAndGet();
    }

    public void deallocatePage(int pageId, byte type){
        try{
            int id = (type == PageType.DATA) ? 0 : 1;

            Page page0 = readPage(id, PageType.META);
            int firstFreePageId = page0.getPageId();

            Page currPage = readPage(pageId, type);
            currPage.setPageId(firstFreePageId);
            currPage.setFreeSpacePointer((short) 4096);

            page0.setPageId(pageId);

            //TODO: Here we are re-writing the entire page
            //TODO: update this method later to improve performance
            writePage(pageId, currPage);
            writePage(id, page0);
        }catch(Exception e){
            System.out.println("Exception at DiskManager.deallocatePage(): "+e.getLocalizedMessage());
        }
    }

    public Path getPath(byte type){
        String dest = "";

        switch(type){
            case PageType.META:
                dest = "/home/dawindersingh5233/Projects/MiniDB/database/meta/meta.db";
                return Path.of(dest);

            case PageType.INDEX_INTERNAL:
            case PageType.INDEX_LEAF:
                dest = "/home/dawindersingh5233/Projects/MiniDB/database/index/index.db";
                return Path.of(dest);

            case PageType.DATA:
                dest = "/home/dawindersingh5233/Projects/MiniDB/database/data/data.db";
                return Path.of(dest);

            default:
                return null;
        }
    }
}
