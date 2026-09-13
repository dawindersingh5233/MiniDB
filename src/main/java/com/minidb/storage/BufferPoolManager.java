package com.minidb.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

record PageKey(int pageId, byte pageType) {
}

public class BufferPoolManager {
    private static BufferPoolManager instance;
    public static final int POOL_SIZE = 64;

    private Frame[] frames;
    private Map<PageKey, Integer> pageTable;
    private Deque<Integer> freeFrameList;
    private LRUReplacer replacer;
    private DiskManager disk;

    public BufferPoolManager(){
        this.frames = new Frame[POOL_SIZE];
        this.pageTable = new ConcurrentHashMap<>();
        this.freeFrameList = new ArrayDeque<>();
        this.replacer = new LRUReplacer();
        this.disk = DiskManager.getInstance();

        for(int i = 0; i < POOL_SIZE; i++){
            this.freeFrameList.offerLast(i);
        }
    }

    public Page fetchPage(int pageId, byte pageType){
        if(this.pageTable.containsKey(new PageKey(pageId, cacheType(pageType)))){
            int frameIndex = pageTable.get(new PageKey(pageId, cacheType(pageType)));
            Frame currFrame = this.frames[frameIndex];
            currFrame.incrementPinCount();

            //If the page is in eviction list it will remove it
            this.replacer.pin(frameIndex);

            return currFrame.getPage();
        }else{
            if(!freeFrameList.isEmpty()){
                try{
                    Page newPage = this.disk.readPage(pageId, pageType);

                    int frameIndex = this.freeFrameList.pollFirst();
                    frames[frameIndex] = new Frame(newPage, 1, false);

                    //If the page is in eviction list it will remove it
                    this.replacer.pin(frameIndex);

                    //Update the page table
                    this.pageTable.put(new PageKey(pageId, cacheType(pageType)), frameIndex);

                    return newPage;
                }catch(Exception e){
                    //TODO: check the right way to handle exceptions in java
                    System.out.println("Exception in BufferPoolManager.fetchPage(): "+ e.getMessage());
                }
            }else{
                try{
                    Page newPage = this.disk.readPage(pageId, pageType);

                    int frameIndex = this.replacer.evict();
                    if(frameIndex != -1){
                        //Flush the page to disk
                        if(this.frames[frameIndex].isDirty()){
                            Page evictedPage = this.frames[frameIndex].getPage();
                            int evictedPageId = evictedPage.getPageId();
                            disk.writePage(evictedPageId, evictedPage);
                        }

                        Page temp = this.frames[frameIndex].getPage();
                        this.pageTable.remove(new PageKey(temp.getPageId(), cacheType(temp.getPageType())));

                        frames[frameIndex] = new Frame(newPage, 1, false);

                        //Update the page table
                        this.pageTable.put(new PageKey(pageId, cacheType(pageType)), frameIndex);

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
        int pageId = this.disk.allocatePage(type);

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

    public void unpinPage(int pageId, byte type, boolean isDirty){
        if(this.pageTable.containsKey(new PageKey(pageId, cacheType(type)))){
            int frameIndex = this.pageTable.get(new PageKey(pageId, cacheType(type)));
            Frame frame = frames[frameIndex];
            frame.decrementPinCount();
            frame.setDirty(frame.isDirty() || isDirty);

            if(frame.getPinCount() == 0){
                this.replacer.unpin(frameIndex);
            }
        }
    }

    public void deallocatedPage(int pageId, byte type) {
        if(this.pageTable.containsKey(new PageKey(pageId, cacheType(type)))){
            int frameIndex = this.pageTable.get(new PageKey(pageId, cacheType(type)));
            Frame frame = frames[frameIndex];
            frame.decrementPinCount();

            if(frame.isDirty()){
                try{
                    this.disk.writePage(pageId, frame.getPage());
                }catch(Exception e){
                    e.printStackTrace();
                }
            }

            if(frame.getPinCount() == 0) {
                this.replacer.unpin(frameIndex);
            }

            this.disk.deallocatePage(pageId, type);
        }
    }

    public void flushAll(){
        for(int i = 0; i < POOL_SIZE; i++){
            Frame frame = this.frames[i];

            if(frame != null && frame.isDirty()){
                try{
                    Page page = frame.getPage();
                    System.out.println("Flusing PageId-"+page.getPageId());

                    this.disk.writePage(page.getPageId(), page);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    private byte cacheType(byte type){
        if(type == PageType.INDEX_INTERNAL || type == PageType.INDEX_LEAF){
            return PageType.INDEX_LEAF;
        }
        return type;
    }

    public static synchronized BufferPoolManager getInstance(){
        if(instance == null){
            instance = new BufferPoolManager();
        }

        return instance;
    }
}
