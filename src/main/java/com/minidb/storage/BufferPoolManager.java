package com.minidb.storage;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BufferPoolManager {
    private static BufferPoolManager instance;
    public static final int POOL_SIZE = 64;

    private Frame[] frames;
    private Map<Integer, Integer> pageTable;
    private Deque<Integer> freeFrameList;
    private LRUReplacer replacer;

    public BufferPoolManager(){
        this.frames = new Frame[POOL_SIZE];
        this.pageTable = new ConcurrentHashMap<>();
        this.freeFrameList = new ArrayDeque<>();
        this.replacer = new LRUReplacer();

        for(int i = 0; i < POOL_SIZE; i++){
            this.freeFrameList.offerLast(i);
        }
    }

    public Page fetchPage(int pageId, byte pageType){
        if(this.pageTable.containsKey(pageId)){
            int frameIndex = pageTable.get(pageId);
            Frame currFrame = this.frames[frameIndex];
            currFrame.incrementPinCount();

            //If the page is in eviction list it will remove it
            this.replacer.pin(frameIndex);

            return currFrame.getPage();
        }else{
            if(!freeFrameList.isEmpty()){
                try{
                    DiskManager disk = new DiskManager();
                    Page newPage = disk.readPage(pageId, pageType);

                    int frameIndex = this.freeFrameList.pollFirst();
                    frames[frameIndex] = new Frame(newPage, 1, false);

                    //If the page is in eviction list it will remove it
                    this.replacer.pin(frameIndex);

                    //Update the page table
                    this.pageTable.put(pageId, frameIndex);

                    return newPage;
                }catch(Exception e){
                    //TODO: check the right way to handle exceptions in java
                    System.out.println("Exception in BufferPoolManager.fetchPage(): "+ e.getMessage());
                }
            }else{
                try{
                    DiskManager disk = new DiskManager();
                    Page newPage = disk.readPage(pageId, pageType);

                    int frameIndex = this.replacer.evict();
                    if(frameIndex != -1){
                        //Flush the page to disk
                        if(this.frames[frameIndex].isDirty()){
                            Page evictedPage = this.frames[frameIndex].getPage();
                            int evictedPageId = evictedPage.getPageId();
                            disk.writePage(evictedPageId, evictedPage);
                        }

                        frames[frameIndex] = new Frame(newPage, 1, false);

                        //Update the page table
                        this.pageTable.put(pageId, frameIndex);

                        return newPage;
                    }
                }catch(Exception e){
                    //TODO: check the right way to handle exceptions in java
                    System.out.println("Exception in BufferPoolManager.fetchPage(): "+ e.getMessage());
                }
            }
        }

        //Where user calls this method and we return null this means:
        //All frames are taken by some active transaction, therefore
        //caller must throw an exception
        return null;
    }

    public int allocateNewPage(byte type){
        DiskManager disk = new DiskManager();
        int pageId = disk.allocatePage(type);

        try{
            switch(type){
                case PageType.DATA:
                    SlottedPage page = new SlottedPage();
                    page.setPageId(pageId);
                    disk.writePage(pageId, page);
                    break;

                case PageType.INDEX_INTERNAL:
                    InternalNode internalNode = new InternalNode();
                    internalNode.setPageId(pageId);
                    disk.writePage(pageId, internalNode);
                    break;

                case PageType.INDEX_LEAF:
                    LeafNode leaf = new LeafNode();
                    leaf.setPageId(pageId);
                    disk.writePage(pageId, leaf);
                    break;
            }
        }catch(Exception e){
            System.out.println("Exception in BufferPoolManager.allocateNewPage(): "+ e.getMessage());
        }

        return pageId;
    }

    public void unpinPage(int pageId, boolean isDirty){
        if(this.pageTable.containsKey(pageId)){
            int frameIndex = this.pageTable.get(pageId);
            Frame frame = frames[frameIndex];
            frame.decrementPinCount();
            frame.setDirty(isDirty);

            if(frame.getPinCount() == 0){
                this.replacer.unpin(frameIndex);
            }
        }
    }

    public static synchronized BufferPoolManager getInstance(){
        if(instance == null){
            instance = new BufferPoolManager();
        }

        return instance;
    }
}
