package com.minidb.storage;

import java.nio.file.Path;

public class test {
    public static void main(String args[]){
        String dest = "/home/dawinder/Documents/Projects/MiniDB/data/main.db";
        Path path = Path.of(dest);

        System.out.println("### Program Execution Started ###");
        System.out.println();
        DiskManager dm = new DiskManager(path);
        BufferPoolManager bm = new BufferPoolManager(path);

        testBufferPoolManager(bm);

        System.out.println("### Program Execution Completed ###");
    }

    public static void insertPageTest(DiskManager diskManager){
        System.out.println("### Testing insertPage() Start ###");
        Page page1 = new Page();
        page1.setPageId(5);
        page1.setPageType(PageType.INDEX_LEAF);
        page1.setCheckSum(9849);
        page1.setFreeSpacePointer((short) 4096);
        page1.setSlotCount((short) 0);

        try{
            System.out.println("# Writing Page - "+page1.getPageId());
            diskManager.writePage(page1.getPageId(), page1);

        }catch(Exception e){
            System.out.println("Exception in main(): "+ e.getMessage());
        }

        System.out.println("### Testing insertPage() End ###");
    }

    public static void readPageTest(DiskManager diskManager){
        try{
            System.out.println();
            System.out.println("### Testing readPage() Start ###");

            Page page1 = diskManager.readPage(1);
            System.out.println(page1);

            Page page2 = diskManager.readPage(2);
            System.out.println(page2);

            Page page3 = diskManager.readPage(3);
            System.out.println(page3);

            System.out.println("### Testing readPage() End ###");
        }catch(Exception e){
            System.out.println("Exception in main(): "+ e.getMessage());
        }
    }

    public static void reAllocatedTest(DiskManager diskManager){
        System.out.println("### Testing allocatePage() Start ###");
        diskManager.deallocatePage(2);

        Page newPage = new Page();
        newPage.setPageId(diskManager.allocatePage());
        newPage.setPageType(PageType.INDEX_LEAF);
        newPage.setCheckSum(9676);
        newPage.setSlotCount((short) 0);
        newPage.setFreeSpacePointer((short) 4096);

        try{
            diskManager.writePage(newPage.getPageId(), newPage);
        }catch(Exception e){
            System.out.println("Exception in main(): "+ e.getMessage());
        }
        System.out.println("### Testing allocatePage() End ###");
    }

    public static void testBufferPoolManager(BufferPoolManager bm){
        try {
            System.out.println("### Reading Page 1 ###");
            Page page1 = bm.fetchPage(1);
            System.out.println(page1);

            System.out.println("### Reading Page 2 ###");
            Page page2 = bm.fetchPage(2);
            System.out.println(page2);

            System.out.println("### Reading Page 3 ###");
            Page page3 = bm.fetchPage(3);
            System.out.println(page3);

            System.out.println("### Reading Page 1 ###");
            page1 = bm.fetchPage(1);
            System.out.println(page1);

            System.out.println("### Reading Page 3 ###");
            page3 = bm.fetchPage(3);
            System.out.println(page3);

            System.out.println("### Reading Page 4 ###");
            Page page4 = bm.fetchPage(4);
            if(page4 == null) {
                System.out.println(" !!! Buffer is full & Eviction Not Possible !!!");
            }else{
                System.out.println(page4);
            }

            System.out.println("### Page 2 Execution completed ###");
            bm.unpinPage(2, false);

            System.out.println("### Re-reading Page 2 ###");
            page2 = bm.fetchPage(2);
            System.out.println(page2);

            System.out.println("### Reading Page 4 ###");
            page4 = bm.fetchPage(4);
            if(page4 == null) {
                System.out.println(" !!! Buffer is full & Eviction Not Possible !!!");
            }else{
                System.out.println(page4);
            }

            //Page with this id is not present
            System.out.println("### Reading Page 10 ###");
            Page page10 = bm.fetchPage(10);
            System.out.println(page10);
        }catch(Exception e){
            System.out.println("Exception in main(): "+ e.getMessage());
        }
    }
}
