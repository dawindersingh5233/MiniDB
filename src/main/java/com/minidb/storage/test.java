package com.minidb.storage;

public class test {
    public static void main(String args[]){
        String path = "/home/dawinder/Documents/Projects/MiniDB/src/main/java/com/minidb/storage/main.db";

        System.out.println("Program Started");

        DiskManager disk = new DiskManager(path);

        Page page1 = new Page();
        page1.setPageId(disk.allocatePage());
        page1.setPageType((byte)1);
        page1.setCheckSum(5233);
        page1.setSlotCount((short)10);
        page1.setFreeSpacePointer((short)4069);

        Page page2 = new Page();
        page2.setPageId(disk.allocatePage());
        page2.setPageType((byte)1);
        page2.setCheckSum(5233);
        page2.setSlotCount((short)10);
        page2.setFreeSpacePointer((short)4069);

        Page page3 = new Page();
        page3.setPageId(disk.allocatePage());
        page3.setPageType((byte)1);
        page3.setCheckSum(5233);
        page3.setSlotCount((short)10);
        page3.setFreeSpacePointer((short)4069);

        /*
        try{
            disk.writePage(1, page1);
            System.out.println("# Page1 successfully written to disk");

            disk.writePage(2, page2);
            System.out.println("# Page2 successfully written to disk");

            disk.writePage(3, page3);
            System.out.println("# Page3 successfully written to disk");
        }catch(Exception e){
            System.out.println("exception during writing page");
        }
        */

        try{
            System.out.println("Reading page: ");
            Page pg = disk.readPage(3);
            System.out.println(pg);
        }catch(Exception e){
            System.out.println("exception during reading page");
        }

        System.out.println("Done !");
    }
}
