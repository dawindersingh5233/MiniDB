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
    private AtomicInteger counter;
    private Path path;

    public DiskManager(Path path){
        this.counter = new AtomicInteger(0);
        this.path = path;

        // Ensure parent directories exist
        File fileObj = this.path.toFile();
        if (fileObj.getParentFile() != null) {
            fileObj.getParentFile().mkdirs();
        }

        if(!Files.exists(this.path)){
            createMetaPage();
        }
    }

    public void createMetaPage() {
        Page page = new Page();
        page.setPageId(-1);
        page.setPageType(PageType.META);

        try {
            writePage(0, page);
        }catch(Exception e){
            System.out.println("Exception at DiskManager.createMetaPage(): "+e.getMessage());
        }
    }

    public SlottedPage readPage(int pageId) throws IOException {
        int pageSize = Page.PAGE_SIZE;
        long offset = pageId * pageSize;
        SlottedPage page = null;

        try(
                RandomAccessFile reader = new RandomAccessFile(this.path.toFile(), "r");
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

            page = new SlottedPage(buffer);
        }

        return page;
    }

    public void writePage(int pageId, SlottedPage page) throws IOException{
        int pageSize = Page.PAGE_SIZE;
        long offset = pageId * pageSize;

        try(
                RandomAccessFile writter = new RandomAccessFile(this.path.toFile(), "rw");
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

    public int allocatePage() {
        try {
            SlottedPage page0 = readPage(0);
            int firstFreePageId = page0.getPageId();

            if(firstFreePageId != -1){
                SlottedPage freePage = readPage(firstFreePageId);
                int nextFreePageId = freePage.getPageId();

                page0.setPageId(nextFreePageId);

                //Here we are putting pageId=0 because write method will
                //calculate the offset based on the id that we pass, and
                //since we want to store this page at index 0
                writePage(0, page0);

                return firstFreePageId;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return this.counter.incrementAndGet();
    }

    public void deallocatePage(int pageId){
        try{
            SlottedPage page0 = readPage(0);
            int firstFreePageId = page0.getPageId();

            SlottedPage currPage = readPage(pageId);
            currPage.setPageId(firstFreePageId);
            currPage.setPageType(PageType.INVALID);
            currPage.setFreeSpacePointer((short) 4096);

            page0.setPageId(pageId);

            //TODO: Here we are re-writing the entire page
            //TODO: update this method later to improve performance
            writePage(pageId, currPage);
            writePage(0, page0);
        }catch(Exception e){
            System.out.println("Exception at DiskManager.deallocatePage(): "+e.getLocalizedMessage());
        }
    }
}
